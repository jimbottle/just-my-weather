package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.Detail
import io.raylytics.justmyweather.view.DetailRow
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
    /** Set when this day is past NWS's reach and came from MET Norway; the
     * tile marks it quietly and the detail sheet names the source. */
    val extended: ExtendedDay? = null,
    /** True when the place is outside NWS territory and EVERY day is MET's:
     * there is no NWS day for an extended one to differ from, so the tile's
     * marker would mean nothing, and the sheet explains the source instead. */
    val outsideNws: Boolean = false,
) {
    /** Whether the tile marks this day as coming from the second source. */
    val markedExtended: Boolean
        get() = extended != null && !outsideNws

    // One answer per question, whichever source the day came from, so the
    // tile and the detail sheet cannot disagree (an extended day's chance was
    // once in its sheet but missing from its tile).

    /** The day's chance of rain: the wetter half's for an NWS day. */
    val precipChance: Double?
        get() = extended?.precipChancePercent
            ?: listOfNotNull(day?.precipProbabilityPercent, night?.precipProbabilityPercent).maxOrNull()

    val windMph: Double?
        get() = extended?.windMph ?: (day ?: night)?.windMph

    val windDirection: String?
        get() = extended?.windDirection ?: (day ?: night)?.windDirection
}

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

/**
 * The first [dailyDays] days of a combined forecast, where a leading
 * night-only row ("Tonight", with no high) is the rest of TODAY and not a
 * day of its own. NWS's fourteen half-day periods fetched in the evening
 * combine to eight rows — tonight, six full days, a trailing afternoon — so
 * counting the first row as a day would make seven days drop the last
 * afternoon, and "3 days" would reach only two real ones (found in review).
 *
 * Pure, and the one place the trim happens, so both daily styles reach the
 * same last period: the day-and-night style shows [periods] of the same
 * rows.
 */
fun visibleDays(days: List<DayForecast>, dailyDays: Int): List<DayForecast> {
    val leadingNight = days.firstOrNull()?.let { it.day == null && it.night != null } == true
    return days.take(if (leadingNight) dailyDays + 1 else dailyDays)
}

/**
 * [visibleDays], then — when the user asked for more days than NWS forecasts
 * — MET Norway's days after NWS's last date, up to [dailyDays] real days in
 * all. A leading night-only row is still today and does not count. Days NWS
 * covers are never repeated: the extended list is cut at the last NWS
 * period's date in [zone], the place's. If NWS gave no dates to align on,
 * nothing is appended rather than risk a duplicate.
 */
fun forecastDays(
    nwsDays: List<DayForecast>,
    extended: List<ExtendedDay>?,
    dailyDays: Int,
    zone: ZoneId,
    conventions: Conventions,
): List<DayForecast> {
    // Outside NWS territory there are no NWS days at all: MET's are the
    // forecast, from its first whole day.
    if (nwsDays.isEmpty()) {
        return extended.orEmpty().take(dailyDays).map { it.toDayForecast(outsideNws = true, conventions) }
    }
    val shown = visibleDays(nwsDays, dailyDays)
    val leadingNight = shown.firstOrNull()?.let { it.day == null && it.night != null } == true
    val needed = dailyDays - (if (leadingNight) shown.size - 1 else shown.size)
    if (needed <= 0 || extended.isNullOrEmpty()) return shown
    val lastNwsDate =
        nwsDays.lastOrNull()?.let { it.night ?: it.day }?.startTime?.atZone(zone)?.toLocalDate()
            ?: return shown
    return shown +
        extended
            .filter { it.date.isAfter(lastNwsDate) }
            .take(needed)
            .map { it.toDayForecast(outsideNws = false, conventions) }
}

/**
 * The Daily view's days, or null while there is not yet enough to decide.
 *
 * Null while the periods are still coming, and also while they have come
 * back EMPTY with MET's days not yet loaded: an empty period list is what a
 * place outside NWS territory gets, and its days are MET's, a fetch behind.
 * Showing "No forecast" in that gap would flash a false empty state on every
 * load abroad. Once MET's list arrives — empty too, if its fetch failed —
 * the answer is final either way.
 */
fun dailyView(
    periods: List<DailyPeriod>?,
    hours: List<ForecastPoint>?,
    extended: List<ExtendedDay>?,
    dailyDays: Int,
    zone: ZoneId,
    conventions: Conventions,
): List<DayForecast>? {
    if (periods == null || (periods.isEmpty() && extended == null)) return null
    // Abroad, MET's fold keeps only days it covers to the evening, so after
    // the early morning today is gone and Daily would open on tomorrow —
    // where NWS would still say "Today" or "Tonight". The remaining hours
    // stand in for it, as they do for the high of NWS's "Tonight".
    val today = if (periods.isEmpty()) restOfToday(hours, extended, zone) else null
    val days = today?.let { listOf(it) + extended.orEmpty() }
    return forecastDays(combineDays(periods, hours, zone), days ?: extended, dailyDays, zone, conventions)
}

