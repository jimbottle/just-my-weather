package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.PointsLookup
import io.raylytics.justmyweather.data.nws.RelativeLocation
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure JSON (de)serialisation for the persisted point cache — a map of
 * "lat,lon" → [ResolvedPoint]. Split from the DataStore store so it tests on
 * the JVM. Corrupt data decodes to an empty map: a bad cache just means we
 * resolve the grid again, never a crash.
 */
object PointCacheCodec {
    /**
     * One flat shape for both kinds of point, so a cache written before
     * [ResolvedPoint.OutsideNws] existed reads unchanged: it has no
     * `outsideNws` field, which defaults to false — an NWS point, which is
     * all it could have held.
     */
    @Serializable
    private data class StoredPoint(
        val outsideNws: Boolean = false,
        val gridId: String? = null,
        val gridX: Int? = null,
        val gridY: Int? = null,
        val forecastZoneId: String? = null,
        val observationStationId: String? = null,
        // Defaulted null so a cache written before the zone was captured still
        // decodes; the next resolve fills it in.
        val timeZone: String? = null,
        val city: String? = null,
        val state: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(points: Map<String, ResolvedPoint>): String =
        json.encodeToString(
            points.mapValues { (_, resolved) ->
                when (resolved) {
                    ResolvedPoint.OutsideNws -> StoredPoint(outsideNws = true)
                    is ResolvedPoint.Nws -> {
                        val p = resolved.point
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
                    }
                }
            },
        )

    fun decode(raw: String?): Map<String, ResolvedPoint> {
        if (raw.isNullOrBlank()) return emptyMap()
        val stored =
            runCatching { json.decodeFromString<Map<String, StoredPoint>>(raw) }.getOrNull() ?: return emptyMap()
        // An NWS entry missing a grid field is dropped, not guessed at: the
        // next load resolves that coordinate again.
        return stored.mapNotNull { (key, s) -> s.toResolved()?.let { key to it } }.toMap(LinkedHashMap())
    }

    private fun StoredPoint.toResolved(): ResolvedPoint? {
        if (outsideNws) return ResolvedPoint.OutsideNws
        return ResolvedPoint.Nws(
            PointsLookup(
                gridId = gridId ?: return null,
                gridX = gridX ?: return null,
                gridY = gridY ?: return null,
                forecastZoneId = forecastZoneId ?: return null,
                observationStationId = observationStationId ?: return null,
                timeZone = timeZone,
                relativeLocation = if (city != null && state != null) RelativeLocation(city, state) else null,
            ),
        )
    }
}
