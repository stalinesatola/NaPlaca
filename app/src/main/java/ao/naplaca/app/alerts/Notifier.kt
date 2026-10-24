package ao.naplaca.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ao.naplaca.app.MainActivity
import ao.naplaca.app.R

class Notifier(private val context: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LIVE, context.getString(R.string.channel_live_name), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_live_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, context.getString(R.string.channel_reminder_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminder_desc) },
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(alert: Alert, leagueSlug: String) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_MATCH_ID, alert.matchId)
            putExtra(MainActivity.EXTRA_LEAGUE, leagueSlug)
        }
        val pending = PendingIntent.getActivity(
            context, alert.matchId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = if (alert.type == AlertType.REMINDER) CHANNEL_REMINDER else CHANNEL_LIVE
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (channel == CHANNEL_LIVE) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(alert.matchId)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify((alert.matchId + alert.type + alert.body).hashCode(), notification)
        } catch (_: SecurityException) {
            // Permissão retirada entre a verificação e o envio.
        }
    }

    companion object {
        const val CHANNEL_LIVE = "live"
        const val CHANNEL_REMINDER = "reminder"
    }
}
