package ao.naplaca.app.alerts

import ao.naplaca.app.data.local.AlertPrefs
import ao.naplaca.app.domain.EventKind
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchEvent
import ao.naplaca.app.domain.MatchState
import ao.naplaca.app.domain.Source
import ao.naplaca.app.domain.TeamSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchDiffTest {
    private val now = 1_800_000_000_000L
    private val prefs = AlertPrefs()

    private fun match(
        state: MatchState = MatchState.LIVE,
        home: Int? = 0,
        away: Int? = 0,
        detail: String = "30'",
        homeRed: Int = 0,
        start: Long = now - 30 * 60_000,
        events: List<MatchEvent> = emptyList(),
    ) = Match(
        id = "ESPN:1", source = Source.ESPN, leagueSlug = "ang.girabola", leagueName = "Girabola",
        home = TeamSide("10", "Petro de Luanda", "Petro", null, home, redCards = homeRed),
        away = TeamSide("20", "1.º de Agosto", "Agosto", null, away),
        startEpochMillis = start, state = state, statusDetail = detail, events = events,
    )

    private fun snap(m: Match) = MatchDiff.snapshot(m, reminderSent = false, now = now)

    @Test
    fun primeiraObservacaoNaoInventaGolos() {
        assertTrue(MatchDiff.diff(null, match(home = 2), prefs, now).isEmpty())
    }

    @Test
    fun goloComMarcador() {
        val before = snap(match())
        val goal = MatchEvent("e1", EventKind.GOAL, "31'", "10", "Tiago Azulão", "Golo")
        val alerts = MatchDiff.diff(before, match(home = 1, events = listOf(goal)), prefs, now)
        assertEquals(1, alerts.size)
        assertEquals(AlertType.GOAL, alerts[0].type)
        assertTrue(alerts[0].title.contains("Petro de Luanda"))
        assertTrue(alerts[0].body.contains("Tiago Azulão"))
        assertTrue(alerts[0].body.contains("Petro 1 - 0 Agosto"))
    }

    @Test
    fun golosDesligadosNasPreferencias() {
        val alerts = MatchDiff.diff(snap(match()), match(away = 1), prefs.copy(goals = false), now)
        assertTrue(alerts.none { it.type == AlertType.GOAL })
    }

    @Test
    fun inicioVermelhoIntervaloEFim() {
        val pre = snap(match(state = MatchState.PRE, home = null, away = null, detail = ""))
        assertEquals(listOf(AlertType.KICKOFF), MatchDiff.diff(pre, match(detail = "1'"), prefs, now).map { it.type })

        val red = MatchDiff.diff(snap(match()), match(homeRed = 1), prefs, now)
        assertEquals(listOf(AlertType.RED_CARD), red.map { it.type })

        val ht = MatchDiff.diff(snap(match()), match(detail = "Intervalo"), prefs.copy(halftime = true), now)
        assertEquals(listOf(AlertType.HALFTIME), ht.map { it.type })

        val ft = MatchDiff.diff(snap(match(home = 1)), match(state = MatchState.POST, home = 1, detail = "Final"), prefs, now)
        assertEquals(listOf(AlertType.FULL_TIME), ft.map { it.type })
    }

    @Test
    fun lembreteSoUmaVezEDentroDaJanela() {
        val soon = match(state = MatchState.PRE, home = null, away = null, detail = "", start = now + 20 * 60_000)
        assertEquals(listOf(AlertType.REMINDER), MatchDiff.diff(null, soon, prefs, now).map { it.type })

        val sent = MatchDiff.snapshot(soon, reminderSent = true, now = now)
        assertTrue(MatchDiff.diff(sent, soon, prefs, now).isEmpty())

        val late = soon.copy(startEpochMillis = now + 3 * 60 * 60_000)
        assertTrue(MatchDiff.diff(null, late, prefs, now).isEmpty())
    }

    @Test
    fun horasDeSilencioAtravessamAMeiaNoite() {
        val q = AlertPrefs(quietHours = true, quietStart = 23, quietEnd = 7)
        assertTrue(q.isQuiet(23))
        assertTrue(q.isQuiet(3))
        assertFalse(q.isQuiet(7))
        assertFalse(q.isQuiet(15))
        assertFalse(AlertPrefs(quietHours = false).isQuiet(3))
    }
}
