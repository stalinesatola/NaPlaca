package ao.naplaca.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.ui.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(requestNotifications: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var leagues by remember { mutableStateOf(Leagues.defaultSelection) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Bem-vindo ao NaPlaca", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "Resultados ao vivo, tabelas, marcadores e alertas de golos — do Girabola à Champions. Grátis e sem conta.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(name, { name = it }, label = { Text("Como se chama? (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Que ligas quer acompanhar?", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Leagues.all.forEach { l ->
                    val sel = l.slug in leagues
                    FilterChip(selected = sel, onClick = { leagues = if (sel) leagues - l.slug else leagues + l.slug }, label = { Text(l.name) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    requestNotifications()
                    scope.launch {
                        c.profileStore.update { it.copy(name = name.trim(), leagues = leagues.ifEmpty { Leagues.defaultSelection }, onboarded = true) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Começar") }
        }
    }
}
