package ao.naplaca.app.data.remote

import ao.naplaca.app.Fixtures
import ao.naplaca.app.domain.EventKind
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnMapperTest {

    @Test
    fun scoreboardConverteJogosTerminadosComGolosECartoes() {
        val matches = EspnMapper.scoreboard(Fixtures.json("espn_scoreboard.json"), "eng.1")
        assertEquals(3, matches.size)

        val m = matches.first { it.id == "ESPN:401879263" }
        assertEquals(Source.ESPN, m.source)
        assertEquals("Premier League", m.leagueName)
        assertEquals("AFC Bournemouth", m.home.name)
        assertEquals("Sunderland", m.away.name)
        assertEquals(2, m.home.score)
        assertEquals(0, m.away.score)
        assertEquals(MatchState.POST, m.state)
        assertEquals("Final", m.statusDetail)
        assertEquals(1, m.away.redCards)
        assertEquals(0, m.home.redCards)
        assertTrue(m.startEpochMillis > 0)

        val goals = m.events.filter { it.kind == EventKind.GOAL }
        assertEquals(2, goals.size)
        assertEquals("Marcus Tavernier", goals.first().player)
        assertEquals("6'", goals.first().minute)
    }

    @Test
    fun autogoloEReconhecido() {
        val m = EspnMapper.scoreboard(Fixtures.json("espn_scoreboard.json"), "eng.1").first { it.id == "ESPN:401879261" }
        assertTrue(m.events.any { it.kind == EventKind.OWN_GOAL })
    }

    @Test
    fun summaryTrazEstatisticasEscalacoesELances() {
        val d = EspnMapper.summary(Fixtures.json("espn_summary.json"), "eng.1")
        assertNotNull(d)
        d!!
        assertEquals("ESPN:401879263", d.match.id)
        val possession = d.stats.first { it.label == "Posse de bola" }
        assertEquals("65%", possession.home)
        assertEquals("Posse de bola", d.stats.first().label)
        assertEquals(2, d.lineups.size)
        assertTrue(d.lineups.all { l -> l.players.count { it.starter } == 11 })
        assertTrue(d.timeline.any { it.kind == EventKind.GOAL && it.player == "Marcus Tavernier" })
    }

    @Test
    fun classificacaoOrdenadaPorPosicao() {
        val groups = EspnMapper.standings(Fixtures.json("espn_standings.json"))
        assertEquals(1, groups.size)
        val rows = groups.first().rows
        assertEquals(20, rows.size)
        assertEquals(1, rows.first().rank)
        assertEquals("Manchester City", rows.first().teamName)
        assertEquals(21, rows.first().points)
        assertEquals(12, rows.first().goalDiff)
        assertEquals(rows.sortedBy { it.rank }, rows)
    }

    @Test
    fun lideresDeGolosEAssistencias() {
        val boards = EspnMapper.leaders(Fixtures.json("espn_leaders.json"))
        val goals = boards.first { it.key == "goalsLeaders" }
        assertEquals("Melhores marcadores", goals.title)
        assertEquals("Erling Haaland", goals.leaders.first().playerName)
        assertEquals(8.0, goals.leaders.first().value, 0.0)
        assertEquals(7, goals.leaders.first().appearances)
        assertEquals("Manchester City", goals.leaders.first().teamName)
        assertTrue(boards.any { it.key == "assistsLeaders" })
    }

    @Test
    fun estadosAoVivoEAdiado() {
        fun status(name: String, state: String, clock: String = "") = Network.json.parseToJsonElement(
            """{"displayClock":"$clock","type":{"name":"$name","state":"$state"}}""",
        ) as kotlinx.serialization.json.JsonObject

        assertEquals(MatchState.LIVE to "67'", EspnMapper.status(status("STATUS_SECOND_HALF", "in", "67'")))
        assertEquals(MatchState.LIVE to "Intervalo", EspnMapper.status(status("STATUS_HALFTIME", "in", "45'")))
        assertEquals(MatchState.POSTPONED, EspnMapper.status(status("STATUS_POSTPONED", "post")).first)
        assertEquals(MatchState.PRE, EspnMapper.status(status("STATUS_SCHEDULED", "pre")).first)
        assertFalse(EspnMapper.status(status("STATUS_FULL_TIME", "post")).first == MatchState.LIVE)
    }
}
