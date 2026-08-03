package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether to hand each fresh reading to Gadgetbridge, which relays it to a
 * paired watch.
 *
 * Off by default, and deliberately so: this sends data to another app on the
 * phone, which a weather app has no business doing until asked. Most installs
 * have no watch, and for them the setting never turns on and no broadcast is
 * ever built.
 */
class GadgetbridgeSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[KEY] ?: false }

    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY] = value }
    }

    private companion object {
        val KEY = booleanPreferencesKey("gadgetbridge_enabled")
    }
}
