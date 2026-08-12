package io.raylytics.justmyweather.data

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * The last reading we successfully loaded, remembered so a cold start has
 * something real to show while the live fetch is in flight — the alternative is
 * the bare "…" placeholder, which is the whole reason this exists.
 *
 * The coordinates and [savedAt] are carried alongside the snapshot because a
 * remembered reading is only worth showing if it is still *about* the user's
 * situation: same place, recent enough. [isUsableFor] is that judgement, and it
 * is pure so it tests on the JVM.
 */
data class CachedSnapshot(
    val snapshot: WeatherSnapshot,
    val latitude: Double,
    val longitude: Double,
    /** When we stored it — i.e. when the fetch succeeded, not when the station
     * took the reading (that is [WeatherSnapshot.observedAt], which can already
     * be an hour behind and is null for stations that omit it). */
    val savedAt: Instant,
) {
    /**
     * Is this worth painting for [location] at [now]?
     *
     * Two independent gates, both about honesty rather than performance:
     *
     *  - **Age.** Past [MAX_AGE] the reading resembles nothing current and the
     *     user gets the placeholder instead.
     *  - **Place.** A remembered reading describes where it was taken. A device
     *     fix drifts by metres between launches, so exact coordinate equality
     *     would reject nearly every genuine hit; [MAX_DEGREES_AWAY] is instead
     *     sized to the thing the reading is drawn from — one NWS grid cell is
     *     ~2.5 km, so a fix this close resolves to the same station.
     *
     * A negative age (the device clock moved backwards since the write) is
     * rejected too: we cannot tell how stale the entry really is.
     */
    fun isUsableFor(location: WeatherLocation, now: Instant): Boolean {
        val age = Duration.between(savedAt, now)
        if (age.isNegative || age > MAX_AGE) return false
        return abs(location.latitude - latitude) <= MAX_DEGREES_AWAY &&
            abs(location.longitude - longitude) <= MAX_DEGREES_AWAY
    }

    companion object {
        /**
         * A day, so the overnight gap is covered.
         *
         * This was three hours, chosen to cover "I checked before lunch and
         * again after" — which turned out to be the wrong pattern to design
         * for. The commonest opening of a weather app is the first check of
         * the day, and measured on a real device that gap was twelve hours
         * (21:49 to 09:50), so the cap rejected a perfectly good reading every
         * single morning and the feature never fired when it was most wanted.
         *
         * A tight cap was justified while the glance showed a bare clock time
         * with no date: last night's 68° would have read as this morning's.
         * That reason is gone — the age now ships beside the timestamp
         * ("Observed 8:12 PM · 14 hr ago", and "1 day ago" past a day), the
         * reading is marked refreshing, and the live fetch replaces it within
         * about a second. Staleness is stated rather than hidden, so the cap
         * only has to exclude readings so old they resemble nothing at all.
         */
        val MAX_AGE: Duration = Duration.ofHours(24)

        /** ~5.5 km of latitude; less of longitude away from the equator. */
        const val MAX_DEGREES_AWAY: Double = 0.05
    }
}

/**
 * Stores the single most recent reading. One entry, not a map: the app shows one
 * place at a time, and a remembered reading is only ever used to fill the first
 * paint. The seam lets it be in-memory (tests, and the default) or durable
 * across process death ([DataStoreSnapshotCache]).
 */
interface SnapshotCache {
    suspend fun get(): CachedSnapshot?

    suspend fun put(entry: CachedSnapshot)
}

/** A process-lifetime cache — the default when durability isn't wired in. */
class InMemorySnapshotCache : SnapshotCache {
    private var entry: CachedSnapshot? = null

    override suspend fun get(): CachedSnapshot? = entry

    override suspend fun put(entry: CachedSnapshot) {
        this.entry = entry
    }
}
