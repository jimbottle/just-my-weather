package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.widget.WidgetData
import io.raylytics.justmyweather.widget.WidgetDataCodec
import kotlinx.coroutines.flow.first

/**
 * The one [WidgetData] the widgets draw from, persisted as a JSON blob so a
 * widget can be redrawn after a reboot or a launcher restart without a fetch.
 * Mirrors [DataStoreSnapshotCache]: a thin I/O shell over a pure codec.
 */
class WidgetDataStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun get(): WidgetData? = WidgetDataCodec.decode(dataStore.data.first()[KEY])

    suspend fun put(data: WidgetData) {
        dataStore.edit { prefs -> prefs[KEY] = WidgetDataCodec.encode(data) }
    }

    private companion object {
        val KEY = stringPreferencesKey("widget_data")
    }
}
