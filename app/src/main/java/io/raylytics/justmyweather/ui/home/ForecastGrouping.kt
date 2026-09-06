package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.Detail
import io.raylytics.justmyweather.view.DetailRow
import io.raylytics.justmyweather.view.Details
import io.raylytics.justmyweather.view.degrees
import io.raylytics.justmyweather.view.percent
import java.time.LocalDate
import java.time.ZoneId

/*
 * Pure reshaping of forecast data for display: no I/O, no clock reads, no
 * Compose — so both transforms test on the JVM. The screen only formats what
 * these return.
 */

/** A whole day condensed from NWS's half-day periods: the daytime high and the
 * following night's low under one name. A leading night-only period (opening
 * the app in the evening) keeps its own name with no high. The halves ride
 * along so a tap can open everything they carry — the detail sheet wants the
 * prose, and the prose is per half. */
data class DayForecast(
    val name: String,
    val highF: Double?,
    val lowF: Double?,
    val shortForecast: String?,
    val day: DailyPeriod? = null,
    val night: DailyPeriod? = null,
)

/** Pair each daytime period with the night that follows it. NWS interleaves
 * day/night strictly, so this is a single pass; any unpaired period still
 * yields a day rather than being dropped. */
fun combineDays(periods: List<DailyPeriod>): List<DayForecast> {
    val days = mutableListOf<DayForecast>()
    var i = 0
    while (i < periods.size) {
        val period = periods[i]
        if (period.isDaytime) {
            val night = periods.getOrNull(i + 1)?.takeIf { !it.isDaytime }
            days +=
                DayForecast(
                    name = period.name,
                    highF = period.temperatureF,
                    lowF = night?.temperatureF,
                    shortForecast = period.shortForecast,
                    day = period,
                    night = night,
                )
            i += if (night != null) 2 else 1
        } else {
            days +=
                DayForecast(
                    name = period.name,
                    highF = null,
                    lowF = period.temperatureF,
                    shortForecast = period.shortForecast,
                    night = period,
                )
            i++
        }
    }
    return days
}

/** One calendar day's worth of hourly points, for the date labels that group
 * the hourly strip. The label formatting stays in the screen. */
data class HourDayGroup(
    val date: LocalDate,
    val hours: List<ForecastPoint>,
)

/** Split consecutive hourly points at local-midnight boundaries. [zone] is a
 * parameter (not a systemDefault() read) so the split is deterministic. */
fun groupHoursByDay(points: List<ForecastPoint>, zone: ZoneId): List<HourDayGroup> {
    val groups = mutableListOf<HourDayGroup>()
    var date: LocalDate? = null
    var bucket = mutableListOf<ForecastPoint>()
    points.forEach { point ->
        val pointDate = point.startTime.atZone(zone).toLocalDate()
        if (pointDate != date) {
            date?.let { groups += HourDayGroup(it, bucket) }
            date = pointDate
            bucket = mutableListOf()
        }
        bucket += point
    }
    date?.let { groups += HourDayGroup(it, bucket) }
    return groups
}

/**
 * A combined day's detail: the high and low, the chance of rain for either
 * half, and both halves' prose. Pure, like everything else in this file, so
 * the sheet's content is decided on the JVM.
 */
fun DayForecast.detail(): Detail =
    Detail(
        title = name,
        subtitle = "Forecast",
        rows =
            buildList {
                add(DetailRow("High", highF.degrees()))
                add(DetailRow("Low", lowF.degrees()))
                val chance = listOfNotNull(day?.precipProbabilityPercent, night?.precipProbabilityPercent).maxOrNull()
                add(DetailRow("Chance of precipitation", chance.percent()))
                day?.let { add(DetailRow("Wind", Details.wind(it.windMph, it.windDirection))) }
                add(DetailRow("Conditions", shortForecast ?: "—"))
            },
        body =
            listOfNotNull(
                day?.detailedForecast,
                night?.detailedForecast?.let { "${night.name}: $it" },
            ).joinToString("\n\n").ifBlank { null },
    )
