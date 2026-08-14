package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.data.nws.PointsLookup
import kotlinx.coroutines.flow.first

/**
 * A [PointCache] that survives process death by persisting the whole map as one
 * JSON blob (encode/decode in [PointCacheCodec]). An in-memory memo mirrors the
 * store so a load doesn't re-read/decode DataStore each time, and a freshly
 * resolved point is available immediately even before the async write lands.
 * Access is serialised by [WeatherRepository]'s mutex, so the read-modify-write
 * here doesn't race.
 */
class DataStorePointCache(
    private val dataStore: DataStore<Preferences>,
) : PointCache {
    private val memo = mutableMapOf<String, PointsLookup>()
    private var loaded = false

    override suspend fun get(key: String): PointsLookup? {
        seed()
        return memo[key]
    }

    override suspend fun put(key: String, point: PointsLookup) {
        // Seed before writing too. The repository always reads before it
        // writes, so this is belt and braces — but the memo is what gets
        // encoded, and a put on an unseeded instance would persist only its
        // own entry and silently drop everything already stored.
        seed()
        memo[key] = point
        // Bound it. Rounding means someone who stays put keeps one entry, but
        // someone who travels adds one per kilometre of ground covered, and
        // nothing here ever expires. Oldest first: a LinkedHashMap iterates in
        // insertion order, and that order survives the JSON round trip because
        // both encode and decode walk the document in order.
        while (memo.size > MAX_ENTRIES) {
            memo.remove(memo.keys.first())
        }
        dataStore.edit { prefs -> prefs[KEY] = PointCacheCodec.encode(memo) }
    }

    /** Fill the memo from the store once; thereafter the memo is authoritative.
     * Keys an older build wrote at full precision are dropped on the way in:
     * nothing will ever ask for one again, so they would only bloat a blob
     * that is re-encoded on every write. */
    private suspend fun seed() {
        if (loaded) return
        PointCacheCodec.decode(dataStore.data.first()[KEY])
            .filterKeys { PointCacheKey.isCanonical(it) }
            .forEach { (k, v) -> memo.putIfAbsent(k, v) }
        loaded = true
    }

    private companion object {
        val KEY = stringPreferencesKey("point_cache")

        /** Enough for the places a person actually returns to — home, work,
         * a relative's — with room to spare, while keeping the blob small
         * enough that rewriting it on each new point stays cheap. */
        const val MAX_ENTRIES = 32
    }
}
