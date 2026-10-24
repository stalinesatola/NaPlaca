package ao.naplaca.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.naplaca.app.data.local.Profile
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.ui.appContainer
import ao.naplaca.app.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaguesScreen(onOpenLeague: (String) -> Unit) {
    val c = appContainer()
    val profile by c.profileStore.profile.collectAsStateWithLifecycle(Profile())
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Ligas") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "Toque na estrela para mostrar a liga no separador Jogos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Leagues.all.groupBy { it.country }.forEach { (country, leagues) ->
                item(key = "c-$country") { SectionHeader(country) }
                items(leagues, key = { it.slug }) { league ->
                    val selected = league.slug in profile.leagues
                    ListItem(
                        modifier = Modifier.clickable { onOpenLeague(league.slug) },
                        leadingContent = { Icon(Icons.Outlined.EmojiEvents, null) },
                        headlineContent = { Text(league.name) },
                        supportingContent = { Text(if (league.source.name == "ESPN") "Ao vivo · tabela · marcadores" else "Resultados · tabela") },
                        trailingContent = {
                            IconButton(onClick = {
                                scope.launch {
                                    c.profileStore.update { p ->
                                        p.copy(leagues = if (selected) p.leagues - league.slug else p.leagues + league.slug)
                                    }
                                }
                            }) {
                                Icon(
                                    if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    if (selected) "Remover dos jogos" else "Mostrar nos jogos",
                                    tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

