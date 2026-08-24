package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.PointsLookup
import io.raylytics.justmyweather.data.nws.RelativeLocation
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for the persisted point cache — a map of
 * "lat,lon" → [PointsLookup]. Split from the DataStore store so it tests on the
 * JVM. Corrupt data decodes to an empty map: a bad cache just means we resolve
 * the grid again, never a crash.
 */
object PointCacheCodec {
    @Serializable
    private data class StoredPoint(
        val gridId: String,
        val gridX: Int,
        val gridY: Int,
        val forecastZoneId: String,
        val observationStationId: String,
        // Defaulted null so a cache written before the zone was captured still
        // decodes; the next resolve fills it in.
        val timeZone: String? = null,
        val city: String? = null,
        val state: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(points: Map<String, PointsLookup>): String =
        json.encodeToString(
            points.mapValues { (_, p) ->
                StoredPoint(
                    gridId = p.gridId,
                    gridX = p.gridX,
                    gridY = p.gridY,
                    forecastZoneId = p.forecastZoneId,
                    observationStationId = p.observationStationId,
                    timeZone = p.timeZone,
                    city = p.relativeLocation?.city,
                    state = p.relativeLocation?.state,
                )
            },
        )

    fun decode(raw: String?): Map<String, PointsLookup> {
        if (raw.isNullOrBlank()) return emptyMap()
        val stored =
            runCatching { json.decodeFromString<Map<String, StoredPoint>>(raw) }.getOrNull() ?: return emptyMap()
        return stored.mapValues { (_, s) ->
            PointsLookup(
                gridId = s.gridId,
                gridX = s.gridX,
                gridY = s.gridY,
                forecastZoneId = s.forecastZoneId,
                observationStationId = s.observationStationId,
                timeZone = s.timeZone,
                relativeLocation =
                    if (s.city != null && s.state != null) RelativeLocation(s.city, s.state) else null,
            )
        }
    }
}
