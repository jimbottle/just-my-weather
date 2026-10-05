package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
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
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(data: WidgetData): String =
        json.encodeToString(
            StoredData(
                latitude = data.location.latitude,
                longitude = data.location.longitude,
                label = data.location.label,
                snapshot = data.snapshot?.stored(),
                hours = data.hours?.map { it.stored() },
                periods = data.periods?.map { it.stored() },
                extended = data.extended?.map { it.stored() },
                fetchedAtEpochMillis = data.fetchedAt.toEpochMilli(),
                error = data.error,
            ),
        )

    fun decode(raw: String?): WidgetData? {
        if (raw.isNullOrBlank()) return null
        val s = runCatching { json.decodeFromString<StoredData>(raw) }.getOrNull() ?: return null
        // A date this JVM cannot parse drops its day rather than the whole
        // blob: the rest of the data is still good.
        val extended = s.extended?.mapNotNull { it.restored() }
        return WidgetData(
            location = WeatherLocation(s.latitude, s.longitude, s.label),
            snapshot = s.snapshot?.restored(),
            hours = s.hours?.map { it.restored() },
            periods = s.periods?.map { it.restored() },
            extended = extended,
            fetchedAt = Instant.ofEpochMilli(s.fetchedAtEpochMillis),
            error = s.error,
        )
    }

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
