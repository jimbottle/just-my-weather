package io.raylytics.justmyweather.data.places

import io.raylytics.justmyweather.data.PointCacheKey
import io.raylytics.justmyweather.data.WeatherLocation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Which bundled place is this coordinate nearest?" — for the two callers
 * that have a bare coordinate and need to know where it is: a GPS fix
 * outside NWS territory (for a label and a time zone, which MET does not
 * give) and the region resolver (for a country; docs/REGIONS.md).
 *
 * The catalog is loaded on first use and then kept, unlike the picker's,
 * because these callers ask on every refresh. It is only ever loaded when
 * one of them actually asks: a fix or place abroad, or a region decided
 * without a mobile network (a Wi-Fi-only tablet with a location fix pays
 * for it once, a few megabytes for the life of the process). A phone on a
 * mobile network never does — its region comes from the network. Answers are memoised per rounded coordinate (the same key the
 * point cache uses), so a phone that stays put scans once.
 */
class PlaceLookup(private val load: suspend () -> PlaceCatalog) {
    private val mutex = Mutex()
    private var catalog: PlaceCatalog? = null
    private val memo = mutableMapOf<String, Place?>()

    suspend fun nearest(latitude: Double, longitude: Double): Place? =
        mutex.withLock {
            val key = PointCacheKey.of(WeatherLocation(latitude, longitude, label = ""))
            if (key in memo) return@withLock memo[key]
            val places = catalog ?: load().also { catalog = it }
            places.nearest(latitude, longitude).also { memo[key] = it }
        }
}
