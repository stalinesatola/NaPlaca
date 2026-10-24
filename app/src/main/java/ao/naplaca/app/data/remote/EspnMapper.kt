package ao.naplaca.app.data.remote

import ao.naplaca.app.domain.EventKind
import ao.naplaca.app.domain.Leader
import ao.naplaca.app.domain.LeaderBoard
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.domain.Lineup
import ao.naplaca.app.domain.LineupPlayer
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchDetail
import ao.naplaca.app.domain.MatchEvent
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.Source
import ao.naplaca.app.domain.StandingGroup
import ao.naplaca.app.domain.StandingRow
import ao.naplaca.app.domain.TeamInfo
import ao.naplaca.app.domain.TeamSide
import ao.naplaca.app.domain.TeamStat
import kotlinx.serialization.json.JsonObject

object EspnMapper {

    fun scoreboard(root: JsonObject, leagueSlug: String): List<Match> =
        root.arr("events").mapNotNull { event(it, leagueSlug) }

    /** Converte um "event" (scoreboard, calendário da equipa ou header do summary). */
    fun event(e: JsonObject, leagueSlug: String): Match? {
        val id = e.str("id") ?: return null
        val comp = e.arr("competitions").firstOrNull() ?: return null
        val competitors = comp.arr("competitors")
        val homeJson = competitors.firstOrNull { it.str("homeAway") == "home" } ?: competitors.getOrNull(0) ?: return null
        val awayJson = competitors.firstOrNull { it.str("homeAway") == "away" } ?: competitors.getOrNull(1) ?: return null
        val status = comp.obj("status") ?: e.obj("status")
        val details = comp.arr("details").mapIndexedNotNull { i, d -> detailEvent(id, i, d) }
        val start = TimeParse.epochMillis(comp.str("date") ?: e.str("date")) ?: 0L
        val (state, detail) = status(status)
        val leagueName = e.obj("league").str("name")
            ?: Leagues.bySlug(leagueSlug)?.name ?: leagueSlug
        return Match(
            id = "${Source.ESPN}:$id",
            source = Source.ESPN,
            leagueSlug = leagueSlug,
            leagueName = Leagues.bySlug(leagueSlug)?.name ?: leagueName,
            home = side(homeJson, details),
            away = side(awayJson, details),
            startEpochMillis = start,
            state = state,
            statusDetail = detail,
            venue = comp.obj("venue").str("fullName") ?: e.obj("venue").str("displayName"),
            events = details,
        )
    }

    private fun side(c: JsonObject, details: List<MatchEvent>): TeamSide {
        val team = c.obj("team")
        val id = c.str("id") ?: team.str("id") ?: ""
        val logo = team.str("logo") ?: team.arr("logos").firstOrNull().str("href")
        return TeamSide(
            id = id,
            name = team.str("displayName") ?: team.str("name") ?: "?",
            shortName = team.str("shortDisplayName") ?: team.str("abbreviation") ?: team.str("displayName") ?: "?",
            logo = logo,
            score = c.score(),
            form = c.str("form"),
            redCards = details.count { it.teamId == id && it.kind == EventKind.RED_CARD },
        )
    }

    /** Estado e texto curto em português. */
    fun status(status: JsonObject?): Pair<MatchState, String> {
        val type = status.obj("type")
        val name = type.str("name").orEmpty()
        val state = type.str("state").orEmpty()
        val clock = status.str("displayClock").orEmpty()
        return when {
            name.contains("POSTPONED") || name.contains("CANCELED") || name.contains("ABANDONED") ||
                name.contains("SUSPENDED") || name.contains("DELAYED") ->
                MatchState.POSTPONED to when {
                    name.contains("POSTPONED") -> "Adiado"
                    name.contains("CANCELED") -> "Cancelado"
                    name.contains("SUSPENDED") -> "Suspenso"
                    name.contains("DELAYED") -> "Atrasado"
                    else -> "Interrompido"
                }
            state == "in" -> MatchState.LIVE to when {
                name.contains("HALFTIME") -> "Intervalo"
                name.contains("SHOOTOUT") -> "Penáltis"
                name.contains("END_OF_REGULATION") || name.contains("END_PERIOD") -> "Fim do tempo regulamentar"
                clock.isNotBlank() -> clock
                else -> "Ao vivo"
            }
            state == "post" -> MatchState.POST to when {
                name.contains("PENALT") || type.str("shortDetail").orEmpty().contains("Pens") -> "Final (pen.)"
                name.contains("AET") || name.contains("EXTRA") -> "Final (prol.)"
                else -> "Final"
            }
            else -> MatchState.PRE to ""
        }
    }

