package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.PointsLookup

/**
 * Stores resolved [PointsLookup]s by a "lat,lon" key. A point never changes for
 * a fixed coordinate, so caching it skips two NWS calls (/points + /stations) on
 * every load. The seam lets the cache be in-memory (tests) or durable across
 * process death ([DataStorePointCache]); [WeatherRepository] serialises access.
 */
interface PointCache {
    suspend fun get(key: String): PointsLookup?

    suspend fun put(key: String, point: PointsLookup)
}

/** A process-lifetime cache — the default when durability isn't wired in. */
class InMemoryPointCache : PointCache {
    private val points = mutableMapOf<String, PointsLookup>()

    override suspend fun get(key: String): PointsLookup? = points[key]

    override suspend fun put(key: String, point: PointsLookup) {
        points[key] = point
    }
}
