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
    /** True when [highF] is not NWS's high but the warmest remaining hour of
     * today — see [combineDays]. The tile shows it like any high; the detail
     * sheet says what it is. */
    val highFromHours: Boolean = false,
)

/**
 * Pair each daytime period with the night that follows it. NWS interleaves
 * day/night strictly, so this is a single pass; any unpaired period still
 * yields a day rather than being dropped.
 *
 * A leading night has no high of its own: after mid-afternoon NWS stops
 * issuing today's daytime period, so the forecast opens on "Tonight" with
 * only a low. Given the hourly forecast, that day borrows the warmest
 * remaining hour on today's date (in the PLACE's calendar, [zone]) as its
 * high. It is an approximation and gets more so as the evening goes on —
 * by ten it is the evening's warmest hour, not the day's — which is why the
 * result is flagged and the detail sheet names it. A trailing day's missing
 * low stays missing: nothing carries it.
 */
fun combineDays(
    periods: List<DailyPeriod>,
    hours: List<ForecastPoint>? = null,
    zone: ZoneId = ZoneId.systemDefault(),
): List<DayForecast> {
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
            val borrowed = if (days.isEmpty()) warmestRemainingHour(hours, zone) else null
            days +=
                DayForecast(
                    name = period.name,
                    highF = borrowed,
                    lowF = period.temperatureF,
                    shortForecast = period.shortForecast,
                    night = period,
                    highFromHours = borrowed != null,
                )
            i++
        }
    }
    return days
}

/** The warmest of the hourly points that fall on the first point's date in
 * [zone] — the rest of today, as the hourly forecast starts at the current
 * hour. Null with no hours, or none carrying a temperature. */
private fun warmestRemainingHour(hours: List<ForecastPoint>?, zone: ZoneId): Double? {
    val first = hours?.firstOrNull() ?: return null
    val today = first.startTime.atZone(zone).toLocalDate()
    return hours
        .takeWhile { it.startTime.atZone(zone).toLocalDate() == today }
        .mapNotNull { it.temperatureF }
        .maxOrNull()
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
                add(DetailRow(if (highFromHours) "High (rest of today)" else "High", highF.degrees()))
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