    private fun detailEvent(matchId: String, index: Int, d: JsonObject): MatchEvent? {
        val typeText = d.obj("type").str("text").orEmpty()
        val kind = when {
            d.bool("ownGoal") -> EventKind.OWN_GOAL
            d.bool("scoringPlay") && d.bool("penaltyKick") -> EventKind.PENALTY_GOAL
            d.bool("scoringPlay") -> EventKind.GOAL
            d.bool("redCard") -> EventKind.RED_CARD
            d.bool("yellowCard") -> EventKind.YELLOW_CARD
            else -> kindFromText(typeText)
        }
        if (d.bool("shootout")) return null
        val player = d.arr("athletesInvolved").firstOrNull().str("displayName")
        val minute = d.obj("clock").str("displayValue").orEmpty()
        return MatchEvent(
            id = "$matchId-$index-${kind.name}-$minute-${player.orEmpty()}",
            kind = kind,
            minute = minute,
            teamId = d.obj("team").str("id"),
            player = player,
            text = listOfNotNull(translateType(typeText), player).joinToString(" · "),
        )
    }

    fun kindFromText(text: String): EventKind {
        val t = text.lowercase()
        return when {
            "own goal" in t -> EventKind.OWN_GOAL
            "penalty" in t && "goal" in t -> EventKind.PENALTY_GOAL
            "goal" in t -> EventKind.GOAL
            "red card" in t || "second yellow" in t -> EventKind.RED_CARD
            "yellow card" in t -> EventKind.YELLOW_CARD
            "substitution" in t -> EventKind.SUBSTITUTION
            "kickoff" in t || "halftime" in t || "half" in t || "end" in t || "start" in t -> EventKind.PERIOD
            else -> EventKind.OTHER
        }
    }

    private fun translateType(text: String): String? {
        if (text.isBlank()) return null
        val t = text.lowercase()
        return when {
            "own goal" in t -> "Golo (autogolo)"
            "penalty" in t && "goal" in t -> "Golo (penálti)"
            "header" in t -> "Golo de cabeça"
            "goal" in t -> "Golo"
            "second yellow" in t -> "2.º amarelo"
            "red card" in t -> "Cartão vermelho"
            "yellow card" in t -> "Cartão amarelo"
            "substitution" in t -> "Substituição"
            "kickoff" in t -> "Início"
            "halftime" in t -> "Intervalo"
            "penalty" in t && "miss" in t -> "Penálti falhado"
            "penalty" in t && "saved" in t -> "Penálti defendido"
            else -> text
        }
    }

    fun summary(root: JsonObject, leagueSlug: String): MatchDetail? {
        val header = root.obj("header") ?: return null
        val match = event(header, leagueSlug) ?: return null
        val teams = root.obj("boxscore").arr("teams")
        val homeStats = teams.firstOrNull { it.obj("team").str("id") == match.home.id } ?: teams.getOrNull(0)
        val awayStats = teams.firstOrNull { it.obj("team").str("id") == match.away.id } ?: teams.getOrNull(1)
        val awayByName = awayStats.arr("statistics").associateBy { it.str("name") }
        val stats = homeStats.arr("statistics").mapNotNull { s ->
            val key = s.str("name") ?: return@mapNotNull null
            val label = STAT_LABELS[key] ?: return@mapNotNull null
            TeamStat(label, formatStat(key, s.str("displayValue")), formatStat(key, awayByName[key].str("displayValue")))
        }.sortedBy { st -> STAT_LABELS.values.indexOf(st.label) }

        val lineups = root.arr("rosters").map { r ->
            val team = r.obj("team")
            Lineup(
                teamId = team.str("id").orEmpty(),
                teamName = team.str("displayName").orEmpty(),
                formation = r.str("formation"),
                players = r.arr("roster").map { p ->
                    LineupPlayer(
                        name = p.obj("athlete").str("displayName") ?: "?",
                        jersey = p.str("jersey"),
                        position = p.obj("position").str("abbreviation"),
                        starter = p.bool("starter"),
                        subbedIn = p.bool("subbedIn"),
                        subbedOut = p.bool("subbedOut"),
                    )
                },
            )
        }

        val timeline = root.arr("keyEvents").mapNotNull { k ->
            val typeText = k.obj("type").str("text").orEmpty()
            val kind = if (k.bool("scoringPlay")) {
                kindFromText(typeText).takeIf { it in GOAL_KINDS } ?: EventKind.GOAL
            } else {
                kindFromText(typeText)
            }
            if (k.bool("shootout")) return@mapNotNull null
            val player = k.arr("participants").firstOrNull().obj("athlete").str("displayName")
            MatchEvent(
                id = k.str("id") ?: return@mapNotNull null,
                kind = kind,
                minute = k.obj("clock").str("displayValue").orEmpty(),
                teamId = k.obj("team").str("id"),
                player = player,
                text = if (kind == EventKind.PERIOD || kind == EventKind.OTHER) {
                    k.str("text") ?: typeText
                } else {
                    listOfNotNull(translateType(typeText), player).joinToString(" · ")
                },
            )
        }
        return MatchDetail(match = match.copy(events = match.events.ifEmpty { timeline.filter { it.kind != EventKind.PERIOD } }), stats = stats, lineups = lineups, timeline = timeline)
    }

