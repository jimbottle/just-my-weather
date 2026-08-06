package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * A [SnapshotCache] that survives process death, persisting the entry as one
 * JSON blob (encode/decode in [SnapshotCodec]). Mirrors [DataStorePointCache]
 * deliberately — same shape, same reasons — with an in-memory memo so a read
 * after a write doesn't wait on the store.
 *
 * Reads are the launch path, so they must not block on the write: [put] is
 * called from a background load, and the memo means a [get] racing it sees
 * either the old entry or the new one, never a half-written file.
 */
class DataStoreSnapshotCache(
    private val dataStore: DataStore<Preferences>,
) : SnapshotCache {
    private var memo: CachedSnapshot? = null

    override suspend fun get(): CachedSnapshot? =
        memo ?: SnapshotCodec.decode(dataStore.data.first()[KEY])?.also { memo = it }

    override suspend fun put(entry: CachedSnapshot) {
        memo = entry
        dataStore.edit { prefs -> prefs[KEY] = SnapshotCodec.encode(entry) }
    }

    private companion object {
        val KEY = stringPreferencesKey("last_snapshot")
    }
}
