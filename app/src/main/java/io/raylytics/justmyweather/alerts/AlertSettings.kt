package io.raylytics.justmyweather.alerts

/**
 * Global alerting preferences — how loud/when, separate from the rules
 * themselves. v1 is quiet hours: a nightly window during which alerts still
 * arrive but make no sound or vibration (delivered on a low-importance channel),
 * so a 3am overnight-low alert is waiting for you at 7am rather than waking you.
 * Nothing is suppressed — quiet means hushed, not dropped.
 *
 * Per-rule tone, snooze, and polling cadence are tracked as follow-ups; this
 * stays a small, pure, persistable value.
 */
data class AlertSettings(
    val quietHoursEnabled: Boolean = false,
    /** Local hour [0,24) the quiet window opens. */
    val quietStartHour: Int = 22,
    /** Local hour [0,24) the quiet window closes (exclusive). */
    val quietEndHour: Int = 7,
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
    }
}
