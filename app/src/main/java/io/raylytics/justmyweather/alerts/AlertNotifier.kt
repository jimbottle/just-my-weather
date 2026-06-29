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

/**
 * Posts a local notification when a personal alert fires. One quiet channel for
 * now; per-rule tone/quiet-hours come later. Honest about permissions: if the
 * user hasn't granted POST_NOTIFICATIONS (Android 13+), we simply don't post —
 * the worker still updates firing state, so nothing is lost or double-fired.
 */
class AlertNotifier(
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(CHANNEL_ID, "Personal alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Conditions you asked to be told about" }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun notify(rule: AlertRule, decision: FireDecision) {
        if (!hasPermission()) return
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("${rule.subject.label} alert")
                .setContentText(decision.reason)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()
        // Stable id per rule so a re-fire updates rather than stacks.
        NotificationManagerCompat.from(context).notify(rule.id.hashCode(), notification)
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "personal_alerts"
    }
}
