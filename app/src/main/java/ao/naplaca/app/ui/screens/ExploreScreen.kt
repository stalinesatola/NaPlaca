package ao.naplaca.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ao.naplaca.app.domain.Leader
import ao.naplaca.app.domain.LeaderBoard
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.domain.Source
import ao.naplaca.app.ui.Format
import ao.naplaca.app.ui.appViewModel
import ao.naplaca.app.ui.components.SectionHeader
import ao.naplaca.app.ui.theme.LiveRed
import ao.naplaca.app.ui.theme.Yellow

enum class ExploreMetric(val label: String) { GOALS("Golos"), ASSISTS("Assistências"), GOALS_PER_GAME("Golos/jogo") }

/** Explorador de estatísticas: rankings de jogadores por liga com barras de "calor". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen() {
    val leagues = Leagues.all.filter { it.source == Source.ESPN && it.hasLeaders }
    var slug by rememberSaveable { mutableStateOf("eng.1") }
    var metric by rememberSaveable { mutableStateOf(ExploreMetric.GOALS) }

    Scaffold(topBar = { TopAppBar(title = { Text("Explorar estatísticas") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(leagues, key = { it.slug }) { l ->
                    FilterChip(selected = l.slug == slug, onClick = { slug = l.slug }, label = { Text(l.name) })
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExploreMetric.entries) { m ->
                    FilterChip(selected = m == metric, onClick = { metric = m }, label = { Text(m.label) })
                }
            }
            val vm = appViewModel(key = "explore-$slug") { LeagueViewModel(it, slug) }
            val block by vm.leaders.collectAsStateWithLifecycle()
            BlockContent(block, empty = "Sem estatísticas para esta liga") { boards ->
                val ranked = rank(boards, metric)
                val max = ranked.maxOfOrNull { it.value } ?: 1.0
                LazyColumn(Modifier.fillMaxSize()) {
                    item { SectionHeader("${metric.label} · ${Leagues.name(slug)}") }
                    items(ranked, key = { it.playerId }) { l -> LeaderLine(l, max, Format.number(l.value)) }
                    item {
                        Text(
                            "Golos/jogo considera só jogadores com pelo menos 3 jogos. Fonte: ESPN.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

fun rank(boards: List<LeaderBoard>, metric: ExploreMetric): List<Leader> {
    val goals = boards.firstOrNull { it.key == "goalsLeaders" }?.leaders.orEmpty()
    val list = when (metric) {
        ExploreMetric.GOALS -> goals
        ExploreMetric.ASSISTS -> boards.firstOrNull { it.key == "assistsLeaders" }?.leaders.orEmpty()
        ExploreMetric.GOALS_PER_GAME -> goals.filter { (it.appearances ?: 0) >= 3 }
            .map { it.copy(value = it.value / it.appearances!!) }
            .sortedByDescending { it.value }
    }
    return list.take(30).mapIndexed { i, l -> l.copy(rank = i + 1) }
}

@Composable
fun HeatBar(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(lerp(Yellow, LiveRed, f)))
    }
}
