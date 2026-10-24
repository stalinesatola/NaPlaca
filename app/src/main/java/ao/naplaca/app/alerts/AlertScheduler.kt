package ao.naplaca.app.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AlertScheduler {
    private const val PERIODIC = "live-periodic"
    private const val FAST = "live-fast"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<LiveMatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Verificação imediata (ex.: o utilizador acabou de seguir um jogo). */
    fun kick(context: Context) = enqueueFast(context, delaySeconds = 0)

    fun scheduleFast(context: Context) = enqueueFast(context, delaySeconds = 60)

    private fun enqueueFast(context: Context, delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<LiveMatchWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(FAST, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelFast(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(FAST)
    }
}
