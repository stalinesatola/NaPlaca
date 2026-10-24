package ao.naplaca.app.domain

import kotlinx.serialization.Serializable

/** De onde vêm os dados de um jogo/equipa. */
enum class Source { ESPN, SPORTSDB }

enum class MatchState { PRE, LIVE, POST, POSTPONED }

@Serializable
data class TeamSide(
    val id: String,
    val name: String,
    val shortName: String,
    val logo: String?,
    val score: Int?,
    val form: String? = null,
    val redCards: Int = 0,
)

enum class EventKind { GOAL, OWN_GOAL, PENALTY_GOAL, YELLOW_CARD, RED_CARD, SUBSTITUTION, PERIOD, OTHER }

@Serializable
data class MatchEvent(
    val id: String,
    val kind: EventKind,
    val minute: String,
    val teamId: String?,
    val player: String?,
    val text: String,
)

@Serializable
data class Match(
    /** Id composto, ex.: "ESPN:401879256" ou "SPORTSDB:2578263". */
    val id: String,
    val source: Source,
    val leagueSlug: String,
    val leagueName: String,
    val home: TeamSide,
    val away: TeamSide,
    val startEpochMillis: Long,
    val state: MatchState,
    /** Ex.: "67'", "Intervalo", "Final", "15:30". */
    val statusDetail: String,
    val venue: String? = null,
    val events: List<MatchEvent> = emptyList(),
) {
    val rawId: String get() = id.substringAfter(':')
    val isLive: Boolean get() = state == MatchState.LIVE
    fun involves(teamId: String) = home.id == teamId || away.id == teamId
}

@Serializable
data class TeamStat(val label: String, val home: String, val away: String)

@Serializable
data class LineupPlayer(
    val name: String,
    val jersey: String?,
    val position: String?,
    val starter: Boolean,
    val subbedIn: Boolean,
    val subbedOut: Boolean,
)

@Serializable
data class Lineup(val teamId: String, val teamName: String, val formation: String?, val players: List<LineupPlayer>)

@Serializable
data class MatchDetail(
    val match: Match,
    val stats: List<TeamStat>,
    val lineups: List<Lineup>,
    val timeline: List<MatchEvent>,
)

@Serializable
data class StandingRow(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val logo: String?,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val points: Int,
    val form: String? = null,
    val note: String? = null,
)

@Serializable
data class StandingGroup(val name: String, val rows: List<StandingRow>)

@Serializable
data class Leader(
    val rank: Int,
    val playerId: String,
    val playerName: String,
    val teamId: String?,
    val teamName: String?,
    val teamLogo: String?,
    val value: Double,
    val appearances: Int?,
)

@Serializable
data class LeaderBoard(val key: String, val title: String, val leaders: List<Leader>)

@Serializable
data class TeamInfo(
    val id: String,
    val source: Source,
    val leagueSlug: String,
    val name: String,
    val logo: String?,
    val color: String?,
    val standingSummary: String?,
)
