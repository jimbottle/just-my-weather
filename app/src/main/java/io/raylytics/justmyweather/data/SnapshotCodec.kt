package io.raylytics.justmyweather.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Pure JSON (de)serialisation for the remembered reading. Split from the
 * DataStore store so it tests on the JVM, exactly like [PointCacheCodec].
 *
 * Corrupt or half-written data decodes to null: a bad entry just means the
 * first paint falls back to the placeholder, never a crash on launch. Instants
 * travel as epoch millis rather than through a custom serialiser — one fewer
 * moving part in a file whose only job is to be boring.
 */
object SnapshotCodec {
    @Serializable
    private data class StoredSnapshot(
        val latitude: Double,
        val longitude: Double,
        val savedAtEpochMillis: Long,
        val locationLabel: String,
        val temperatureF: Double? = null,
        val conditions: String? = null,
        val windMph: Double? = null,
        val precipitationIn: Double? = null,
        val pressureInHg: Double? = null,
        val observedAtEpochMillis: Long? = null,
        val relativeHumidityPercent: Double? = null,
        val windDirectionDegrees: Double? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(entry: CachedSnapshot): String {
        val s = entry.snapshot
        return json.encodeToString(
            StoredSnapshot(
                latitude = entry.latitude,
                longitude = entry.longitude,
                savedAtEpochMillis = entry.savedAt.toEpochMilli(),
                locationLabel = s.locationLabel,
                temperatureF = s.temperatureF,
                conditions = s.conditions,
                windMph = s.windMph,
                precipitationIn = s.precipitationIn,
                pressureInHg = s.pressureInHg,
                observedAtEpochMillis = s.observedAt?.toEpochMilli(),
                relativeHumidityPercent = s.relativeHumidityPercent,
                windDirectionDegrees = s.windDirectionDegrees,
            ),
        )
    }

    fun decode(raw: String?): CachedSnapshot? {
        if (raw.isNullOrBlank()) return null
        val stored = runCatching { json.decodeFromString<StoredSnapshot>(raw) }.getOrNull() ?: return null
        return CachedSnapshot(
            snapshot =
                WeatherSnapshot(
                    locationLabel = stored.locationLabel,
                    temperatureF = stored.temperatureF,
                    conditions = stored.conditions,
                    windMph = stored.windMph,
                    precipitationIn = stored.precipitationIn,
                    pressureInHg = stored.pressureInHg,
                    observedAt = stored.observedAtEpochMillis?.let(Instant::ofEpochMilli),
                    relativeHumidityPercent = stored.relativeHumidityPercent,
                    windDirectionDegrees = stored.windDirectionDegrees,
                ),
            latitude = stored.latitude,
            longitude = stored.longitude,
            savedAt = Instant.ofEpochMilli(stored.savedAtEpochMillis),
        )
    }
}
