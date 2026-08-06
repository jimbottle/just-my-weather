package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The durable path end to end — put, then get from a fresh instance, as a cold
 * start does after process death — against a fake in-memory DataStore.
 */
class DataStoreSnapshotCacheTest {
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

    private val entry =
        CachedSnapshot(
            snapshot =
                WeatherSnapshot(
                    locationLabel = "Brooklyn, NY",
                    temperatureF = 68.0,
                    conditions = "Partly Cloudy",
                    windMph = null,
                    precipitationIn = null,
                    pressureInHg = null,
                    observedAt = Instant.parse("2026-06-24T18:00:00Z"),
                ),
            latitude = 40.71,
            longitude = -74.0,
            savedAt = Instant.parse("2026-06-24T18:05:00Z"),
        )

    @Test
    fun `a put survives into a fresh instance via the store`() = runTest {
        val store = FakePreferencesDataStore()
        DataStoreSnapshotCache(store).put(entry)
        assertEquals(entry, DataStoreSnapshotCache(store).get())
    }

    @Test
    fun `an empty store has nothing remembered`() = runTest {
        assertNull(DataStoreSnapshotCache(FakePreferencesDataStore()).get())
    }

    @Test
    fun `the newest reading replaces the last one`() = runTest {
        val store = FakePreferencesDataStore()
        val cache = DataStoreSnapshotCache(store)
        cache.put(entry)
        val newer = entry.copy(savedAt = entry.savedAt.plusSeconds(600))
        cache.put(newer)
        assertEquals(newer, DataStoreSnapshotCache(store).get())
    }
}
