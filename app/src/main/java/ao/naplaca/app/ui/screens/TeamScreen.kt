package ao.naplaca.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ao.naplaca.app.AppContainer
import ao.naplaca.app.alerts.AlertScheduler
import ao.naplaca.app.data.repository.FavoritesRepository
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.Source
import ao.naplaca.app.domain.TeamInfo
import ao.naplaca.app.ui.appViewModel
import ao.naplaca.app.ui.components.LoadingBox
import ao.naplaca.app.ui.components.MatchRow
import ao.naplaca.app.ui.components.MessageBox
import ao.naplaca.app.ui.components.SectionHeader
import ao.naplaca.app.ui.components.TeamLogo
import ao.naplaca.app.ui.components.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TeamUiState(
    val loading: Boolean = true,
    val info: TeamInfo? = null,
    val matches: List<Match> = emptyList(),
    val error: String? = null,
)

class TeamViewModel(private val c: AppContainer, private val source: Source, private val slug: String, private val teamId: String) : ViewModel() {
    private val _state = MutableStateFlow(TeamUiState())
    val state: StateFlow<TeamUiState> = _state.asStateFlow()
    val favorite = c.favorites.teamKeys.map { FavoritesRepository.key(source, teamId) in it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            try {
                val info = async { runCatching { c.football.team(source, slug, teamId) }.getOrNull() }
                val matches = c.football.teamMatches(source, slug, teamId)
                _state.value = TeamUiState(loading = false, info = info.await(), matches = matches)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.value = TeamUiState(loading = false, error = friendlyError(t))
            }
        }
    }

    /** Devolve true se a equipa passou a favorita. */
    fun toggleFavorite(onAdded: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val fromMatch = s.matches.firstNotNullOfOrNull { m ->
            when (teamId) { m.home.id -> m.home; m.away.id -> m.away; else -> null }
        }
        val name = s.info?.name ?: fromMatch?.name ?: return@launch
        val was = favorite.value
        c.favorites.toggleTeam(source, slug, teamId, name, s.info?.logo ?: fromMatch?.logo)
        if (!was) onAdded()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    source: Source,
    slug: String,
    teamId: String,
    onBack: () -> Unit,
    onOpenMatch: (Match) -> Unit,
    requestNotifications: () -> Unit,
) {
    val vm = appViewModel(key = "team-$source-$teamId") { TeamViewModel(it, source, slug, teamId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val teamName = state.info?.name ?: state.matches.firstNotNullOfOrNull { m ->
        when (teamId) { m.home.id -> m.home.name; m.away.id -> m.away.name; else -> null }
    }
    val logo = state.info?.logo ?: state.matches.firstNotNullOfOrNull { m ->
        when (teamId) { m.home.id -> m.home.logo; m.away.id -> m.away.logo; else -> null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(teamName ?: "Equipa") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = {
                        vm.toggleFavorite {
                            requestNotifications()
                            AlertScheduler.kick(context)
                        }
                    }) {
                        Icon(
                            if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                            tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null && state.matches.isEmpty() -> MessageBox("Não foi possível carregar", Modifier.padding(padding), message = state.error, error = true)
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamLogo(logo, size = 64.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(teamName ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(Leagues.name(slug), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.info?.standingSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            val form = recentForm(state.matches, teamId)
                            if (form.isNotEmpty()) FormBadges(form)
                        }
                    }
                    if (!favorite) {
                        Text(
                            "Adicione aos favoritos (coração) para receber alertas automáticos de todos os jogos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                val upcoming = state.matches.filter { it.state == MatchState.PRE || it.state == MatchState.LIVE }
                val past = state.matches.filter { it.state == MatchState.POST }.sortedByDescending { it.startEpochMillis }
                if (upcoming.isNotEmpty()) {
                    item { SectionHeader("Próximos jogos") }
                    items(upcoming, key = { "u-${it.id}" }) { m ->
                        MatchRow(m, followed = false, onClick = { onOpenMatch(m) }, onToggleFollow = null, showDate = true)
                        HorizontalDivider()
                    }
                }
                if (past.isNotEmpty()) {
                    item { SectionHeader("Resultados") }
                    items(past, key = { "p-${it.id}" }) { m ->
                        MatchRow(m, followed = false, onClick = { onOpenMatch(m) }, onToggleFollow = null, showDate = true)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Forma (V/E/D) dos últimos 5 jogos terminados, no formato W/D/L. */
fun recentForm(matches: List<Match>, teamId: String): String =
    matches.filter { it.state == MatchState.POST && it.involves(teamId) && it.home.score != null && it.away.score != null }
        .sortedBy { it.startEpochMillis }
        .takeLast(5)
        .joinToString("") { m ->
            val mine = if (m.home.id == teamId) m.home.score!! else m.away.score!!
            val other = if (m.home.id == teamId) m.away.score!! else m.home.score!!
            when {
                mine > other -> "W"
                mine == other -> "D"
                else -> "L"
            }
        }
