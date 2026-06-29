package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.raylytics.justmyweather.data.nws.PointsLookup
import io.raylytics.justmyweather.data.nws.RelativeLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val point = PointsLookup("OKX", 33, 35, "NYZ072", "KNYC", RelativeLocation("Brooklyn", "NY"))

    @Test
    fun `a put survives into a fresh instance via the store`() = runTest {
        val store = FakePreferencesDataStore()
        DataStorePointCache(store).put("40.71,-74.0", point)
        // A new instance (a new process) must seed its memo from the store and
        // decode the persisted point — not rely on in-process state.
        val reread = DataStorePointCache(store).get("40.71,-74.0")
        assertEquals(point, reread)
    }

    @Test
    fun `get returns null for an unknown key`() = runTest {
        assertNull(DataStorePointCache(FakePreferencesDataStore()).get("0.0,0.0"))
    }
}
