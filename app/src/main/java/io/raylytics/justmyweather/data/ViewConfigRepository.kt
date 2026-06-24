package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewConfigCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the user's [ViewConfig] in a single DataStore preference (the whole
 * config as one JSON blob). The encode/decode lives in [ViewConfigCodec] so
 * this class stays a thin I/O shell. Exposes the config as a [Flow] so the home
 * view recomposes the moment the user changes it on the customize screen.
 */
class ViewConfigRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val config: Flow<ViewConfig> =
        dataStore.data.map { prefs -> ViewConfigCodec.decode(prefs[KEY]) }

    suspend fun save(config: ViewConfig) {
        dataStore.edit { prefs -> prefs[KEY] = ViewConfigCodec.encode(config) }
    }

    private companion object {
        val KEY = stringPreferencesKey("view_config")
    }
}
