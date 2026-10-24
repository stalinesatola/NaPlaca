package ao.naplaca.app.data.repository

import ao.naplaca.app.data.local.CacheDao
import ao.naplaca.app.data.local.CacheEntity
import ao.naplaca.app.data.remote.EspnApi
import ao.naplaca.app.data.remote.EspnMapper
import ao.naplaca.app.data.remote.Network
import ao.naplaca.app.data.remote.SportsDbApi
import ao.naplaca.app.data.remote.SportsDbMapper
import ao.naplaca.app.domain.LeaderBoard
import ao.naplaca.app.domain.League
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchDetail
import ao.naplaca.app.domain.Source
import ao.naplaca.app.domain.StandingGroup
import ao.naplaca.app.domain.TeamInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Resultado com indicação de origem — permite mostrar a cache logo e depois atualizar. */
data class Loaded<T>(val data: T, val fromCache: Boolean, val error: Throwable? = null, val updatedAt: Long = 0L)

class FootballRepository(
    private val espn: EspnApi,
    private val sportsDb: SportsDbApi,
    private val cache: CacheDao,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val espnDate = DateTimeFormatter.ofPattern("yyyyMMdd")

    private companion object {
        val ESPN_ZONE: ZoneId = ZoneId.of("America/New_York")
    }
    private val matchList = ListSerializer(Match.serializer())

    // ---------------------------------------------------------------- jogos do dia

    /** Emite primeiro a cache (se houver) e depois os dados frescos de todas as ligas pedidas. */
    fun matchesForDay(date: LocalDate, slugs: Collection<String>): Flow<Loaded<List<Match>>> = flow {
        val key = "matches|${slugs.sorted().joinToString(",")}|$date"
        val cached = readCache(key, matchList)
        if (cached != null) emit(Loaded(cached.first, fromCache = true, updatedAt = cached.second))
        try {
            val fresh = fetchMatchesForDay(date, slugs)
            writeCache(key, matchList, fresh)
            emit(Loaded(fresh, fromCache = false, updatedAt = System.currentTimeMillis()))
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            emit(Loaded(cached?.first ?: emptyList(), fromCache = true, error = t, updatedAt = cached?.second ?: 0L))
        }
    }

    suspend fun fetchMatchesForDay(date: LocalDate, slugs: Collection<String>): List<Match> = coroutineScope {
        val leagues = slugs.mapNotNull { Leagues.bySlug(it) }
        val results = leagues.map { league ->
            async { runCatching { fetchLeagueDay(league, date) } }
        }.awaitAll()
        // Se todas falharem, propaga o erro; se só algumas falharem, mostra o que veio.
        if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }
            .distinctBy { it.id }
            .sortedWith(compareBy<Match>({ Leagues.all.indexOfFirst { l -> l.slug == it.leagueSlug } }, { it.startEpochMillis }))
    }

    private suspend fun fetchLeagueDay(league: League, date: LocalDate): List<Match> = when (league.source) {
        Source.ESPN -> espnDays(league.slug, espnDatesFor(date))
            .filter { localDate(it.startEpochMillis) == date }
        Source.SPORTSDB -> SportsDbMapper.events(
            sportsDb.eventsDay(date.toString(), league.sportsDbId!!), league.slug, league.name,
        )
    }

    /**
     * A ESPN agrupa os jogos por dia de Nova Iorque e não aceita intervalos de datas.
     * Devolve os dias de Nova Iorque que se sobrepõem ao dia local pedido (1 ou 2).
     */
    fun espnDatesFor(date: LocalDate): Set<LocalDate> {
        val start = date.atStartOfDay(zone())
        val end = date.plusDays(1).atStartOfDay(zone()).minusNanos(1)
        val a = start.withZoneSameInstant(ESPN_ZONE).toLocalDate()
        val b = end.withZoneSameInstant(ESPN_ZONE).toLocalDate()
        return generateSequence(a) { it.plusDays(1) }.takeWhile { !it.isAfter(b) }.toSet()
    }

    /** Pede vários dias em paralelo; falha só se todos falharem. */
    private suspend fun espnDays(slug: String, dates: Set<LocalDate>): List<Match> = coroutineScope {
        val results = dates.map { d -> async { runCatching { EspnMapper.scoreboard(espn.scoreboard(slug, d.format(espnDate)), slug) } } }.awaitAll()
        if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }
    }

    private fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()

    /** Estado atual de jogos específicos (usado pelos alertas). Agrupa por liga para poupar pedidos. */
    suspend fun refreshMatches(ids: Collection<Pair<String, String>>, aroundEpochMillis: Map<String, Long>): List<Match> = coroutineScope {
        // ids: (matchId, leagueSlug)
        ids.groupBy { it.second }.map { (slug, group) ->
            async {
                runCatching {
                    val league = Leagues.bySlug(slug) ?: return@runCatching emptyList()
                    val wanted = group.map { it.first }.toSet()
                    when (league.source) {
                        Source.ESPN -> {
                            val dates = group.mapNotNull { aroundEpochMillis[it.first] }
                                .map { Instant.ofEpochMilli(it).atZone(ESPN_ZONE).toLocalDate() }
                                .ifEmpty { listOf(LocalDate.now(ESPN_ZONE)) }
                                .toSet()
                            espnDays(slug, dates).filter { it.id in wanted }
                        }
                        Source.SPORTSDB -> wanted.mapNotNull { id ->
                            SportsDbMapper.events(sportsDb.event(id.substringAfter(':')), slug, league.name).firstOrNull()
                        }
                    }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    // ---------------------------------------------------------------- detalhe

    suspend fun matchDetail(matchId: String, leagueSlug: String): MatchDetail {
        val source = Source.valueOf(matchId.substringBefore(':'))
        val raw = matchId.substringAfter(':')
        return when (source) {
            Source.ESPN -> EspnMapper.summary(espn.summary(leagueSlug, raw), leagueSlug)
                ?: error("Jogo não encontrado")
            Source.SPORTSDB -> {
                val name = Leagues.name(leagueSlug)
                val match = SportsDbMapper.events(sportsDb.event(raw), leagueSlug, name).firstOrNull()
                    ?: error("Jogo não encontrado")
                MatchDetail(match, stats = emptyList(), lineups = emptyList(), timeline = emptyList())
            }
        }
    }

    // ---------------------------------------------------------------- ligas

    fun standings(slug: String): Flow<Loaded<List<StandingGroup>>> =
        cached("standings|$slug", ListSerializer(StandingGroup.serializer())) {
            val league = Leagues.bySlug(slug) ?: error("Liga desconhecida")
            when (league.source) {
                Source.ESPN -> EspnMapper.standings(espn.standings(slug))
                Source.SPORTSDB -> SportsDbMapper.table(sportsDb.table(league.sportsDbId!!, league.sportsDbSeason!!))
            }
        }

    fun leaders(slug: String): Flow<Loaded<List<LeaderBoard>>> =
        cached("leaders|$slug", ListSerializer(LeaderBoard.serializer())) {
            val league = Leagues.bySlug(slug) ?: error("Liga desconhecida")
            if (league.source != Source.ESPN) emptyList() else EspnMapper.leaders(espn.leaders(slug))
        }

    /** Jogos recentes e próximos de uma liga (calendário). */
    fun leagueFixtures(slug: String): Flow<Loaded<List<Match>>> = cached("fixtures|$slug", matchList) {
        val league = Leagues.bySlug(slug) ?: error("Liga desconhecida")
        when (league.source) {
            Source.ESPN -> coroutineScope {
                // Jornada atual (sem data) + uma semana e meia à volta de hoje, dia a dia.
                val today = LocalDate.now(ESPN_ZONE)
                val current = async { runCatching { EspnMapper.scoreboard(espn.scoreboard(slug), slug) }.getOrDefault(emptyList()) }
                val days = espnDays(slug, (-3L..7L).map { today.plusDays(it) }.toSet())
                current.await() + days
            }
            Source.SPORTSDB -> coroutineScope {
                val past = async { runCatching { sportsDb.pastLeague(league.sportsDbId!!) }.getOrNull() }
                val next = async { runCatching { sportsDb.nextLeague(league.sportsDbId!!) }.getOrNull() }
                val season = async { runCatching { sportsDb.season(league.sportsDbId!!, league.sportsDbSeason!!) }.getOrNull() }
                listOfNotNull(past.await(), next.await(), season.await())
                    .flatMap { SportsDbMapper.events(it, slug, league.name) }
            }
        }.distinctBy { it.id }.sortedBy { it.startEpochMillis }
    }

    // ---------------------------------------------------------------- equipas

    suspend fun team(source: Source, slug: String, teamId: String): TeamInfo? = when (source) {
        Source.ESPN -> EspnMapper.team(espn.team(slug, teamId), slug)
        Source.SPORTSDB -> SportsDbMapper.team(sportsDb.team(teamId), slug)
    }

    suspend fun teamMatches(source: Source, slug: String, teamId: String): List<Match> = coroutineScope {
        when (source) {
            Source.ESPN -> {
                val past = async { runCatching { espn.teamSchedule(slug, teamId) }.getOrNull() }
                val next = async { runCatching { espn.teamSchedule(slug, teamId, fixture = true) }.getOrNull() }
                listOfNotNull(past.await(), next.await()).flatMap { EspnMapper.scoreboard(it, slug) }
            }
            Source.SPORTSDB -> {
                val name = Leagues.name(slug)
                val past = async { runCatching { sportsDb.teamLast(teamId) }.getOrNull() }
                val next = async { runCatching { sportsDb.teamNext(teamId) }.getOrNull() }
                listOfNotNull(past.await(), next.await()).flatMap { SportsDbMapper.events(it, slug, name) }
            }
        }.distinctBy { it.id }.sortedBy { it.startEpochMillis }
    }

    // ---------------------------------------------------------------- cache

    private fun <T> cached(key: String, serializer: KSerializer<T>, fetch: suspend () -> T): Flow<Loaded<T>> = flow {
        val c = readCache(key, serializer)
        if (c != null) emit(Loaded(c.first, fromCache = true, updatedAt = c.second))
        try {
            val fresh = fetch()
            writeCache(key, serializer, fresh)
            emit(Loaded(fresh, fromCache = false, updatedAt = System.currentTimeMillis()))
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (c == null) throw t
            emit(Loaded(c.first, fromCache = true, error = t, updatedAt = c.second))
        }
    }

    private suspend fun <T> readCache(key: String, serializer: KSerializer<T>): Pair<T, Long>? {
        val entry = cache.get(key) ?: return null
        return runCatching { Network.json.decodeFromString(serializer, entry.json) to entry.updatedAt }.getOrNull()
    }

    private suspend fun <T> writeCache(key: String, serializer: KSerializer<T>, value: T) {
        cache.put(CacheEntity(key, Network.json.encodeToString(serializer, value)))
    }

    suspend fun pruneCache() {
        cache.deleteOlderThan(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
    }
}
