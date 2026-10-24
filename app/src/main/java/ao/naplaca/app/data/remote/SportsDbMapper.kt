package ao.naplaca.app.data.remote

import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.Source
import ao.naplaca.app.domain.StandingGroup
import ao.naplaca.app.domain.StandingRow
import ao.naplaca.app.domain.TeamInfo
import ao.naplaca.app.domain.TeamSide
import kotlinx.serialization.json.JsonObject

object SportsDbMapper {

    /** Lê "events" ou "results" (a API usa os dois nomes). */
    fun events(root: JsonObject, leagueSlug: String, leagueName: String): List<Match> =
        (root.arr("events") + root.arr("results")).mapNotNull { event(it, leagueSlug, leagueName) }

    fun event(e: JsonObject, leagueSlug: String, leagueName: String): Match? {
        val id = e.str("idEvent") ?: return null
        val start = TimeParse.epochMillis(e.str("strTimestamp"))
            ?: TimeParse.epochMillis(listOfNotNull(e.str("dateEvent"), e.str("strTime")?.take(5)).joinToString("T"))
            ?: 0L
        val (state, detail) = status(e.str("strStatus"), e.str("strProgress"))
        val homeScore = e.int("intHomeScore")
        val awayScore = e.int("intAwayScore")
        return Match(
            id = "${Source.SPORTSDB}:$id",
            source = Source.SPORTSDB,
            leagueSlug = leagueSlug,
            leagueName = leagueName,
            home = TeamSide(
                id = e.str("idHomeTeam").orEmpty(),
                name = e.str("strHomeTeam") ?: "?",
                shortName = e.str("strHomeTeam") ?: "?",
                logo = e.str("strHomeTeamBadge"),
                score = if (state == MatchState.PRE) null else homeScore,
            ),
            away = TeamSide(
                id = e.str("idAwayTeam").orEmpty(),
                name = e.str("strAwayTeam") ?: "?",
                shortName = e.str("strAwayTeam") ?: "?",
                logo = e.str("strAwayTeamBadge"),
                score = if (state == MatchState.PRE) null else awayScore,
            ),
            startEpochMillis = start,
            state = state,
            statusDetail = detail,
            venue = e.str("strVenue"),
        )
    }

    fun status(status: String?, progress: String?): Pair<MatchState, String> {
        val s = status.orEmpty().uppercase()
        return when (s) {
            "", "NS", "TBD", "NOT STARTED" -> MatchState.PRE to ""
            "HT" -> MatchState.LIVE to "Intervalo"
            "1H", "2H", "ET", "BT", "LIVE", "IN PLAY" -> MatchState.LIVE to (progress?.let { "$it'" } ?: "Ao vivo")
            "P" -> MatchState.LIVE to "Penáltis"
            "FT", "MATCH FINISHED" -> MatchState.POST to "Final"
            "AET" -> MatchState.POST to "Final (prol.)"
            "PEN" -> MatchState.POST to "Final (pen.)"
            "PST", "POSTPONED" -> MatchState.POSTPONED to "Adiado"
            "CANC", "CANCELLED" -> MatchState.POSTPONED to "Cancelado"
            "ABD", "ABANDONED" -> MatchState.POSTPONED to "Abandonado"
            "SUSP" -> MatchState.POSTPONED to "Suspenso"
            else -> MatchState.PRE to ""
        }
    }

    fun table(root: JsonObject): List<StandingGroup> {
        val rows = root.arr("table").map { r ->
            StandingRow(
                rank = r.int("intRank") ?: 0,
                teamId = r.str("idTeam").orEmpty(),
                teamName = r.str("strTeam") ?: "?",
                logo = r.str("strBadge")?.removeSuffix("/tiny"),
                played = r.int("intPlayed") ?: 0,
                wins = r.int("intWin") ?: 0,
                draws = r.int("intDraw") ?: 0,
                losses = r.int("intLoss") ?: 0,
                goalsFor = r.int("intGoalsFor") ?: 0,
                goalsAgainst = r.int("intGoalsAgainst") ?: 0,
                goalDiff = r.int("intGoalDifference") ?: 0,
                points = r.int("intPoints") ?: 0,
                form = r.str("strForm"),
                note = r.str("strDescription"),
            )
        }.sortedBy { it.rank }
        return if (rows.isEmpty()) emptyList() else listOf(StandingGroup("", rows))
    }

    fun team(root: JsonObject, leagueSlug: String): TeamInfo? {
        val t = root.arr("teams").firstOrNull() ?: return null
        return TeamInfo(
            id = t.str("idTeam") ?: return null,
            source = Source.SPORTSDB,
            leagueSlug = leagueSlug,
            name = t.str("strTeam") ?: "?",
            logo = t.str("strBadge"),
            color = t.str("strColour1")?.removePrefix("#"),
            standingSummary = t.str("strStadium")?.let { "Estádio: $it" },
        )
    }
}
