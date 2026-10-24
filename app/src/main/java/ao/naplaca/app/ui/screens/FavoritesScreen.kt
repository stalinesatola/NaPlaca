package ao.naplaca.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.domain.Source
import ao.naplaca.app.ui.Format
import ao.naplaca.app.ui.appContainer
import ao.naplaca.app.ui.components.MessageBox
import ao.naplaca.app.ui.components.SectionHeader
import ao.naplaca.app.ui.components.TeamLogo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenTeam: (Source, String, String) -> Unit,
    onOpenMatch: (String, String) -> Unit,
) {
    val c = appContainer()
    val teams by c.favorites.teams.collectAsStateWithLifecycle(emptyList())
    val followed by c.favorites.followed.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionHeader("Equipas") }
            if (teams.isEmpty()) {
                item { MessageBox("Ainda sem equipas favoritas", message = "Abra uma equipa (a partir de um jogo ou da tabela) e toque no coração.") }
            }
            items(teams, key = { it.key }) { t ->
                ListItem(
                    modifier = Modifier.clickable { onOpenTeam(Source.valueOf(t.source), t.leagueSlug, t.teamId) },
                    leadingContent = { TeamLogo(t.logo) },
                    headlineContent = { Text(t.name) },
                    supportingContent = { Text(Leagues.name(t.leagueSlug) + if (t.alertsEnabled) " · alertas ligados" else " · sem alertas") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = t.alertsEnabled, onCheckedChange = { on -> scope.launch { c.favorites.setTeamAlerts(t, on) } })
                            IconButton(onClick = { scope.launch { c.database.favorites().deleteTeam(t.key) } }) {
                                Icon(Icons.Outlined.Delete, "Remover")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
            item { SectionHeader("Jogos seguidos") }
            if (followed.isEmpty()) {
                item { MessageBox("Nenhum jogo seguido", message = "Toque no sino de um jogo para receber alertas.") }
            }
            items(followed, key = { it.matchId }) { m ->
                ListItem(
                    modifier = Modifier.clickable { onOpenMatch(m.matchId, m.leagueSlug) },
                    headlineContent = { Text(m.title) },
                    supportingContent = {
                        Text("${Leagues.name(m.leagueSlug)} · ${Format.dateTime(m.startEpochMillis)}" + if (m.auto) " · automático" else "")
                    },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { c.favorites.unfollow(m.matchId) } }) { Icon(Icons.Outlined.Delete, "Deixar de seguir") }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
