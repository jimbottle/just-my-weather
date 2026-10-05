package io.raylytics.justmyweather.view

/**
 * How many days the sun module's table shows. The twin of [DailyDays], bounded
 * here so the slider, the config transform and the codec clamp alike.
 *
 * Sun times are computed on the phone, not fetched, so the horizon is free:
 * [MAX] is a fortnight, enough to see the days shortening. The default stays
 * at the two the app always showed — today and tomorrow, which is what
 * "the next sunrise" needs — and past the tile's height the rows scroll
 * (Evan, 2026-10-05).
 */
object SunDays {
    const val MIN = 1
    const val MAX = 14
    const val DEFAULT = 2

    fun clamp(days: Int): Int = days.coerceIn(MIN, MAX)
}
