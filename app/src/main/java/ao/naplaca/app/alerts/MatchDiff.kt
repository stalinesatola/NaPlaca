package ao.naplaca.app.alerts

import ao.naplaca.app.data.local.AlertPrefs
import ao.naplaca.app.data.local.MatchSnapshotEntity
import ao.naplaca.app.domain.EventKind
import ao.naplaca.app.domain.Match
import ao.naplaca.app.domain.MatchState

enum class AlertType { GOAL, RED_CARD, KICKOFF, HALFTIME, FULL_TIME, REMINDER }

data class Alert(val type: AlertType, val matchId: String, val title: String, val body: String)

/**
 * Compara o último estado guardado de um jogo com o estado atual e decide que alertas disparar.
 * Função pura — testada em MatchDiffTest.
 */
object MatchDiff {

    fun snapshot(match: Match, reminderSent: Boolean, now: Long) = MatchSnapshotEntity(
        matchId = match.id,
        homeScore = match.home.score,
        awayScore = match.away.score,
        state = match.state.name,
        statusDetail = match.statusDetail,
        homeRed = match.home.redCards,
        awayRed = match.away.redCards,
        reminderSent = reminderSent,
        updatedAt = now,
    )

    fun diff(previous: MatchSnapshotEntity?, current: Match, prefs: AlertPrefs, now: Long): List<Alert> {
        val alerts = mutableListOf<Alert>()
        val score = "${current.home.shortName} ${current.home.score ?: 0} - ${current.away.score ?: 0} ${current.away.shortName}"
        val title = "${current.home.name} x ${current.away.name}"

        // Lembrete antes do jogo.
        if (current.state == MatchState.PRE && prefs.reminder && previous?.reminderSent != true) {
            val minutesToStart = (current.startEpochMillis - now) / 60_000
            if (minutesToStart in 0..prefs.reminderMinutes.toLong()) {
                alerts += Alert(AlertType.REMINDER, current.id, title, "Começa dentro de $minutesToStart min · ${current.leagueName}")
            }
        }

        // Sem estado anterior: primeira observação. Só avisamos o início se o jogo já está a decorrer
        // há pouco tempo; não inventamos golos que aconteceram antes de começarmos a seguir.
        if (previous == null) return alerts

        val prevState = runCatching { MatchState.valueOf(previous.state) }.getOrDefault(MatchState.PRE)

        if (prefs.kickoff && prevState == MatchState.PRE && current.state == MatchState.LIVE) {
            alerts += Alert(AlertType.KICKOFF, current.id, title, "Começou! · ${current.leagueName}")
        }

        if (prefs.goals) {
            val ph = previous.homeScore ?: 0
            val pa = previous.awayScore ?: 0
            val ch = current.home.score ?: 0
            val ca = current.away.score ?: 0
            if (ch > ph || ca > pa) {
                val scorer = if (ch > ph) current.home else current.away
                val goal = current.events.lastOrNull {
                    it.teamId == scorer.id && it.kind in setOf(EventKind.GOAL, EventKind.PENALTY_GOAL, EventKind.OWN_GOAL)
                }
                val who = goal?.player?.let { " · $it ${goal.minute}" }.orEmpty()
                alerts += Alert(AlertType.GOAL, current.id, "⚽ GOLO do ${scorer.name}!", "$score$who")
            } else if (ch < ph || ca < pa) {
                alerts += Alert(AlertType.GOAL, current.id, "Golo anulado", score)
            }
        }

        if (prefs.redCards) {
            if (current.home.redCards > previous.homeRed) {
                alerts += Alert(AlertType.RED_CARD, current.id, "🟥 Vermelho para ${current.home.name}", score)
            }
            if (current.away.redCards > previous.awayRed) {
                alerts += Alert(AlertType.RED_CARD, current.id, "🟥 Vermelho para ${current.away.name}", score)
            }
        }

        if (prefs.halftime && current.statusDetail == "Intervalo" && previous.statusDetail != "Intervalo") {
            alerts += Alert(AlertType.HALFTIME, current.id, "Intervalo", score)
        }

        if (prefs.fullTime && prevState != MatchState.POST && current.state == MatchState.POST) {
            alerts += Alert(AlertType.FULL_TIME, current.id, "Fim de jogo", score)
        }

        return alerts
    }
}
