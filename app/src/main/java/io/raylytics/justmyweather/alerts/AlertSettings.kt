package io.raylytics.justmyweather.alerts

/**
 * Global alerting preferences — how loud/when, separate from the rules
 * themselves. Quiet hours: a nightly window during which alerts still arrive but
 * make no sound or vibration (delivered on a low-importance channel), so a 3am
 * overnight-low alert is waiting for you at 7am rather than waking you — quiet
 * means hushed, not dropped. [pollMinutes] is how often the background check
 * runs.
 *
 * A small, pure, persistable value. Per-rule tone and snooze are a follow-up.
 */
data class AlertSettings(
    val quietHoursEnabled: Boolean = false,
    /** Local hour [0,24) the quiet window opens. */
    val quietStartHour: Int = 22,
    /** Local hour [0,24) the quiet window closes (exclusive). */
    val quietEndHour: Int = 7,
    /** How often the background alert check runs, in minutes. Constrained to
     * [POLL_CHOICES]; WorkManager's own floor is 15 minutes. */
    val pollMinutes: Int = 60,
    /**
     * Whether official NWS safety alerts (tornado, severe storm, hurricane,
     * dangerous heat, poor air quality) also post notifications. OFF by
     * default: the banner on the glance is passive, but a notification
     * interrupts, and official-hazard pushing is something to opt into rather
     * than discover.
     */
    val safetyNotifications: Boolean = false,
) {
    /**
     * Whether [hour] (local, 0–23) is inside the quiet window. Handles a window
     * that wraps midnight (22 → 7). Returns false when quiet hours are off.
     */
    fun isQuietAt(hour: Int): Boolean {
        if (!quietHoursEnabled) return false
        return if (quietStartHour <= quietEndHour) {
            hour in quietStartHour until quietEndHour
        } else {
            // Wraps midnight: in the window if at/after the start OR before the end.
            hour >= quietStartHour || hour < quietEndHour
        }
    }

    companion object {
        val DEFAULT = AlertSettings()

        /** The cadences offered in the UI, in minutes (30m, 1h, 3h, 6h). All
         * above WorkManager's 15-minute periodic floor. */
        val POLL_CHOICES = listOf(30, 60, 180, 360)

        /** A short human label for a cadence ("30m", "1h", "3h"). */
        fun pollLabel(minutes: Int): String =
            if (minutes < 60) "${minutes}m" else "${minutes / 60}h"
    }
}
