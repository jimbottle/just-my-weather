package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.metno.MetNoClient
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.data.nws.NwsClient
import io.raylytics.justmyweather.data.nws.NwsHttpException
import io.raylytics.justmyweather.data.places.Place
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/** A place to show weather for: a coordinate plus a human label. */
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    /**
     * IANA zone of the place, when the place itself says so — a gazetteer
     * place abroad does. In NWS territory the point lookup supplies the zone
     * and this stays null; for a raw GPS fix it is null and the device's zone
     * applies, which is right, since the device is where the fix is.
     */
    val timeZone: String? = null,
    /** ISO 3166-1 alpha-2 country of the place, when known (a gazetteer
     * place). One input to the app's region; see docs/REGIONS.md. */
    val country: String? = null,
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
 * Two sources, chosen per place. NWS covers the United States and is the
 * richer of the two — station observations and official alerts — so it is
 * always asked first. Where NWS says a point is not its own (a 404 on
 * /points), every load for that place goes to MET Norway instead: its
 * forecast for the current hour stands in for an observation, its hourly
 * and daily for NWS's, and there are no official alerts. Each public load
 * below is one `when` over that verdict, so the routing reads in one place.
 *
 * Resolution (lat/lon → NWS grid + station, or "not NWS") is cached per
 * coordinate because it never changes for a fixed point, while observations
 * are always fetched fresh.
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
    /** Days eight and nine, which NWS does not forecast, and everything for
     * a place outside NWS territory. Null means no second source: the Daily
     * view stops where NWS does, and a place abroad fails to load. */
    private val metNo: MetNoClient? = null,
    /** The bundled place nearest a coordinate (data/places/PlaceLookup).
     * Abroad it stands in for what NWS's point gives at home: a name for a
     * GPS fix and the place's time zone. Null-returning by default, which
     * leaves a fix abroad as "Current location" in the device's zone. */
    private val nearest: suspend (latitude: Double, longitude: Double) -> Place? = { _, _ -> null },
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
        val snapshot =
            when (val resolved = resolve(location)) {
                is ResolvedPoint.Nws -> {
                    val point = resolved.point
                    val obs = nws.getObservation(point.observationStationId)
                    // A GPS fix arrives without a name; reuse the city/state the
                    // point lookup already carried rather than fetching /points
                    // again.
                    val label =
                        location.label.ifBlank {
                            point.relativeLocation?.let { "${it.city}, ${it.state}" } ?: "Current location"
                        }
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
                        feelsLikeF = obs.feelsLikeF,
                        timeZone = point.timeZone,
                    )
                }
                ResolvedPoint.OutsideNws -> {
                    val now = metOrFail().getNow(location.latitude, location.longitude, clock())
                    val near = if (location.label.isBlank()) nearestTo(location) else null
                    WeatherSnapshot(
                        // MET names no place, so a GPS fix borrows the
                        // nearest bundled town — what NWS's relativeLocation
                        // does at home.
                        locationLabel = location.label.ifBlank { near?.label ?: "Current location" },
                        temperatureF = now.temperatureF,
                        conditions = now.conditions,
                        windMph = now.windMph,
                        precipitationIn = now.precipitationIn,
                        pressureInHg = now.pressureInHg,
                        observedAt = now.observedAt,
                        relativeHumidityPercent = now.relativeHumidityPercent,
                        windDirectionDegrees = now.windDirectionDegrees,
                        feelsLikeF = now.feelsLikeF,
                        // MET does not say. A gazetteer place carries its
                        // own; a GPS fix takes its nearest town's, and failing
                        // that the device's zone applies — where the fix is.
                        timeZone = location.timeZone ?: near?.timeZone,
                        fromForecast = true,
                    )
                }
            }
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
    suspend fun loadForecast(location: WeatherLocation): List<ForecastPoint> =
        when (val resolved = resolve(location)) {
            is ResolvedPoint.Nws -> resolved.point.let { nws.getHourlyForecast(it.gridId, it.gridX, it.gridY) }
            ResolvedPoint.OutsideNws -> metOrFail().getHourlyForecast(location.latitude, location.longitude)
        }

    /**
     * Active NWS alerts for the location's forecast zone, unfiltered.
     *
     * Raw on purpose: which of these count as a safety concern is a product
     * policy that lives in [io.raylytics.justmyweather.alerts.SafetyAlerts],
     * not a property of the feed. Keeping the judgement out of the data seam
     * means the rule can change without touching the layer that fetches.
     *
     * Queried by coordinate rather than by the cached grid's zone, so
     * county/polygon warnings (tornado, severe thunderstorm) are not missed —
     * the resolution is consulted only to know whether NWS covers the place
     * at all. Outside its territory there are no official alerts to show (NWS
     * answers a foreign point with a 400), and an empty list says exactly
     * that. The verdict is nearly always cached already: every caller loads
     * the reading first.
     */
    suspend fun loadActiveAlerts(location: WeatherLocation): List<ActiveAlert> =
        when (resolve(location)) {
            is ResolvedPoint.Nws -> nws.getActiveAlertsAt(location.latitude, location.longitude)
            ResolvedPoint.OutsideNws -> emptyList()
        }

    /** The daily (half-day period) forecast for the home screen's Daily mode.
     * Same cached point resolution as everything else. Empty outside NWS
     * territory: MET has no half-day periods, and its whole days come from
     * [loadExtendedDaily] instead. */
    suspend fun loadDailyForecast(location: WeatherLocation): List<DailyPeriod> =
        when (val resolved = resolve(location)) {
            is ResolvedPoint.Nws -> resolved.point.let { nws.getDailyForecast(it.gridId, it.gridX, it.gridY) }
            ResolvedPoint.OutsideNws -> emptyList()
        }

    /** MET Norway's daily forecast for [location], or empty with no extended
     * source configured. The caller decides which of these days NWS already
     * covers. The days are cut in the place's calendar, which the (cached)
     * NWS point knows; the device's zone is the fallback — including when
     * resolving the point fails, so an NWS outage costs the NWS days and not
     * MET's too. */
    suspend fun loadExtendedDaily(location: WeatherLocation): List<ExtendedDay> {
        val client = metNo ?: return emptyList()
        val resolved = runCatching { resolve(location) }.getOrNull()
        val id =
            when (resolved) {
                is ResolvedPoint.Nws -> resolved.point.timeZone
                ResolvedPoint.OutsideNws -> location.timeZone ?: nearestTo(location)?.timeZone
                null -> location.timeZone
            }
        val zone = id?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        return client.getDailyForecast(location.latitude, location.longitude, zone)
    }

    /**
     * The place's timezone if we already know it, WITHOUT going to the network.
     *
     * Cache-only on purpose. The caller is the sun-times computation, which is
     * pure arithmetic that must keep working on a dead network — asking here
     * must never become a fetch, and "not known yet" is a perfectly good answer
     * that falls back to the device's zone until a reading lands.
     */

    suspend fun cachedZone(location: WeatherLocation): String? =
        (runCatching { pointCache.get(PointCacheKey.of(location)) }.getOrNull() as? ResolvedPoint.Nws)
            ?.point?.timeZone
            // A place abroad knows its own zone; no lookup needed.
            ?: location.timeZone

    private suspend fun resolve(location: WeatherLocation): ResolvedPoint {
        // Rounded, not raw: a coarse fix jitters by metres between launches
        // and every point inside a 2.5km grid cell resolves the same anyway.
        // See PointCacheKey.
        val key = PointCacheKey.of(location)
        return resolveMutex.withLock {
            pointCache.get(key)
                ?: askNws(location).also { pointCache.put(key, it) }
        }
    }

    /**
     * NWS's verdict on a coordinate. Only its explicit "no data for this
     * point" counts as abroad — a 404 whose problem type is InvalidPoint,
     * which is what /points returns for London, Toronto and open ocean alike
     * (checked against the live API, 2026-10-06). The verdict is cached
     * forever, so anything less certain — a 500, a timeout, a 404 of some
     * other kind — propagates as a failure and is asked again next time,
     * rather than pinning a US point to the second source for good.
     */
    private suspend fun askNws(location: WeatherLocation): ResolvedPoint =
        try {
            ResolvedPoint.Nws(nws.resolveLocation(location.latitude, location.longitude))
        } catch (e: NwsHttpException) {
            val noSuchPoint = e.status == 404 && !e.path.endsWith("/stations") && "InvalidPoint" in e.body
            if (noSuchPoint) ResolvedPoint.OutsideNws else throw e
        }

    private suspend fun nearestTo(location: WeatherLocation): Place? =
        runCatching { nearest(location.latitude, location.longitude) }.getOrNull()

    private fun metOrFail(): MetNoClient =
        metNo ?: error("No forecast source covers this place")
}