/**
 * Today, folded from the hourly points left on today's date in [zone] —
 * or null when MET's days already include today, or no hour is left.
 * Pure, like everything in this file.
 */
internal fun restOfToday(hours: List<ForecastPoint>?, extended: List<ExtendedDay>?, zone: ZoneId): ExtendedDay? {
    val first = hours?.firstOrNull() ?: return null
    val today = first.startTime.atZone(zone).toLocalDate()
    if (extended?.firstOrNull()?.date?.isAfter(today) != true) return null
    val left = hours.takeWhile { it.startTime.atZone(zone).toLocalDate() == today }
    val temps = left.mapNotNull { it.temperatureF }
    if (temps.isEmpty()) return null
    val windiest = left.filter { it.windMph != null }.maxByOrNull { it.windMph!! }
    return ExtendedDay(
        date = today,
        highF = temps.max(),
        lowF = temps.min(),
        precipChancePercent = left.mapNotNull { it.precipProbabilityPercent }.maxOrNull(),
        // The next hour's words: the rest of the day as it starts.
        conditions = left.firstNotNullOfOrNull { it.shortForecast },
        windMph = windiest?.windMph,
        windDirection = windiest?.windDirection,
        partial = true,
    )
}

/** A MET day is named "Tue 10/6" ("Tue 6/10" where the day comes first): it
 * has no NWS name, and a bare weekday would repeat one already on screen a
 * week earlier. */
private fun ExtendedDay.toDayForecast(outsideNws: Boolean, conventions: Conventions) =
    DayForecast(
        name = if (partial) "Today" else date.format(conventions.dayAndDate),
        highFromHours = partial,
        highF = highF,
        lowF = lowF,
        shortForecast = conditions,
        extended = this,
        outsideNws = outsideNws,
    )

/** The half-day periods that make up [days], in order. */
val List<DayForecast>.periods: List<DailyPeriod>
    get() = flatMap { listOfNotNull(it.day, it.night) }

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
fun DayForecast.detail(conventions: Conventions): Detail {
    extended?.let { ext ->
        return Detail(
            title = name,
            // MET Norway's data is CC BY 4.0; this is its attribution.
            subtitle = if (outsideNws) "Forecast · MET Norway" else "Extended forecast · MET Norway",
            rows =
                listOf(
                    DetailRow(if (ext.partial) "High (rest of today)" else "High", conventions.degrees(ext.highF)),
                    DetailRow(if (ext.partial) "Low (rest of today)" else "Low", conventions.degrees(ext.lowF)),
                    // MET reports an amount for most of the world and a
                    // chance only for some regions; show whichever it gave.
                    if (ext.precipChancePercent != null) {
                        DetailRow("Chance of precipitation", ext.precipChancePercent.percent())
                    } else {
                        DetailRow(
                            "Precipitation",
                            ext.precipIn?.let(conventions::precipitation) ?: "—",
                        )
                    },
                    DetailRow("Wind", conventions.wind(ext.windMph, ext.windDirection)),
                    DetailRow("Conditions", ext.conditions ?: "—"),
                ),
            body =
                if (outsideNws) {
                    "This place is outside the National Weather Service's coverage, so its " +
                        "forecast comes from the Norwegian Meteorological Institute (MET Norway)."
                } else {
                    "Past the seven days the National Weather Service forecasts, " +
                        "this day comes from the Norwegian Meteorological Institute (MET Norway)."
                },
        )
    }
    return Detail(
        title = name,
        subtitle = "Forecast",
        rows =
            buildList {
                add(DetailRow(if (highFromHours) "High (rest of today)" else "High", conventions.degrees(highF)))
                add(DetailRow("Low", conventions.degrees(lowF)))
                val chance = listOfNotNull(day?.precipProbabilityPercent, night?.precipProbabilityPercent).maxOrNull()
                add(DetailRow("Chance of precipitation", chance.percent()))
                day?.let { add(DetailRow("Wind", conventions.wind(it.windMph, it.windDirection))) }
                add(DetailRow("Conditions", shortForecast ?: "—"))
            },
        body =
            listOfNotNull(
                day?.detailedForecast,
                night?.detailedForecast?.let { "${night.name}: $it" },
            ).joinToString("\n\n").ifBlank { null },
    )
}
