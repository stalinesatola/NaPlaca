package ao.naplaca.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.naplaca.app.BuildConfig
import ao.naplaca.app.data.local.Profile
import ao.naplaca.app.data.local.ThemeMode
import ao.naplaca.app.ui.appContainer
import ao.naplaca.app.ui.components.SectionHeader
import ao.naplaca.app.ui.components.friendlyError
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onOpenFavorites: () -> Unit, onOpenAlerts: () -> Unit) {
    val c = appContainer()
    val profile by c.profileStore.profile.collectAsStateWithLifecycle(Profile())
    val teams by c.favorites.teams.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    LaunchedEffect(profile.name) { name = profile.name }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.backup.exportTo(uri) }
                .onSuccess { Toast.makeText(context, "Cópia de segurança guardada", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, friendlyError(it), Toast.LENGTH_LONG).show() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { c.backup.importFrom(uri) }
                .onSuccess { b -> Toast.makeText(context, "Restaurado: ${b.favoriteTeams.size} equipas favoritas", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Ficheiro inválido: ${friendlyError(it)}", Toast.LENGTH_LONG).show() }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(if (profile.name.isBlank()) "Perfil" else "Olá, ${profile.name}") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("O seu perfil")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LaunchedEffect(name) {
                if (name != profile.name) {
                    kotlinx.coroutines.delay(600)
                    c.profileStore.update { it.copy(name = name.trim()) }
                }
            }
            Text(
                "O perfil fica só neste telemóvel. Use a cópia de segurança para o levar para outro aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            SectionHeader("Tema")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = profile.theme == mode,
                        onClick = { scope.launch { c.profileStore.update { it.copy(theme = mode) } } },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Sistema"
                                    ThemeMode.LIGHT -> "Claro"
                                    ThemeMode.DARK -> "Escuro"
                                },
                            )
                        },
                    )
                }
            }

            SectionHeader("Acompanhar")
            NavItem(Icons.Outlined.FavoriteBorder, "Favoritos", "${teams.size} equipas · jogos seguidos", onOpenFavorites)
            NavItem(Icons.Outlined.NotificationsActive, "Alertas", "Golos, cartões, início e fim, lembretes", onOpenAlerts)

            SectionHeader("Cópia de segurança")
            NavItem(Icons.Outlined.Upload, "Exportar", "Guardar perfil, favoritos e alertas num ficheiro") {
                exportLauncher.launch("naplaca-${LocalDate.now()}.json")
            }
            NavItem(Icons.Outlined.Download, "Importar", "Restaurar a partir de um ficheiro") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }

            SectionHeader("Sobre")
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Info, null) },
                headlineContent = { Text("NaPlaca ${BuildConfig.VERSION_NAME}") },
                supportingContent = {
                    Text(
                        "Dados: ESPN (API pública), TheSportsDB e centro de estatísticas Bantubet. " +
                            "O NaPlaca não é afiliado a estas entidades. Código aberto no GitHub.",
                    )
                },
            )
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
    )
    HorizontalDivider()
}
