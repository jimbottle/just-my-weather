package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.ui.home.ObservationAge
import io.raylytics.justmyweather.ui.home.timeFormat
import io.raylytics.justmyweather.view.ReadingHeading
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The widget's footer line, as the glance words it. Pure, so what a
 * carried-over reading says is settled on the JVM.
 *
 * An observation shows its age ("Observed 12:40 PM · 12 min ago", or just
 * the age where there is no room for the clock time): the age is what
 * carries staleness on a surface that cannot say "refreshing".
 *
 * A forecast hour (outside NWS territory) has a time it is FOR rather than
 * an age, and names MET Norway where there is room — its CC BY attribution,
 * as on the glance. It normally rolls over every tick; but a failed tick
 * keeps the previous reading standing, so once the hour is
 * [STALE_FORECAST] behind it shows its age as well — or "Forecast for
 * 8:00 PM" would sit there for days looking current (roborev 5393).
 */
internal fun readingLine(snapshot: WeatherSnapshot, zone: ZoneId, now: Instant, wide: Boolean): String {
    val observedAt = snapshot.observedAt
    val time = observedAt?.atZone(zone)?.format(timeFormat)
    if (snapshot.fromForecast) {
        val stale = observedAt != null && Duration.between(observedAt, now) > STALE_FORECAST
        val age = if (stale) ObservationAge.label(observedAt!!, now) else null
        return if (wide) {
            listOfNotNull(ReadingHeading.of(fromForecast = true, time = time), age, ReadingHeading.FORECAST_SOURCE)
                .joinToString(" · ")
        } else {
            age?.let { "Forecast · $it" } ?: ReadingHeading.of(fromForecast = true, time = time)
        }
    }
    if (observedAt == null || time == null) return ReadingHeading.of(fromForecast = false, time = null)
    val age = ObservationAge.label(observedAt, now)
    return when {
        age == null -> ReadingHeading.of(fromForecast = false, time = time)
        wide -> "${ReadingHeading.of(fromForecast = false, time = time)} · $age"
        else -> "Observed $age"
    }
}

/** A forecast hour is at most an hour old when shown, plus a tick (15 min)
 * before the next one replaces it; past this it was carried by a failure. */
internal val STALE_FORECAST: Duration = Duration.ofMinutes(90)
