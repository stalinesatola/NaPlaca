package ao.naplaca.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.naplaca.app.AppContainer
import ao.naplaca.app.data.repository.Loaded
import ao.naplaca.app.domain.Leader
import ao.naplaca.app.domain.LeaderBoard
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.Source
import ao.naplaca.app.domain.StandingGroup
import ao.naplaca.app.domain.StandingRow
import ao.naplaca.app.ui.Format
import ao.naplaca.app.ui.appViewModel
import ao.naplaca.app.ui.components.LoadingBox
import ao.naplaca.app.ui.components.MatchRow
import ao.naplaca.app.ui.components.MessageBox
import ao.naplaca.app.ui.components.SectionHeader
import ao.naplaca.app.ui.components.TeamLogo
import ao.naplaca.app.ui.components.friendlyError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado genérico de um bloco de dados carregado com cache. */
data class Block<T>(val loading: Boolean = true, val data: T? = null, val error: String? = null)

fun <T> ViewModel.collectBlock(source: () -> Flow<Loaded<T>>): StateFlow<Block<T>> {
    val state = MutableStateFlow(Block<T>())
    viewModelScope.launch {
        source()
            .catch { t -> state.value = state.value.copy(loading = false, error = friendlyError(t)) }
            .collect { r -> state.value = Block(loading = false, data = r.data, error = r.error?.let(::friendlyError)) }
    }
    return state.asStateFlow()
}

class LeagueViewModel(c: AppContainer, slug: String) : ViewModel() {
    val standings = collectBlock { c.football.standings(slug) }
    val fixtures = collectBlock { c.football.leagueFixtures(slug) }
    val leaders = collectBlock { c.football.leaders(slug) }
    val followed = c.favorites.followedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeagueScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenMatch: (Match) -> Unit,
    onOpenTeam: (Source, String, String) -> Unit,
) {
    val league = Leagues.bySlug(slug)
    val vm = appViewModel(key = "league-$slug") { LeagueViewModel(it, slug) }
    val tabs = buildList {
        add("Tabela"); add("Jogos")
        if (league?.hasLeaders == true) add("Marcadores")
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(league?.name ?: slug) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            val source = league?.source ?: Source.ESPN
            when (tab) {
                0 -> {
                    val s by vm.standings.collectAsStateWithLifecycle()
                    BlockContent(s, empty = "Tabela indisponível") { groups -> StandingsList(groups) { row -> onOpenTeam(source, slug, row.teamId) } }
                }
                1 -> {
                    val s by vm.fixtures.collectAsStateWithLifecycle()
                    val followed by vm.followed.collectAsStateWithLifecycle()
                    BlockContent(s, empty = "Sem jogos próximos") { matches -> FixturesList(matches, followed, onOpenMatch) }
                }
                else -> {
                    val s by vm.leaders.collectAsStateWithLifecycle()
                    BlockContent(s, empty = "Sem dados de marcadores") { boards -> LeadersList(boards) }
                }
            }
        }
    }
}

@Composable
fun <T> BlockContent(block: Block<T>, empty: String, content: @Composable (T) -> Unit) {
    val data = block.data
    when {
        data == null && block.loading -> LoadingBox()
        data == null -> MessageBox("Não foi possível carregar", message = block.error, error = true)
        data is Collection<*> && data.isEmpty() -> MessageBox(empty, message = block.error)
        else -> content(data!!)
    }
}

@Composable
fun StandingsList(groups: List<StandingGroup>, onTeam: (StandingRow) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        groups.forEach { g ->
            if (groups.size > 1 && g.name.isNotBlank()) item(key = "g-${g.name}") { SectionHeader(g.name) }
            item(key = "head-${g.name}") { StandingHeader() }
            items(g.rows, key = { "${g.name}-${it.teamId}" }) { r ->
                StandingLine(r, onClick = { onTeam(r) })
                HorizontalDivider()
            }
        }
        item {
            Text(
                "J jogos · V vitórias · E empates · D derrotas · DG diferença de golos · P pontos",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun StandingHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("#", Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium)
        Text("Equipa", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        listOf("J", "V", "E", "D", "DG").forEach { Text(it, Modifier.width(30.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
        Text("P", Modifier.width(34.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StandingLine(r: StandingRow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(r.rank.toString(), Modifier.width(28.dp), fontWeight = FontWeight.Bold)
        TeamLogo(r.logo, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(r.teamName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        listOf(r.played, r.wins, r.draws, r.losses).forEach { Text(it.toString(), Modifier.width(30.dp), textAlign = TextAlign.Center) }
        Text(if (r.goalDiff > 0) "+${r.goalDiff}" else r.goalDiff.toString(), Modifier.width(30.dp), textAlign = TextAlign.Center)
        Text(r.points.toString(), Modifier.width(34.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FixturesList(matches: List<Match>, followed: Set<String>, onOpenMatch: (Match) -> Unit) {
    // Abre a lista perto de "agora": primeiro jogo ao vivo ou por jogar.
    val start = matches.indexOfFirst { it.state == MatchState.LIVE || it.state == MatchState.PRE }.coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(matches.size) { if (start > 0) listState.scrollToItem(start) }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(matches, key = { it.id }) { m ->
            MatchRow(m, followed = m.id in followed, onClick = { onOpenMatch(m) }, onToggleFollow = null, showDate = true)
            HorizontalDivider()
        }
    }
}

@Composable
fun LeadersList(boards: List<LeaderBoard>) {
    LazyColumn(Modifier.fillMaxSize()) {
        boards.forEach { b ->
            item(key = b.key) { SectionHeader(b.title) }
            items(b.leaders.take(20), key = { "${b.key}-${it.playerId}" }) { l -> LeaderLine(l, b.leaders.firstOrNull()?.value ?: 1.0) }
        }
    }
}

@Composable
fun LeaderLine(l: Leader, max: Double, valueText: String = Format.number(l.value)) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.rank.toString(), Modifier.width(28.dp), fontWeight = FontWeight.Bold)
            TeamLogo(l.teamLogo, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(l.playerName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(l.teamName, l.appearances?.let { "$it jogos" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(valueText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        HeatBar(fraction = if (max > 0) (l.value / max).toFloat() else 0f)
    }
}
