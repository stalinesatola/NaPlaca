package ao.naplaca.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.naplaca.app.AppContainer
import ao.naplaca.app.alerts.AlertScheduler
import ao.naplaca.app.domain.EventKind
import ao.naplaca.app.domain.Lineup
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchDetail
import ao.naplaca.app.domain.MatchEvent
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.TeamSide
import ao.naplaca.app.domain.TeamStat
import ao.naplaca.app.ui.Format
import ao.naplaca.app.ui.appViewModel
import ao.naplaca.app.ui.components.LiveDot
import ao.naplaca.app.ui.components.LoadingBox
import ao.naplaca.app.ui.components.MessageBox
import ao.naplaca.app.ui.components.SectionHeader
import ao.naplaca.app.ui.components.TeamLogo
import ao.naplaca.app.ui.components.friendlyError
import ao.naplaca.app.ui.theme.LiveRed
import ao.naplaca.app.ui.theme.Yellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MatchDetailUiState(
    val loading: Boolean = true,
    val detail: MatchDetail? = null,
    val error: String? = null,
)

class MatchDetailViewModel(private val c: AppContainer, private val matchId: String, private val league: String) : ViewModel() {
    private val _state = MutableStateFlow(MatchDetailUiState())
    val state: StateFlow<MatchDetailUiState> = _state.asStateFlow()
    val followed = c.favorites.followedIds.map { matchId in it }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            load()
            while (isActive) {
                delay(30_000)
                val m = _state.value.detail?.match
                if (m != null && (m.isLive || (m.state == MatchState.PRE && m.startEpochMillis < System.currentTimeMillis()))) load()
            }
        }
    }

    fun refresh() = viewModelScope.launch { load() }

    private suspend fun load() {
        runCatching { c.football.matchDetail(matchId, league) }
            .onSuccess { d -> _state.update { MatchDetailUiState(loading = false, detail = d) } }
            .onFailure { t -> _state.update { it.copy(loading = false, error = friendlyError(t)) } }
    }

    fun toggleFollow(onFollowed: () -> Unit) = viewModelScope.launch {
        val m = _state.value.detail?.match ?: return@launch
        val was = followed.value
        c.favorites.toggleFollow(m)
        if (!was) onFollowed()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    matchId: String,
    league: String,
    onBack: () -> Unit,
    onOpenTeam: (Match, TeamSide) -> Unit,
    onOpenBantubet: () -> Unit,
    requestNotifications: () -> Unit,
) {
    val vm = appViewModel(key = "match-$matchId") { MatchDetailViewModel(it, matchId, league) }
    val state by vm.state.collectAsStateWithLifecycle()
    val followed by vm.followed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.match?.leagueName ?: "Jogo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    val m = state.detail?.match
                    if (m != null) {
                        IconButton(onClick = { shareMatch(context, m) }) { Icon(Icons.Outlined.Share, "Partilhar") }
                        if (m.state != MatchState.POST) {
                            IconButton(onClick = {
                                vm.toggleFollow {
                                    requestNotifications()
                                    AlertScheduler.kick(context)
                                }
                            }) {
                                Icon(
                                    if (followed) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                                    if (followed) "Deixar de seguir" else "Seguir jogo",
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            detail == null && state.loading -> LoadingBox(Modifier.padding(padding))
            detail == null -> MessageBox(
                "Não foi possível abrir o jogo", Modifier.padding(padding), message = state.error, error = true,
                actionLabel = "Tentar de novo", onAction = { vm.refresh() },
            )
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                ScoreHeader(detail.match, onOpenTeam)
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val tabs = listOf("Resumo", "Estatísticas", "Escalações")
                TabRow(selectedTabIndex = tab) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                when (tab) {
                    0 -> SummaryTab(detail, onOpenBantubet)
                    1 -> StatsTab(detail.match, detail.stats)
                    else -> LineupsTab(detail.lineups)
                }
            }
        }
    }
}

private fun shareMatch(context: android.content.Context, m: Match) {
    val score = if (m.state == MatchState.PRE) "${Format.dateTime(m.startEpochMillis)}" else "${m.home.score ?: 0}-${m.away.score ?: 0} (${m.statusDetail})"
    val text = "${m.home.name} x ${m.away.name} · $score · ${m.leagueName}\nVia NaPlaca"
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(send, "Partilhar jogo"))
}

