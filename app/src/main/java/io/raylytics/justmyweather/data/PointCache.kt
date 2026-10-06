package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.PointsLookup

/**
 * What a coordinate resolved to: an NWS grid point, or a place NWS does not
 * cover. Both are worth remembering, since neither changes for a fixed
 * coordinate — and the second is what routes every load for that place to
 * MET Norway instead (see [WeatherRepository]).
 */
sealed interface ResolvedPoint {
    data class Nws(val point: PointsLookup) : ResolvedPoint

    /** NWS answered "no data for this point" — abroad, or out at sea. */
    data object OutsideNws : ResolvedPoint
}

/**
 * Stores [ResolvedPoint]s by a "lat,lon" key. A point never changes for a
 * fixed coordinate, so caching it skips two NWS calls (/points + /stations) on
 * every load — or, abroad, the one /points call that would only say no again.
 * The seam lets the cache be in-memory (tests) or durable across process death
 * ([DataStorePointCache]); [WeatherRepository] serialises access.
 */
interface PointCache {
    suspend fun get(key: String): ResolvedPoint?

    suspend fun put(key: String, point: ResolvedPoint)
}

/** A process-lifetime cache — the default when durability isn't wired in. */
class InMemoryPointCache : PointCache {
    private val points = mutableMapOf<String, ResolvedPoint>()

    override suspend fun get(key: String): ResolvedPoint? = points[key]

    override suspend fun put(key: String, point: ResolvedPoint) {
        points[key] = point
    }
}
