package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.DateOrder
import io.raylytics.justmyweather.view.PrecipitationUnit
import io.raylytics.justmyweather.view.PressureUnit
import io.raylytics.justmyweather.view.TemperatureUnit
import io.raylytics.justmyweather.view.UnitPrefs
import io.raylytics.justmyweather.view.WindUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * Pure JSON (de)serialisation for [WidgetData], in the style of
 * [io.raylytics.justmyweather.data.SnapshotCodec]: private stored shapes,
 * instants as epoch millis, dates as ISO strings, every optional field
 * defaulted so a blob from an older build still decodes. Corrupt data
 * decodes to null — a widget then shows its placeholder until the next
 * tick, never a crash in the launcher's process.
 *
 * It has its own stored snapshot rather than reusing the cache's because the
 * cache's shape predates the place's zone, and the widget needs the zone to
 * put an honest clock time on "Observed".
 */
object WidgetDataCodec {
    @Serializable
    private data class StoredSnapshot(
        val locationLabel: String,
        val temperatureF: Double? = null,
        val conditions: String? = null,
        val windMph: Double? = null,
        val precipitationIn: Double? = null,
        val pressureInHg: Double? = null,
        val observedAtEpochMillis: Long? = null,
        val relativeHumidityPercent: Double? = null,
        val windDirectionDegrees: Double? = null,
        val feelsLikeF: Double? = null,
        val timeZone: String? = null,
        val fromForecast: Boolean = false,
    )

    @Serializable
    private data class StoredHour(
        val startEpochMillis: Long,
        val temperatureF: Double? = null,
        val windMph: Double? = null,
        val precipProbabilityPercent: Double? = null,
        val shortForecast: String? = null,
        val windDirection: String? = null,
        val relativeHumidityPercent: Double? = null,
        val dewpointF: Double? = null,
    )

    @Serializable
    private data class StoredPeriod(
        val name: String,
        val startEpochMillis: Long? = null,
        val isDaytime: Boolean,
        val temperatureF: Double? = null,
        val shortForecast: String? = null,
        val precipProbabilityPercent: Double? = null,
        val windMph: Double? = null,
        val windDirection: String? = null,
        val detailedForecast: String? = null,
    )

    @Serializable
    private data class StoredExtendedDay(
        val date: String,
        val highF: Double? = null,
        val lowF: Double? = null,
        val precipChancePercent: Double? = null,
        val conditions: String? = null,
        val windMph: Double? = null,
        val windDirection: String? = null,
        val precipIn: Double? = null,
    )

    @Serializable
    private data class StoredData(
        val latitude: Double,
        val longitude: Double,
        val label: String,
        val snapshot: StoredSnapshot? = null,
        val hours: List<StoredHour>? = null,
        val periods: List<StoredPeriod>? = null,
        val extended: List<StoredExtendedDay>? = null,
        val fetchedAtEpochMillis: Long,
        val error: String? = null,
        // Each list's own fetch time; null means the list was never fetched
        // (or predates the clock, and then it is treated as stale).
        val hoursFetchedAtEpochMillis: Long? = null,
        val periodsFetchedAtEpochMillis: Long? = null,
        val extendedFetchedAtEpochMillis: Long? = null,
        // Absent in data written before regions: the US conventions, which
        // is what such a widget was already showing.
        val conventions: StoredConventions? = null,
    )

