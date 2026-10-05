package io.raylytics.justmyweather.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.raylytics.justmyweather.widget.WidgetConfig
import io.raylytics.justmyweather.widget.WidgetConfigCodec
import kotlinx.coroutines.flow.first

/**
 * Persists each home screen widget's [WidgetConfig] in the app's DataStore,
 * one preference per widget keyed by the launcher's widget id. Encode/decode
 * lives in [WidgetConfigCodec], like every other repository here.
 *
 * Keyed by id rather than kept as one list because widgets come and go
 * independently — the launcher tells us about one at a time — and a
 * per-widget key makes "this widget was removed" a single delete. [all] is
 * what the refresh worker reads to know what to fetch and the receiver reads
 * to know whether anything is left.
 */
class WidgetConfigRepository(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun get(widgetId: Int): WidgetConfig? = WidgetConfigCodec.decode(dataStore.data.first()[key(widgetId)])

    suspend fun save(widgetId: Int, config: WidgetConfig) {
        dataStore.edit { prefs -> prefs[key(widgetId)] = WidgetConfigCodec.encode(config) }
    }

    suspend fun delete(widgetIds: Collection<Int>) {
        if (widgetIds.isEmpty()) return
        dataStore.edit { prefs -> widgetIds.forEach { prefs.remove(key(it)) } }
    }

    /** Every widget with a saved config, by id. A corrupt entry is skipped,
     * not fatal: the widget it belongs to re-seeds itself on its next draw. */
    suspend fun all(): Map<Int, WidgetConfig> =
        dataStore.data.first().asMap().entries.mapNotNull { (key, value) ->
            val id = key.name.removePrefix(PREFIX).toIntOrNull()?.takeIf { key.name.startsWith(PREFIX) }
            val config = (value as? String)?.let(WidgetConfigCodec::decode)
            if (id != null && config != null) id to config else null
        }.toMap()

    private fun key(widgetId: Int) = stringPreferencesKey("$PREFIX$widgetId")

    private companion object {
        const val PREFIX = "widget_config_"
    }
}
