package ao.naplaca.app.alerts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ao.naplaca.app.NaPlacaApp
import ao.naplaca.app.data.local.AlertLogEntity
import ao.naplaca.app.domain.MatchState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Verifica os jogos seguidos (e os jogos do dia das equipas favoritas) e envia notificações locais.
 * Corre de 15 em 15 minutos e, enquanto há jogos seguidos ao vivo, reagenda-se a cada minuto.
 * Não precisa de servidor nem de Firebase.
 */
class LiveMatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as NaPlacaApp).container
        val now = System.currentTimeMillis()
        val prefs = c.profileStore.current().alerts
        val dao = c.database.favorites()
        val zone = ZoneId.systemDefault()

        // 1. Seguir automaticamente os jogos de hoje/amanhã das equipas favoritas com alertas.
        val favorites = dao.teams().filter { it.alertsEnabled }
        if (favorites.isNotEmpty()) {
            val leagues = favorites.map { it.leagueSlug }.toSet()
            val today = LocalDate.now(zone)
            for (day in listOf(today, today.plusDays(1))) {
                val matches = runCatching { c.football.fetchMatchesForDay(day, leagues) }.getOrDefault(emptyList())
                matches.filter { m -> favorites.any { f -> f.source == m.source.name && m.involves(f.teamId) } }
                    .forEach { c.favorites.follow(it, auto = true) }
            }
        }

        // 2. Limpeza: jogos que começaram há mais de 1 dia.
        dao.deleteFollowedBefore(now - DAY)
        c.database.snapshots().deleteOlderThan(now - 3 * DAY)

        // 3. Atualizar só os jogos relevantes agora (a começar em breve ou a decorrer).
        val window = (prefs.reminderMinutes + 20) * MINUTE
        val active = dao.followed().filter { it.startEpochMillis in (now - 4 * HOUR)..(now + window) }
        var keepFast = false
        if (active.isNotEmpty()) {
            val matches = c.football.refreshMatches(
                active.map { it.matchId to it.leagueSlug },
                active.associate { it.matchId to it.startEpochMillis },
            )
            val quiet = prefs.isQuiet(LocalTime.now(zone).hour)
            for (match in matches) {
                val snapshots = c.database.snapshots()
                val previous = snapshots.get(match.id)
                val alerts = MatchDiff.diff(previous, match, prefs, now)
                for (alert in alerts) {
                    c.database.alertLog().insert(AlertLogEntity(matchId = alert.matchId, title = alert.title, body = alert.body))
                    if (!quiet) c.notifier.show(alert, match.leagueSlug)
                }
                val reminderSent = previous?.reminderSent == true || alerts.any { it.type == AlertType.REMINDER }
                snapshots.upsert(MatchDiff.snapshot(match, reminderSent, now))
                if (match.state == MatchState.LIVE ||
                    (match.state == MatchState.PRE && match.startEpochMillis - now < 20 * MINUTE)
                ) keepFast = true
            }
        }

        c.football.pruneCache()
        // Por último: reagendar (substitui este próprio pedido, que já terminou o trabalho).
        if (keepFast) AlertScheduler.scheduleFast(applicationContext) else AlertScheduler.cancelFast(applicationContext)
        return Result.success()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
