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
        if (!loaded) {
            // Seed the memo from the store once; thereafter the memo is authoritative.
            PointCacheCodec.decode(dataStore.data.first()[KEY]).forEach { (k, v) -> memo.putIfAbsent(k, v) }
            loaded = true
        }
        return memo[key]
    }

    override suspend fun put(key: String, point: PointsLookup) {
        memo[key] = point
        dataStore.edit { prefs -> prefs[KEY] = PointCacheCodec.encode(memo) }
    }

    private companion object {
        val KEY = stringPreferencesKey("point_cache")
    }
}
