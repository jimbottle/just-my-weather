package io.raylytics.justmyweather.alerts

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * When a rule looks. [NOW] tests the current observation — the everyday case.
 * The rest test the hourly forecast over a window *ahead*, so you can be warned
 * before it happens: "overnight low below 35°", "wind above 30 within 12 hours".
 *
 * For a forecast window the comparison direction picks the aggregate that
 * matters: BELOW watches the window's *low* (does it ever dip under?), ABOVE
 * watches its *high* (does it ever climb over?). So "Temperature below 35°
 * overnight" fires when the coldest forecast hour overnight is under 35°.
 *
 * [key] is the stable persistence token — never rename it. [label] is the chip
 * text; [phrase] is the fragment the rule summary and notification read with
 * ("…overnight", "…within 12 hours"); NOW has none since it reads as the bare
 * present tense.
 */
enum class AlertWindow(
    val key: String,
    val label: String,
    val phrase: String,
) {
    NOW("now", "Right now", ""),
    NEXT_6H("next_6h", "Next 6h", "within 6 hours"),
    NEXT_12H("next_12h", "Next 12h", "within 12 hours"),
    NEXT_24H("next_24h", "Next 24h", "within 24 hours"),
    OVERNIGHT("overnight", "Overnight", "overnight"),
    ;

    val isForecast: Boolean get() = this != NOW

    /**
     * Whether a forecast hour at [time] falls in this window, measured from
     * [now] in the user's [zone]. NOW matches nothing on the forecast path
     * (it reads the observation instead).
     */
    fun contains(time: Instant, now: Instant, zone: ZoneId): Boolean =
        when (this) {
            NOW -> false
            NEXT_6H -> withinHours(time, now, 6)
            NEXT_12H -> withinHours(time, now, 12)
            NEXT_24H -> withinHours(time, now, 24)
            // Tonight's local night band (20:00–08:00) within the next 24h, so an
            // evening or daytime check still captures the coming night's low.
            OVERNIGHT -> withinHours(time, now, 24) && isNightHour(time.atZone(zone).hour)
        }

    private fun withinHours(time: Instant, now: Instant, hours: Long): Boolean =
        !time.isBefore(now) && time.isBefore(now.plus(hours, ChronoUnit.HOURS))

    private fun isNightHour(hour: Int): Boolean = hour >= 20 || hour < 8

    companion object {
        fun byKey(key: String): AlertWindow? = entries.firstOrNull { it.key == key }
    }
}
