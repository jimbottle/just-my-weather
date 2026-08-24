package io.raylytics.justmyweather.data.places

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists the user's saved places in a single DataStore preference (the whole
 * list as one JSON blob). The encode/decode lives in [SavedPlacesCodec] so this
 * stays a thin I/O shell.
 *
 * The alert worker reads this too, through `LocationResolver` — which is the
 * point of persisting it rather than holding it in a ViewModel. A user who has
 * chosen a place has told the app where to watch, and background alerts that
 * ignored that choice would be watching the wrong sky.
 */
class SavedPlacesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val saved: Flow<SavedPlaces> =
        dataStore.data.map { prefs -> SavedPlacesCodec.decode(prefs[KEY]) }

    /** The chosen place, or null to follow the device. The single question
     * `LocationResolver` asks. */
    suspend fun current(): io.raylytics.justmyweather.data.WeatherLocation? = saved.first().current

    /**
     * Atomically transform the stored list — the transform runs inside the
     * DataStore edit, against the value on disk at that moment, so a tap that
     * lands while an earlier one is still being written composes instead of
     * clobbering it. (The same read-then-save race that cost the glance grid a
     * drag; see `ViewConfigRepository.update`.)
     */
    suspend fun update(transform: (SavedPlaces) -> SavedPlaces) {
        dataStore.edit { prefs ->
            prefs[KEY] = SavedPlacesCodec.encode(transform(SavedPlacesCodec.decode(prefs[KEY])))
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("saved_places")
    }
}
