package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.PointsLookup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    // Serialises point resolution so two refreshes fired close together (VM
    // init + the location-permission grant) don't both hit the network for the
    // same coordinate — the second awaits the lock and finds the cached value.
    private val resolveMutex = Mutex()

    suspend fun load(location: WeatherLocation): WeatherSnapshot {
        val point = resolvePoint(location)
        val obs = nws.getObservation(point.observationStationId)
        // A GPS fix arrives without a name; reuse the city/state the point
        // lookup already carried rather than fetching /points again.
        val label =
            location.label.ifBlank {
                point.relativeLocation?.let { "${it.city}, ${it.state}" } ?: "Current location"
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
        return resolveMutex.withLock {
            pointCache[key]
                ?: nws.resolveLocation(location.latitude, location.longitude)
                    .also { pointCache[key] = it }
        }
    }
}
