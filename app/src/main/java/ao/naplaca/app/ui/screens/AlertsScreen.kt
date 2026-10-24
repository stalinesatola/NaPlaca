package ao.naplaca.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.naplaca.app.alerts.Alert
import ao.naplaca.app.alerts.AlertScheduler
import ao.naplaca.app.alerts.AlertType
import ao.naplaca.app.data.local.AlertPrefs
import ao.naplaca.app.data.local.Profile
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.ui.Format
import ao.naplaca.app.ui.appContainer
import ao.naplaca.app.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(onBack: () -> Unit, requestNotifications: () -> Unit) {
    val c = appContainer()
    val profile by c.profileStore.profile.collectAsStateWithLifecycle(Profile())
    val log by c.database.alertLog().observe().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = profile.alerts
    fun set(transform: (AlertPrefs) -> AlertPrefs) = scope.launch { c.profileStore.update { it.copy(alerts = transform(it.alerts)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (!c.notifier.canNotify()) {
                item {
                    ListItem(
                        headlineContent = { Text("Notificações desligadas") },
                        supportingContent = { Text("Permita notificações para receber golos e resultados.") },
                        trailingContent = { FilledTonalButton(onClick = requestNotifications) { Text("Permitir") } },
                    )
                }
            }
            item { SectionHeader("Avisar-me de") }
            item { Toggle("Golos", prefs.goals) { v -> set { it.copy(goals = v) } } }
            item { Toggle("Cartões vermelhos", prefs.redCards) { v -> set { it.copy(redCards = v) } } }
            item { Toggle("Início do jogo", prefs.kickoff) { v -> set { it.copy(kickoff = v) } } }
            item { Toggle("Intervalo", prefs.halftime) { v -> set { it.copy(halftime = v) } } }
            item { Toggle("Resultado final", prefs.fullTime) { v -> set { it.copy(fullTime = v) } } }
            item { Toggle("Lembrete antes do jogo", prefs.reminder) { v -> set { it.copy(reminder = v) } } }
            if (prefs.reminder) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { m ->
                            FilterChip(selected = prefs.reminderMinutes == m, onClick = { set { it.copy(reminderMinutes = m) } }, label = { Text("$m min antes") })
                        }
                    }
                }
            }
            item { SectionHeader("Não incomodar") }
            item { Toggle("Silenciar à noite (${prefs.quietStart}h–${prefs.quietEnd}h)", prefs.quietHours) { v -> set { it.copy(quietHours = v) } } }
            if (prefs.quietHours) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(22 to 7, 23 to 7, 0 to 8).forEach { (s, e) ->
                            FilterChip(
                                selected = prefs.quietStart == s && prefs.quietEnd == e,
                                onClick = { set { it.copy(quietStart = s, quietEnd = e) } },
                                label = { Text("${s}h–${e}h") },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Os alertas funcionam sem servidor: o telemóvel verifica os jogos seguidos (a cada minuto durante o jogo). " +
                        "Se o Android limitar a app em segundo plano, desative a otimização de bateria para o NaPlaca.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        requestNotifications()
                        c.notifier.show(Alert(AlertType.GOAL, "test", "⚽ GOLO! (teste)", "Petro de Luanda 1 - 0 1.º de Agosto · 23'"), Leagues.GIRABOLA)
                    }) { Text("Testar notificação") }
                    OutlinedButton(onClick = { AlertScheduler.kick(context) }) { Text("Verificar agora") }
                }
            }
            item {
                SectionHeader("Histórico") {
                    if (log.isNotEmpty()) TextButton(onClick = { scope.launch { c.database.alertLog().clear() } }) { Text("Limpar") }
                }
            }
            if (log.isEmpty()) {
                item {
                    Text("Ainda sem alertas.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(log, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(e.title) },
                    supportingContent = { Text(e.body) },
                    trailingContent = { Text(Format.ago(e.createdAt), style = MaterialTheme.typography.labelSmall) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
