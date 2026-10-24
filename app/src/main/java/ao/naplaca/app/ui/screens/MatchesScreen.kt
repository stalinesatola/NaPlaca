package ao.naplaca.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ao.naplaca.app.ui.Format
import ao.naplaca.app.ui.appViewModel
import ao.naplaca.app.ui.components.LoadingBox
import ao.naplaca.app.ui.components.MatchRow
import ao.naplaca.app.ui.components.MessageBox
import ao.naplaca.app.ui.components.SectionHeader
import ao.naplaca.app.ui.components.friendlyError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class MatchFilter(val label: String) { ALL("Todos"), LIVE("Ao vivo"), MY_TEAMS("Minhas equipas"), FOLLOWED("Seguidos") }

data class MatchesUiState(
    val date: LocalDate = LocalDate.now(),
    val filter: MatchFilter = MatchFilter.ALL,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val matches: List<Match> = emptyList(),
    val error: String? = null,
    val updatedAt: Long = 0L,
)

class MatchesViewModel(private val c: AppContainer) : ViewModel() {
    private val raw = MutableStateFlow(MatchesUiState())
    private var loadJob: Job? = null

    val followedIds = c.favorites.followedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val teamKeys = c.favorites.teamKeys.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val state: StateFlow<MatchesUiState> = combine(raw, followedIds, teamKeys) { s, followed, teams ->
        val filtered = when (s.filter) {
            MatchFilter.ALL -> s.matches
            MatchFilter.LIVE -> s.matches.filter { it.isLive }
            MatchFilter.FOLLOWED -> s.matches.filter { it.id in followed }
            MatchFilter.MY_TEAMS -> s.matches.filter { m ->
                FavoritesRepository.key(m.source, m.home.id) in teams || FavoritesRepository.key(m.source, m.away.id) in teams
            }
        }
        s.copy(matches = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchesUiState())

    private var leagues: Set<String> = Leagues.defaultSelection

    init {
        viewModelScope.launch {
            c.profileStore.profile.map { it.leagues }.distinctUntilChanged().collect {
                leagues = it
                load()
            }
        }
        // Atualização automática enquanto houver jogos ao vivo.
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                if (raw.value.matches.any { it.isLive } || raw.value.matches.any { it.state == MatchState.PRE && it.startEpochMillis < System.currentTimeMillis() }) {
                    load(silent = true)
                }
            }
        }
    }

    fun setDate(date: LocalDate) {
        if (date == raw.value.date) return
        raw.update { it.copy(date = date, matches = emptyList(), loading = true) }
        load()
    }

    fun setFilter(filter: MatchFilter) = raw.update { it.copy(filter = filter) }

    fun refresh() {
        raw.update { it.copy(refreshing = true) }
        load(silent = true)
    }

    private fun load(silent: Boolean = false) {
        loadJob?.cancel()
        val date = raw.value.date
        if (!silent) raw.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            c.football.matchesForDay(date, leagues).collect { r ->
                raw.update {
                    it.copy(
                        matches = r.data,
                        loading = false,
                        refreshing = if (r.fromCache && r.error == null) it.refreshing else false,
                        error = r.error?.let(::friendlyError),
                        updatedAt = r.updatedAt,
                    )
                }
            }
        }
    }

    fun toggleFollow(match: Match, onFollowed: () -> Unit) = viewModelScope.launch {
        val was = match.id in followedIds.value
        c.favorites.toggleFollow(match)
        if (!was) onFollowed()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MatchesScreen(
    onOpenMatch: (Match) -> Unit,
    requestNotifications: () -> Unit,
) {
    val vm = appViewModel { MatchesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val followed by vm.followedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NaPlaca", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { showPicker = true }) { Icon(Icons.Outlined.CalendarMonth, "Escolher data") }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Outlined.Refresh, "Atualizar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val days = remember(today) { (-3L..7L).map { today.plusDays(it) } }
            val dayState = rememberLazyListState(initialFirstVisibleItemIndex = 1)
            LazyRow(
                state = dayState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(days) { d ->
                    FilterChip(selected = d == state.date, onClick = { vm.setDate(d) }, label = { Text(Format.dayChip(d, today)) })
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MatchFilter.entries) { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { vm.setFilter(f) },
                        label = {
                            val n = if (f == MatchFilter.LIVE) state.matches.count { it.isLive }.takeIf { state.filter == f } else null
                            Text(if (n != null) "${f.label} ($n)" else f.label)
                        },
                    )
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading && state.matches.isEmpty() -> LoadingBox()
                    state.matches.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            if (state.error != null) {
                                MessageBox("Não foi possível carregar", message = state.error, error = true, actionLabel = "Tentar de novo", onAction = vm::refresh)
                            } else {
                                MessageBox(
                                    when (state.filter) {
                                        MatchFilter.LIVE -> "Nenhum jogo ao vivo agora"
                                        MatchFilter.MY_TEAMS -> "As suas equipas não jogam neste dia"
                                        MatchFilter.FOLLOWED -> "Não está a seguir jogos neste dia"
                                        MatchFilter.ALL -> "Sem jogos neste dia"
                                    },
                                    message = if (state.filter == MatchFilter.ALL) "Escolha mais ligas no separador Ligas." else null,
                                )
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Text(
                                Format.dayLong(state.date) + (if (state.updatedAt > 0) " · atualizado ${Format.ago(state.updatedAt)}" else ""),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            if (state.error != null) {
                                Text(
                                    "Sem ligação — a mostrar dados guardados. ${state.error}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        state.matches.groupBy { it.leagueSlug }.forEach { (slug, matches) ->
                            stickyHeader(key = "h-$slug") { SectionHeader(matches.first().leagueName) }
                            items(matches, key = { it.id }) { m ->
                                MatchRow(
                                    match = m,
                                    followed = m.id in followed,
                                    onClick = { onOpenMatch(m) },
                                    onToggleFollow = {
                                        vm.toggleFollow(m) {
                                            requestNotifications()
                                            AlertScheduler.kick(context)
                                        }
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(pickerState) }
    }

}
