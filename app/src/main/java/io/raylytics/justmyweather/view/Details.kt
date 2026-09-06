package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.data.nws.Units
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * What a tap on a tile opens: the whole of what is tracked for that data
 * point, as label/value rows and, where NWS wrote one, a paragraph. A tile
 * shows one reading; the sheet shows the observation it came from, or the
 * hour or day it belongs to, in full.
 *
 * Pure — data in, a Detail out — so the content of every sheet is decided
 * and tested on the JVM, and the composable that draws it only walks rows.
 * The glance tiles do not build these themselves because a module's tile
 * only has its own value; the observation the value came from lives with
 * the screen, which is why [Details.ofModule] takes the snapshot.
 */

/** One sheet: a title, a quieter line under it, rows, and an optional
 * paragraph below the rows. */
data class Detail(
    val title: String,
    val subtitle: String? = null,
    val rows: List<DetailRow> = emptyList(),
    val body: String? = null,
)

data class DetailRow(val label: String, val value: String)

object Details {
    /**
     * The tapped module's detail, or null for one that opens nothing at the
     * module level (the forecast: its hours and days open their own).
     *
     * A reading opens the whole OBSERVATION, its own field first: the other
     * readings are the context the number was taken in, and "Temperature 93°"
     * alone is what the tile already said.
     */
    fun ofModule(module: ModuleValue, snapshot: WeatherSnapshot, zone: ZoneId): Detail? =
        when (val content = module.content) {
            is ModuleContent.Reading -> ofObservation(module, snapshot, zone)
            is ModuleContent.Sun -> ofSun(module.label, content.days, content.zone)
            is ModuleContent.Forecast -> null
        }

    private fun ofObservation(module: ModuleValue, snapshot: WeatherSnapshot, zone: ZoneId): Detail {
        val tapped = module.module.field
        val fields = WeatherField.entries.sortedBy { if (it == tapped) 0 else 1 }
        val observed = snapshot.observedAt?.let { "Observed ${it.clock(snapshot.zone ?: zone)}" }
        return Detail(
            title = module.label,
            subtitle = listOfNotNull(observed, snapshot.locationLabel).joinToString(" · "),
            rows =
                buildList {
                    fields.forEach { field ->
                        val value =
                            when (field) {
                                // Wind gets its direction here, which the
                                // tile has no room for.
                                WeatherField.WIND ->
                                    wind(snapshot.windMph, Units.compassPoint(snapshot.windDirectionDegrees))
                                else -> field.format(snapshot) ?: "—"
                            }
                        add(DetailRow(field.defaultLabel, value))
                    }
                    add(DetailRow("Humidity", snapshot.relativeHumidityPercent.percent()))
                },
        )
    }

    private fun ofSun(label: String, days: List<SunDay>, zone: ZoneId): Detail =
        Detail(
            title = label,
            subtitle = "Computed for this place",
            rows =
                days.flatMap { day ->
                    val date = day.date.format(DATE)
                    listOf(
                        DetailRow("Sunrise · $date", day.sunrise.clockOrDash(zone)),
                        DetailRow("Sunset · $date", day.sunset.clockOrDash(zone)),
                        DetailRow("Daylight · $date", daylight(day)),
                    )
                },
        )

    /** Every field an hour carries, in the place's clock. */
    fun ofHour(hour: ForecastPoint, zone: ZoneId): Detail {
        val at = hour.startTime.atZone(zone)
        return Detail(
            title = at.format(HOUR).lowercase(Locale.getDefault()),
            subtitle = "${at.format(LONG_DATE)} · Forecast",
            rows =
                listOf(
                    DetailRow("Temperature", hour.temperatureF.degrees()),
                    DetailRow("Chance of precipitation", hour.precipProbabilityPercent.percent()),
                    DetailRow("Wind", wind(hour.windMph, hour.windDirection)),
                    DetailRow("Humidity", hour.relativeHumidityPercent.percent()),
                    DetailRow("Dew point", hour.dewpointF.degrees()),
                    DetailRow("Conditions", hour.shortForecast ?: "—"),
                ),
        )
    }

    /** Every field a half-day period carries, with NWS's prose beneath. */
    fun ofPeriod(period: DailyPeriod): Detail =
        Detail(
            title = period.name,
            subtitle = if (period.isDaytime) "Forecast · day" else "Forecast · night",
            rows =
                listOf(
                    DetailRow(if (period.isDaytime) "High" else "Low", period.temperatureF.degrees()),
                    DetailRow("Chance of precipitation", period.precipProbabilityPercent.percent()),
                    DetailRow("Wind", wind(period.windMph, period.windDirection)),
                    DetailRow("Conditions", period.shortForecast ?: "—"),
                ),
            body = period.detailedForecast,
        )

    /** "12 mph SW", "Calm", or an em-dash — one wording for observation and
     * forecast alike. */
    fun wind(mph: Double?, direction: String?): String =
        when {
            mph == null -> "—"
            mph < 1.0 -> "Calm"
            direction != null -> "${mph.roundToInt()} mph $direction"
            else -> "${mph.roundToInt()} mph"
        }

    private fun daylight(day: SunDay): String {
        val rise = day.sunrise ?: return "—"
        val set = day.sunset ?: return "—"
        val minutes = Duration.between(rise, set).toMinutes()
        if (minutes < 0) return "—"
        return "${minutes / 60} h ${minutes % 60} min"
    }

    private fun Instant.clock(zone: ZoneId): String = atZone(zone).format(CLOCK)

    private fun Instant?.clockOrDash(zone: ZoneId): String = this?.clock(zone) ?: "—"

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("h a")
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
}

/** "93°", or an em-dash for a value NWS did not send. */
fun Double?.degrees(): String = this?.let { "${it.roundToInt()}°" } ?: "—"

/** "28%", or an em-dash. */
fun Double?.percent(): String = this?.let { "${it.roundToInt()}%" } ?: "—"
