package io.raylytics.justmyweather.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * What a widget is drawn from, in Glance's own per-widget state.
 *
 * Glance keeps a widget's composition alive for a while after a draw and
 * answers later `update` calls by recomposing it — WITHOUT calling
 * `provideGlance` again. Anything read there is frozen in the closure, which
 * is how a reconfigured widget kept drawing its old module (seen on the
 * emulator: the config was saved as Forecast, the widget stayed Temperature
 * until the session expired). What a composition may read fresh is
 * `currentState()`, so the config and the data are mirrored here, and every
 * writer pushes before it asks for a draw. The app's DataStore stays the
 * durable source; this is the copy the launcher's draw reads.
 *
 * Both travel as the JSON their codecs write, so the state never has a
 * shape of its own to migrate.
 */
object WidgetState {
    val CONFIG: Preferences.Key<String> = stringPreferencesKey("config")
    val DATA: Preferences.Key<String> = stringPreferencesKey("data")

    /** Mirror [config] and/or [data] into the widget's state. A null leaves
     * that half as it was. */
    suspend fun push(context: Context, id: GlanceId, config: WidgetConfig? = null, data: WidgetData? = null) {
        updateAppWidgetState(context, id) { prefs ->
            config?.let { prefs[CONFIG] = WidgetConfigCodec.encode(it) }
            data?.let { prefs[DATA] = WidgetDataCodec.encode(it) }
        }
    }
}
