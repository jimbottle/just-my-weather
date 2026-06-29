package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ThemeConfigCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists the user's [ThemeConfig] as one JSON blob, alongside the view config
 * in the same DataStore. Mirrors [ViewConfigRepository]: encode/decode lives in
 * [ThemeConfigCodec] so this stays a thin I/O shell, and the config is exposed
 * as a [Flow] so the whole UI re-themes the moment the user changes it.
 */
class ThemeConfigRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val config: Flow<ThemeConfig> =
        dataStore.data.map { prefs -> ThemeConfigCodec.decode(prefs[KEY]) }

    suspend fun save(config: ThemeConfig) {
        dataStore.edit { prefs -> prefs[KEY] = ThemeConfigCodec.encode(config) }
    }

    private companion object {
        val KEY = stringPreferencesKey("theme_config")
    }
}
