package io.raylytics.justmyweather.view

/**
 * How far ahead the hourly forecast runs, in hours. A user setting, bounded
 * here so every reader agrees on the bounds: the customize slider, the config
 * transform, and the codec all clamp to the same range.
 *
 * Four is the least that still reads as a forecast (one row of tiles). A
 * hundred and sixty-eight is a week, and past what NWS sends (~156 points),
 * so the top of the range means "everything there is". Twenty-four is the
 * span people plan against and what the app has always shown.
 */
object HourlyHours {
    const val MIN = 4
    const val MAX = 168
    const val DEFAULT = 24

    /** The slider moves in fours: one row of tiles at full width, and a
     * range this wide needs coarser stops to be settable by thumb. */
    const val STEP = 4

    fun clamp(hours: Int): Int = hours.coerceIn(MIN, MAX)
}
