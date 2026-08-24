package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.data.nws.PointsLookup
import io.raylytics.justmyweather.data.nws.RelativeLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Exercises the durable cache end to end — the encode/put then decode/get path
 * a fresh instance takes after process death — against a fake in-memory
 * [DataStore], with no Android.
 */
class DataStorePointCacheTest {
    /** Minimal DataStore<Preferences> backed by a flow; `edit {}` routes through
     * updateData, so it behaves like the real one for our purposes. */
    private class FakePreferencesDataStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val flow = MutableStateFlow(initial)
        override val data: Flow<Preferences> = flow

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(flow.value).also { flow.value = it }
    }

    private val point =
        PointsLookup("OKX", 33, 35, "NYZ072", "KNYC", relativeLocation = RelativeLocation("Brooklyn", "NY"))

    @Test
    fun `a put survives into a fresh instance via the store`() = runTest {
        val store = FakePreferencesDataStore()
        DataStorePointCache(store).put("40.71,-74.01", point)
        // A new instance (a new process) must seed its memo from the store and
        // decode the persisted point — not rely on in-process state.
        val reread = DataStorePointCache(store).get("40.71,-74.01")
        assertEquals(point, reread)
    }

    @Test
    fun `get returns null for an unknown key`() = runTest {
        assertNull(DataStorePointCache(FakePreferencesDataStore()).get("0.00,0.00"))
    }

    @Test
    fun `keys written by an older build are dropped on the way in`() = runTest {
        // Full-precision keys can never be read again — nothing asks for one
        // now — so carrying them forward only bloats a blob that is rewritten
        // on every put. One phone had seventeen of them.
        // Written straight into the store, the way an older build left it —
        // not through the current cache, which would never produce such a key.
        val legacy = "38.252252252252255,-85.6806540212635"
        val store =
            FakePreferencesDataStore(
                preferencesOf(
                    stringPreferencesKey("point_cache") to
                        PointCacheCodec.encode(mapOf(legacy to point, "38.25,-85.68" to point)),
                ),
            )

        val fresh = DataStorePointCache(store)
        assertNull(fresh.get(legacy), "legacy key not served")
        assertEquals(point, fresh.get("38.25,-85.68"), "canonical key survives")
        // And the legacy entry is gone from the store after the next write,
        // rather than lingering forever.
        fresh.put("38.26,-85.69", point)
        assertFalse(
            PointCacheCodec.decode(store.data.first()[stringPreferencesKey("point_cache")])
                .keys.any { !PointCacheKey.isCanonical(it) },
            "no legacy keys left in the store",
        )
    }

    @Test
    fun `the cache is bounded, oldest entry first`() = runTest {
        // Rounding means someone who stays put keeps one entry, but a traveller
        // adds one per kilometre and nothing here expires.
        val store = FakePreferencesDataStore()
        val cache = DataStorePointCache(store)
        // 40 distinct real keys, inserted in order.
        for (i in 0 until 40) {
            cache.put(PointCacheKey.of(WeatherLocation(38.0 + i * 0.01, -85.0, label = "")), point)
        }
        val stored = PointCacheCodec.decode(store.data.first()[stringPreferencesKey("point_cache")])
        assertEquals(32, stored.size, "bounded")
        assertNull(stored["38.00,-85.00"], "the oldest went first")
        assertEquals(point, stored["38.39,-85.00"], "the newest is kept")
    }

    @Test
    fun `a write does not discard what is already stored`() = runTest {
        // The memo is what gets encoded, and it is filled from the store on
        // first access. An instance that wrote before it read would therefore
        // persist only its own entry and silently wipe the rest. The
        // repository always reads first so this is not reachable today, but a
        // write that destroys the cache is not a trap to leave armed.
        val store =
            FakePreferencesDataStore(
                preferencesOf(
                    stringPreferencesKey("point_cache") to PointCacheCodec.encode(mapOf("38.25,-85.68" to point)),
                ),
            )
        DataStorePointCache(store).put("38.26,-85.69", point)

        val stored = PointCacheCodec.decode(store.data.first()[stringPreferencesKey("point_cache")])
        assertEquals(2, stored.size, "the earlier entry survived the write")
        assertEquals(point, stored["38.25,-85.68"])
    }
}
