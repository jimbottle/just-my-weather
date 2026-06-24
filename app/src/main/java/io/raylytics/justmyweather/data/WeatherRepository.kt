package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.PointsLookup

/** A place to show weather for: a coordinate plus a human label. */
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

/**
 * The one seam between the weather backend and the rest of the app. The UI and
 * the alert workers both go through here, so swapping NWS for another source —
 * or adding a cache — happens in exactly one file.
 *
 * Resolution (lat/lon → NWS grid + station) is cached per coordinate because it
 * never changes for a fixed point, while observations are always fetched fresh.
 */
class WeatherRepository(
    private val nws: NwsClient,
) {
    private val pointCache = mutableMapOf<String, PointsLookup>()

    suspend fun load(location: WeatherLocation): WeatherSnapshot {
        val point = resolvePoint(location)
        val obs = nws.getObservation(point.observationStationId)
        // A GPS fix arrives without a name; let NWS supply "City, ST".
        val label =
            location.label.ifBlank {
                nws.resolveLocationLabel(location.latitude, location.longitude)
                    ?.let { "${it.city}, ${it.state}" }
                    ?: "Current location"
            }
        return WeatherSnapshot(
            locationLabel = label,
            temperatureF = obs.temperatureF,
            conditions = obs.conditions,
            windMph = obs.windMph,
            precipitationIn = obs.precipitationIn,
            pressureInHg = obs.pressureInHg,
            observedAt = obs.observedAt,
        )
    }

    private suspend fun resolvePoint(location: WeatherLocation): PointsLookup {
        val key = "${location.latitude},${location.longitude}"
        return pointCache.getOrPut(key) {
            nws.resolveLocation(location.latitude, location.longitude)
        }
    }
}
