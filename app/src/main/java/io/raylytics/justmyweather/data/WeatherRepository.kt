package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.PointsLookup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

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
 * The last successful reading is *remembered* too ([lastReading]) — not to skip
 * a fetch, which still always happens, but so the first paint of a cold start
 * isn't blank. Only readings for a location the caller actually knows are
 * remembered; see the `remember` parameter on [load].
 */
class WeatherRepository(
    private val nws: NwsClient,
    private val pointCache: PointCache = InMemoryPointCache(),
    private val snapshotCache: SnapshotCache = InMemorySnapshotCache(),
    /** Injected so the freshness rule is testable without waiting three hours. */
    private val clock: () -> Instant = Instant::now,
) {
    // Serialises point resolution so two refreshes fired close together (VM
    // init + the location-permission grant) don't both hit the network for the
    // same coordinate — the second awaits the lock and finds the cached value.
    private val resolveMutex = Mutex()

    /**
     * The current conditions for [location].
     *
     * [remember] controls whether the result becomes the reading a cold start
     * paints ([lastReading]). It defaults to true for the foreground, whose
     * fallback is self-consistent — the home screen shows the default location
     * and next launch looks the entry up under that same default. The
     * background alert poll must pass false when it is guessing: it resolves
     * its own location, and a poll that finds no fix (foreground-only location
     * permission gives background work nothing on Android 10+, and a fix can
     * simply be missing after a reboot) falls back to the default and would
     * otherwise overwrite the user's real entry with a reading from a city they
     * are nowhere near — which the distance gate then rejects, leaving them
     * with the blank first paint this cache exists to prevent.
     */
    suspend fun load(location: WeatherLocation, remember: Boolean = true): WeatherSnapshot {
        val point = resolvePoint(location)
        val obs = nws.getObservation(point.observationStationId)
        // A GPS fix arrives without a name; reuse the city/state the point
        // lookup already carried rather than fetching /points again.
        val label =
            location.label.ifBlank {
                point.relativeLocation?.let { "${it.city}, ${it.state}" } ?: "Current location"
            }
        val snapshot =
            WeatherSnapshot(
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
        // Remember it for the next cold start. A failure to persist must never
        // cost the caller its reading — the worst case is one blank first paint.
        if (remember) {
            runCatching {
                snapshotCache.put(
                    CachedSnapshot(
                        snapshot = snapshot,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        savedAt = clock(),
                    ),
                )
            }
        }
        return snapshot
    }

    /**
     * The last reading we stored, if it is still worth showing for [location] —
     * see [CachedSnapshot.isUsableFor] for what "worth showing" means. Null
     * whenever there is nothing remembered, it is too old, or it was taken
     * somewhere else; callers then have nothing to paint until [load] returns,
     * which is the pre-existing behaviour.
     */
    suspend fun lastReading(location: WeatherLocation): WeatherSnapshot? =
        runCatching { snapshotCache.get() }
            .getOrNull()
            ?.takeIf { it.isUsableFor(location, clock()) }
            ?.snapshot

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
