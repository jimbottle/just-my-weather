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
     *  - **Age.** The screen labels the hero "Observed 9:40 AM" — a time, with
     *     no date. Yesterday evening's 68° would therefore read as this
     *     morning's, which is worse than showing nothing at all. Anything older
     *     than [MAX_AGE] is dropped and the user gets the placeholder.
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
        /** Three hours: long enough to cover "I checked before lunch and again
         * after", short enough that the reading is still plausibly about now. */
        val MAX_AGE: Duration = Duration.ofHours(3)

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