    private val GOAL_KINDS = setOf(EventKind.GOAL, EventKind.OWN_GOAL, EventKind.PENALTY_GOAL)

    private val STAT_LABELS = linkedMapOf(
        "possessionPct" to "Posse de bola",
        "totalShots" to "Remates",
        "shotsOnTarget" to "Remates à baliza",
        "wonCorners" to "Cantos",
        "foulsCommitted" to "Faltas",
        "offsides" to "Foras de jogo",
        "yellowCards" to "Cartões amarelos",
        "redCards" to "Cartões vermelhos",
        "saves" to "Defesas",
        "totalPasses" to "Passes",
        "passPct" to "Precisão de passe",
        "totalTackles" to "Desarmes",
        "interceptions" to "Interceções",
    )

    private fun formatStat(key: String, value: String?): String {
        val v = value ?: return "-"
        return when (key) {
            "possessionPct" -> "$v%"
            "passPct" -> v.toDoubleOrNull()?.let { if (it <= 1.0) "${(it * 100).toInt()}%" else "${it.toInt()}%" } ?: v
            else -> v
        }
    }

    fun standings(root: JsonObject): List<StandingGroup> {
        val children = root.arr("children").ifEmpty { listOf(root) }
        return children.mapNotNull { child ->
            val entries = child.obj("standings").arr("entries")
            if (entries.isEmpty()) return@mapNotNull null
            val rows = entries.map { e ->
                val team = e.obj("team")
                val stats = e.arr("stats").associate { (it.str("name") ?: "") to it }
                fun s(k: String) = stats[k].double("value")?.toInt() ?: stats[k].str("displayValue")?.toIntOrNull() ?: 0
                StandingRow(
                    rank = s("rank"),
                    teamId = team.str("id").orEmpty(),
                    teamName = team.str("displayName") ?: team.str("name") ?: "?",
                    logo = team.arr("logos").firstOrNull().str("href"),
                    played = s("gamesPlayed"),
                    wins = s("wins"),
                    draws = s("ties"),
                    losses = s("losses"),
                    goalsFor = s("pointsFor"),
                    goalsAgainst = s("pointsAgainst"),
                    goalDiff = s("pointDifferential"),
                    points = s("points"),
                    note = e.obj("note").str("description"),
                )
            }.sortedBy { if (it.rank == 0) Int.MAX_VALUE else it.rank }
            StandingGroup(child.str("name") ?: "", rows)
        }
    }

    fun leaders(root: JsonObject): List<LeaderBoard> = root.arr("stats").map { board ->
        val key = board.str("name").orEmpty()
        LeaderBoard(
            key = key,
            title = when (key) {
                "goalsLeaders" -> "Melhores marcadores"
                "assistsLeaders" -> "Assistências"
                else -> board.str("displayName") ?: key
            },
            leaders = board.arr("leaders").mapIndexed { i, l ->
                val athlete = l.obj("athlete")
                val team = athlete.obj("team")
                val apps = athlete.arr("statistics").firstOrNull { it.str("name") == "appearances" }.double("value")
                Leader(
                    rank = i + 1,
                    playerId = athlete.str("id").orEmpty(),
                    playerName = athlete.str("displayName") ?: "?",
                    teamId = team.str("id"),
                    teamName = team.str("displayName") ?: team.str("name"),
                    teamLogo = team.arr("logos").firstOrNull().str("href"),
                    value = l.double("value") ?: 0.0,
                    appearances = apps?.toInt(),
                )
            },
        )
    }

    fun team(root: JsonObject, leagueSlug: String): TeamInfo? {
        val t = root.obj("team") ?: return null
        return TeamInfo(
            id = t.str("id") ?: return null,
            source = Source.ESPN,
            leagueSlug = leagueSlug,
            name = t.str("displayName") ?: "?",
            logo = t.arr("logos").firstOrNull().str("href"),
            color = t.str("color"),
            standingSummary = t.str("standingSummary"),
        )
    }
}
