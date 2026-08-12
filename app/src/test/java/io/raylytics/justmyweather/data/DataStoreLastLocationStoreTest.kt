package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The durable path, which is the only path that matters here: the caller this
 * store exists for is a background worker running in a process the user never
 * launched, so every read it does is a read after process death. Covering it
 * with [InMemoryLastLocationStore] elsewhere proves nothing about that.
 */
class DataStoreLastLocationStoreTest {
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

    private val home = WeatherLocation(38.2522, -85.7585, label = "Louisville, KY")

    @Test
    fun `a save survives into a fresh instance via the store`() = runTest {
        val store = FakePreferencesDataStore()
        DataStoreLastLocationStore(store).save(home)
        assertEquals(home, DataStoreLastLocationStore(store).load())
    }

    @Test
    fun `an empty store remembers nothing rather than the equator`() = runTest {
        // The failure this guards: absent coordinates defaulting to 0.0 would
        // put the user in the Atlantic and look like a perfectly valid fix.
        assertNull(DataStoreLastLocationStore(FakePreferencesDataStore()).load())
    }

    @Test
    fun `half a coordinate is no coordinate`() = runTest {
        // A latitude with no longitude is not a place. Nothing writes this
        // pair separately today, but reading it as (38.25, 0.0) is the same
        // Atlantic bug arriving by a different door.
        val store = FakePreferencesDataStore()
        store.edit { it[doublePreferencesKey("last_location_lat")] = 38.2522 }
        assertNull(DataStoreLastLocationStore(store).load())
    }

    @Test
    fun `a blank label round-trips as blank, not as a missing entry`() = runTest {
        // A device fix arrives label-less; the repository fills the name in
        // later. Losing the coordinates because the label was empty would
        // defeat the whole store.
        val store = FakePreferencesDataStore()
        val fix = WeatherLocation(38.2522, -85.7585, label = "")
        DataStoreLastLocationStore(store).save(fix)
        assertEquals(fix, DataStoreLastLocationStore(store).load())
    }

    @Test
    fun `a newer fix replaces the last one`() = runTest {
        val store = FakePreferencesDataStore()
        val cache = DataStoreLastLocationStore(store)
        cache.save(home)
        val away = WeatherLocation(40.7128, -74.0060, label = "New York, NY")
        cache.save(away)
        assertEquals(away, DataStoreLastLocationStore(store).load())
    }
}
