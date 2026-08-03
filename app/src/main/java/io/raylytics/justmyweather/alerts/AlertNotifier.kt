package io.raylytics.justmyweather.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.raylytics.justmyweather.R
import io.raylytics.justmyweather.data.nws.ActiveAlert

/**
 * Posts a local notification when a personal alert fires. Two channels: a
 * default one that sounds, and a low-importance "quiet" one that doesn't — the
 * worker picks the quiet channel during the user's quiet hours, so an overnight
 * alert is waiting silently rather than waking them. Honest about permissions:
 * if the user hasn't granted POST_NOTIFICATIONS (Android 13+), we simply don't
 * post — the worker still updates firing state, so nothing is lost or
 * double-fired.
 */
class AlertNotifier(
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Personal alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Conditions you asked to be told about" },
        )
        // Its own channel at HIGH so the user can tune official hazards
        // separately from their personal rules in system settings — and so
        // these are never accidentally muted along with them.
        manager.createNotificationChannel(
            NotificationChannel(SAFETY_CHANNEL_ID, "Safety alerts", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(QUIET_CHANNEL_ID, "Personal alerts (quiet)", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "Alerts delivered silently during your quiet hours"
                    enableVibration(false)
                    setSound(null, null)
                },
        )
    }

    /** Post the alert. [silent] routes to the no-sound channel (quiet hours). */
    fun notify(rule: AlertRule, decision: FireDecision, silent: Boolean = false) {
        if (!hasPermission()) return
        val channel = if (silent) QUIET_CHANNEL_ID else CHANNEL_ID
        val notification =
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("${rule.subject.label} alert")
                .setContentText(decision.reason)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()
        // Stable id per rule so a re-fire updates rather than stacks.
        NotificationManagerCompat.from(context).notify(rule.id.hashCode(), notification)
    }

    /**
     * Post an official NWS safety alert.
     *
     * Deliberately NOT routed through the quiet-hours channel. Quiet hours
     * exist so a personal rule ("tell me when it drops below 35") waits until
     * morning; a tornado warning at 3am is the case where being woken is the
     * entire point, and silencing it would turn a comfort feature into a
     * hazard. Personal rules stay hushed; these do not.
     */
    fun notifySafety(alert: ActiveAlert) {
        if (!hasPermission()) return
        val notification =
            NotificationCompat.Builder(context, SAFETY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(alert.event)
                .setContentText(alert.headline)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.headline))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build()
        // Stable id per alert so an update replaces rather than stacks.
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "personal_alerts"
        const val QUIET_CHANNEL_ID = "personal_alerts_quiet"
        const val SAFETY_CHANNEL_ID = "safety_alerts"
    }
}
