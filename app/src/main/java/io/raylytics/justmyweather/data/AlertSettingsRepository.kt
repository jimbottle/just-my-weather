package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.alerts.AlertSettings
import io.raylytics.justmyweather.alerts.AlertSettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists global [AlertSettings] as one JSON blob in the shared DataStore,
 * mirroring the other config repositories. Exposed as a [Flow] for the UI and
 * read once by the worker when deciding how to deliver.
 */
class AlertSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AlertSettings> =
        dataStore.data.map { prefs -> AlertSettingsCodec.decode(prefs[KEY]) }

    suspend fun current(): AlertSettings = AlertSettingsCodec.decode(dataStore.data.first()[KEY])

    suspend fun save(settings: AlertSettings) {
        dataStore.edit { prefs -> prefs[KEY] = AlertSettingsCodec.encode(settings) }
    }

    private companion object {
        val KEY = stringPreferencesKey("alert_settings")
    }
}
