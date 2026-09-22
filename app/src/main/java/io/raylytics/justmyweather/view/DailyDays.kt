package io.raylytics.justmyweather.view

/**
 * How many days the Daily framing shows. The twin of [HourlyHours], bounded
 * here so the slider, the config transform and the codec clamp alike.
 *
 * NWS's daily forecast runs seven days (fourteen half-day periods), so the
 * top of the range is everything there is and the default — what the app
 * has always shown. One is the least that is still a forecast.
 */
object DailyDays {
    const val MIN = 1
    const val MAX = 7
    const val DEFAULT = 7

    fun clamp(days: Int): Int = days.coerceIn(MIN, MAX)
}