    /** Units by their stable keys, never enum names (view/Conventions). */
    @Serializable
    private data class StoredConventions(
        val temperature: String,
        val wind: String,
        val pressure: String,
        val precipitation: String,
        val dayFirst: Boolean,
        val clock24: Boolean,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(data: WidgetData): String =
        json.encodeToString(
            StoredData(
                latitude = data.location.latitude,
                longitude = data.location.longitude,
                label = data.location.label,
                snapshot = data.snapshot?.stored(),
                hours = data.hours?.items?.map { it.stored() },
                periods = data.periods?.items?.map { it.stored() },
                extended = data.extended?.items?.map { it.stored() },
                fetchedAtEpochMillis = data.fetchedAt.toEpochMilli(),
                error = data.error,
                hoursFetchedAtEpochMillis = data.hours?.at?.toEpochMilli(),
                periodsFetchedAtEpochMillis = data.periods?.at?.toEpochMilli(),
                extendedFetchedAtEpochMillis = data.extended?.at?.toEpochMilli(),
                conventions =
                    data.conventions.let { c ->
                        StoredConventions(
                            temperature = c.units.temperature.key,
                            wind = c.units.wind.key,
                            pressure = c.units.pressure.key,
                            precipitation = c.units.precipitation.key,
                            dayFirst = c.dateOrder == DateOrder.DAY_FIRST,
                            clock24 = c.clock24,
                        )
                    },
            ),
        )

    fun decode(raw: String?): WidgetData? {
        if (raw.isNullOrBlank()) return null
        val s = runCatching { json.decodeFromString<StoredData>(raw) }.getOrNull() ?: return null
        // A date this JVM cannot parse drops its day rather than the whole
        // blob: the rest of the data is still good.
        return WidgetData(
            location = WeatherLocation(s.latitude, s.longitude, s.label),
            snapshot = s.snapshot?.restored(),
            hours = fetched(s.hours?.map { it.restored() }, s.hoursFetchedAtEpochMillis),
            periods = fetched(s.periods?.map { it.restored() }, s.periodsFetchedAtEpochMillis),
            extended = fetched(s.extended?.mapNotNull { it.restored() }, s.extendedFetchedAtEpochMillis),
            fetchedAt = Instant.ofEpochMilli(s.fetchedAtEpochMillis),
            error = s.error,
            conventions = s.conventions?.restored() ?: Conventions.US,
        )
    }

    /** A unit key this build doesn't know falls back to the US unit for that
     * dimension rather than failing the whole widget. */
    private fun StoredConventions.restored(): Conventions {
        val us = Conventions.US.units
        return Conventions(
            units =
                UnitPrefs(
                    temperature = TemperatureUnit.entries.firstOrNull { it.key == temperature } ?: us.temperature,
                    wind = WindUnit.entries.firstOrNull { it.key == wind } ?: us.wind,
                    pressure = PressureUnit.entries.firstOrNull { it.key == pressure } ?: us.pressure,
                    precipitation =
                        PrecipitationUnit.entries.firstOrNull { it.key == precipitation } ?: us.precipitation,
                ),
            dateOrder = if (dayFirst) DateOrder.DAY_FIRST else DateOrder.MONTH_FIRST,
            clock24 = clock24,
        )
    }

    /** A stored list with its time, or null for an absent list. A list with
     * no time (written before lists had one) is dated to the epoch: kept,
     * but stale to any freshness check. */
    private fun <T> fetched(items: List<T>?, atEpochMillis: Long?): Fetched<T>? =
        items?.let { Fetched(it, Instant.ofEpochMilli(atEpochMillis ?: 0L)) }

    private fun WeatherSnapshot.stored() =
        StoredSnapshot(
            locationLabel = locationLabel,
            temperatureF = temperatureF,
            conditions = conditions,
            windMph = windMph,
            precipitationIn = precipitationIn,
            pressureInHg = pressureInHg,
            observedAtEpochMillis = observedAt?.toEpochMilli(),
            relativeHumidityPercent = relativeHumidityPercent,
            windDirectionDegrees = windDirectionDegrees,
            feelsLikeF = feelsLikeF,
            timeZone = timeZone,
            fromForecast = fromForecast,
        )

    private fun StoredSnapshot.restored() =
        WeatherSnapshot(
            locationLabel = locationLabel,
            temperatureF = temperatureF,
            conditions = conditions,
            windMph = windMph,
            precipitationIn = precipitationIn,
            pressureInHg = pressureInHg,
            observedAt = observedAtEpochMillis?.let(Instant::ofEpochMilli),
            relativeHumidityPercent = relativeHumidityPercent,
            windDirectionDegrees = windDirectionDegrees,
            feelsLikeF = feelsLikeF,
            timeZone = timeZone,
            fromForecast = fromForecast,
        )

    private fun ForecastPoint.stored() =
        StoredHour(
            startEpochMillis = startTime.toEpochMilli(),
            temperatureF = temperatureF,
            windMph = windMph,
            precipProbabilityPercent = precipProbabilityPercent,
            shortForecast = shortForecast,
            windDirection = windDirection,
            relativeHumidityPercent = relativeHumidityPercent,
            dewpointF = dewpointF,
        )

    private fun StoredHour.restored() =
        ForecastPoint(
            startTime = Instant.ofEpochMilli(startEpochMillis),
            temperatureF = temperatureF,
            windMph = windMph,
            precipProbabilityPercent = precipProbabilityPercent,
            shortForecast = shortForecast,
            windDirection = windDirection,
            relativeHumidityPercent = relativeHumidityPercent,
            dewpointF = dewpointF,
        )

    private fun DailyPeriod.stored() =
        StoredPeriod(
            name = name,
            startEpochMillis = startTime?.toEpochMilli(),
            isDaytime = isDaytime,
            temperatureF = temperatureF,
            shortForecast = shortForecast,
            precipProbabilityPercent = precipProbabilityPercent,
            windMph = windMph,
            windDirection = windDirection,
            detailedForecast = detailedForecast,
        )

    private fun StoredPeriod.restored() =
        DailyPeriod(
            name = name,
            startTime = startEpochMillis?.let(Instant::ofEpochMilli),
            isDaytime = isDaytime,
            temperatureF = temperatureF,
            shortForecast = shortForecast,
            precipProbabilityPercent = precipProbabilityPercent,
            windMph = windMph,
            windDirection = windDirection,
            detailedForecast = detailedForecast,
        )

    private fun ExtendedDay.stored() =
        StoredExtendedDay(
            date = date.toString(),
            highF = highF,
            lowF = lowF,
            precipChancePercent = precipChancePercent,
            conditions = conditions,
            windMph = windMph,
            windDirection = windDirection,
            precipIn = precipIn,
        )

    private fun StoredExtendedDay.restored(): ExtendedDay? {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        return ExtendedDay(
            date = day,
            highF = highF,
            lowF = lowF,
            precipChancePercent = precipChancePercent,
            conditions = conditions,
            windMph = windMph,
            windDirection = windDirection,
            precipIn = precipIn,
        )
    }
}