@Composable
private fun ScoreHeader(m: Match, onOpenTeam: (Match, TeamSide) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamHeader(m.home, Modifier.weight(1f)) { onOpenTeam(m, m.home) }
        Column(Modifier.width(120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (m.state == MatchState.PRE) {
                Text(Format.time(m.startEpochMillis), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(Format.dateTime(m.startEpochMillis), style = MaterialTheme.typography.labelMedium)
            } else {
                Text(
                    "${m.home.score ?: 0} - ${m.away.score ?: 0}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = if (m.isLive) LiveRed else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (m.isLive) {
                        LiveDot()
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(m.statusDetail, style = MaterialTheme.typography.labelLarge, color = if (m.isLive) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            m.venue?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        TeamHeader(m.away, Modifier.weight(1f)) { onOpenTeam(m, m.away) }
    }
}

@Composable
private fun TeamHeader(team: TeamSide, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(team.logo, size = 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(team.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2)
        team.form?.let { FormBadges(it) }
    }
}

@Composable
fun FormBadges(form: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        form.takeLast(5).forEach { ch ->
            val (label, color) = when (ch.uppercaseChar()) {
                'W' -> "V" to Color(0xFF2E7D32)
                'D' -> "E" to Color(0xFF757575)
                'L' -> "D" to Color(0xFFC62828)
                else -> ch.toString() to Color.Gray
            }
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(color), contentAlignment = Alignment.Center) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SummaryTab(detail: MatchDetail, onOpenBantubet: () -> Unit) {
    val m = detail.match
    val events = detail.timeline.ifEmpty { m.events }
        .filter { it.kind != EventKind.OTHER }
    LazyColumn(Modifier.fillMaxSize()) {
        if (events.isEmpty()) {
            item {
                MessageBox(
                    if (m.state == MatchState.PRE) "O jogo ainda não começou" else "Sem lances registados",
                    message = if (m.state == MatchState.PRE) "Siga o jogo (sino) para receber alertas de golos e do resultado." else null,
                )
            }
        } else {
            item { SectionHeader("Lances") }
            items(events, key = { it.id }) { e -> EventRow(e, homeId = m.home.id) }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedButton(onClick = onOpenBantubet, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.BarChart, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ver estatísticas na Bantubet")
                }
                Text(
                    "Dados: ${if (m.source.name == "ESPN") "ESPN" else "TheSportsDB"}. Resultados podem ter atraso.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EventRow(e: MatchEvent, homeId: String) {
    val isHome = e.teamId == homeId
    val icon = when (e.kind) {
        EventKind.GOAL, EventKind.PENALTY_GOAL -> "⚽"
        EventKind.OWN_GOAL -> "⚽ (AG)"
        EventKind.YELLOW_CARD -> "🟨"
        EventKind.RED_CARD -> "🟥"
        EventKind.SUBSTITUTION -> "🔄"
        EventKind.PERIOD -> "⏱"
        EventKind.OTHER -> "•"
    }
    if (e.kind == EventKind.PERIOD) {
        Text(
            "${e.minute} ${e.text}".trim(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
        )
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val content: @Composable (Modifier, TextAlign) -> Unit = { mod, align ->
            Text("$icon ${e.text}", modifier = mod, textAlign = align, style = MaterialTheme.typography.bodyMedium)
        }
        if (isHome) content(Modifier.weight(1f), TextAlign.End) else Spacer(Modifier.weight(1f))
        Text(e.minute, Modifier.width(56.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        if (!isHome) content(Modifier.weight(1f), TextAlign.Start) else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatsTab(m: Match, stats: List<TeamStat>) {
    if (stats.isEmpty()) {
        MessageBox("Estatísticas indisponíveis", message = "Esta fonte não fornece estatísticas para este jogo.")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(m.home.shortName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(m.away.shortName, Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
        }
        items(stats, key = { it.label }) { s -> StatBar(s) }
    }
}

@Composable
private fun StatBar(s: TeamStat) {
    val h = s.home.trimEnd('%').toFloatOrNull() ?: 0f
    val a = s.away.trimEnd('%').toFloatOrNull() ?: 0f
    val total = (h + a).takeIf { it > 0f } ?: 1f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(s.home, fontWeight = if (h > a) FontWeight.Bold else FontWeight.Normal)
            Text(s.label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Text(s.away, fontWeight = if (a > h) FontWeight.Bold else FontWeight.Normal)
        }
        Row(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxWidth(h / total).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth(a / total).fillMaxHeight().background(Yellow))
            }
        }
    }
}

@Composable
private fun LineupsTab(lineups: List<Lineup>) {
    if (lineups.isEmpty() || lineups.all { it.players.isEmpty() }) {
        MessageBox("Escalações ainda não divulgadas", message = "Normalmente saem cerca de 1 hora antes do jogo.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        lineups.forEach { l ->
            item(key = "h-${l.teamId}") { SectionHeader(l.teamName + (l.formation?.let { " · $it" } ?: "")) }
            val starters = l.players.filter { it.starter }
            val bench = l.players.filter { !it.starter }
            items(starters, key = { "${l.teamId}-s-${it.name}-${it.jersey}" }) { p -> PlayerRow(p.jersey, p.name, p.position, p.subbedOut, false) }
            if (bench.isNotEmpty()) {
                item(key = "b-${l.teamId}") {
                    Text("Suplentes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(bench, key = { "${l.teamId}-b-${it.name}-${it.jersey}" }) { p -> PlayerRow(p.jersey, p.name, p.position, false, p.subbedIn) }
            }
            item { HorizontalDivider() }
        }
    }
}

@Composable
private fun PlayerRow(jersey: String?, name: String, position: String?, out: Boolean, `in`: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(jersey ?: "", Modifier.width(32.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(name, Modifier.weight(1f))
        if (out) Text("▼", color = LiveRed, modifier = Modifier.padding(end = 8.dp))
        if (`in`) Text("▲", color = Color(0xFF2E7D32), modifier = Modifier.padding(end = 8.dp))
        Text(position ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
