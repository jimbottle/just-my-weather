package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.PointsLookup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A place to show weather for: a coordinate plus a human label. */
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
) {
    companion object {
        /** Until the user grants location or picks a place, fall back to
         * somewhere real so a fresh install (and the alert worker) is never
         * blank. Shared by the home view and the background worker. */
        val DEFAULT = WeatherLocation(latitude = 40.7128, longitude = -74.0060, label = "New York, NY")
    }
}

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
    private val pointCache: PointCache = InMemoryPointCache(),
) {
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
            relativeHumidityPercent = obs.relativeHumidityPercent,
            windDirectionDegrees = obs.windDirectionDegrees,
        )
    }

    /**
     * The hourly forecast for a location, for forecast-window alerts. Goes
     * through the same cached point resolution as [load], so a poll that needs
     * both current and forecast data resolves the grid only once.
     */
    suspend fun loadForecast(location: WeatherLocation): List<ForecastPoint> {
        val point = resolvePoint(location)
        return nws.getHourlyForecast(point.gridId, point.gridX, point.gridY)
    }

    /**
     * Active NWS alerts for the location's forecast zone, unfiltered.
     *
     * Raw on purpose: which of these count as a safety concern is a product
     * policy that lives in [io.raylytics.justmyweather.alerts.SafetyAlerts],
     * not a property of the feed. Keeping the judgement out of the data seam
     * means the rule can change without touching the layer that fetches.
     *
     * Goes straight to the coordinate rather than through the cached grid
     * lookup: alerts are queried by point precisely so county/polygon warnings
     * (tornado, severe thunderstorm) are not missed, so there is no zone to
     * resolve and one fewer call to make.
     */
    suspend fun loadActiveAlerts(location: WeatherLocation): List<ActiveAlert> =
        nws.getActiveAlertsAt(location.latitude, location.longitude)

    /** The daily (half-day period) forecast for the home screen's Daily mode.
     * Same cached point resolution as everything else. */
    suspend fun loadDailyForecast(location: WeatherLocation): List<DailyPeriod> {
        val point = resolvePoint(location)
        return nws.getDailyForecast(point.gridId, point.gridX, point.gridY)
    }

    private suspend fun resolvePoint(location: WeatherLocation): PointsLookup {
        val key = "${location.latitude},${location.longitude}"
        return resolveMutex.withLock {
            pointCache.get(key)
                ?: nws.resolveLocation(location.latitude, location.longitude)
                    .also { pointCache.put(key, it) }
        }
    }
}
