package io.raylytics.justmyweather.view

/**
 * How many days the Daily framing shows. The twin of [HourlyHours], bounded
 * here so the slider, the config transform and the codec clamp alike.
 *
 * NWS's daily forecast runs [NWS_REACH] days (fourteen half-day periods);
 * past that the days come from MET Norway, up to [MAX] — the nine days its
 * forecast reaches (Evan, 2026-10-03; it was fourteen from Open-Meteo until
 * the Play build gained ads, which its free tier does not allow). A stored
 * fourteen clamps down to nine on read.
 * The default stays at NWS's reach — what the app has always shown, and no
 * second request unless the user asks for more. One is the least that is
 * still a forecast.
 */
object DailyDays {
    const val MIN = 1
    const val MAX = 9
    const val DEFAULT = 7

    /** How far NWS's own daily forecast reaches; past this, a second source. */
    const val NWS_REACH = 7

    fun clamp(days: Int): Int = days.coerceIn(MIN, MAX)
}
