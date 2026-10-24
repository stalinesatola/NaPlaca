package ao.naplaca.app.data.remote

import ao.naplaca.app.Fixtures
import ao.naplaca.app.domain.Leagues
import ao.naplaca.app.domain.MatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportsDbMapperTest {

    @Test
    fun eventosDoGirabola() {
        val matches = SportsDbMapper.events(Fixtures.json("tsdb_events.json"), Leagues.GIRABOLA, "Girabola")
        assertEquals(1, matches.size)
        val m = matches.first()
        assertEquals("SPORTSDB:2578262", m.id)
        assertEquals("FC Luanda", m.home.name)
        assertEquals("Desportivo Huíla", m.away.name)
        assertEquals(0, m.home.score)
        assertEquals(2, m.away.score)
        assertEquals(MatchState.POST, m.state)
        // 2026-10-18T14:30:00 UTC
        assertEquals(1792333800000L, m.startEpochMillis)
    }

    @Test
    fun tabelaDoGirabola() {
        val groups = SportsDbMapper.table(Fixtures.json("tsdb_table.json"))
        val first = groups.single().rows.first()
        assertEquals(1, first.rank)
        assertEquals("Wiliete", first.teamName)
        assertEquals(16, first.points)
        assertTrue(first.logo!!.endsWith(".png"))
    }

    @Test
    fun estadosDaThesportsdb() {
        assertEquals(MatchState.PRE to "", SportsDbMapper.status("NS", null))
        assertEquals(MatchState.LIVE to "55'", SportsDbMapper.status("2H", "55"))
        assertEquals(MatchState.LIVE to "Intervalo", SportsDbMapper.status("HT", null))
        assertEquals(MatchState.POSTPONED to "Adiado", SportsDbMapper.status("PST", null))
    }

    @Test
    fun jogoPorComecarNaoTemResultado() {
        val json = Network.json.parseToJsonElement(
            """{"events":[{"idEvent":"1","strHomeTeam":"Petro","strAwayTeam":"1º Agosto","intHomeScore":"0","intAwayScore":"0","strStatus":"NS","strTimestamp":"2026-10-24T14:00:00"}]}""",
        ) as kotlinx.serialization.json.JsonObject
        val m = SportsDbMapper.events(json, Leagues.GIRABOLA, "Girabola").single()
        assertNull(m.home.score)
        assertEquals(MatchState.PRE, m.state)
    }
}
