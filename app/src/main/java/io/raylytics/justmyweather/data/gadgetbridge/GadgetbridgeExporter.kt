package io.raylytics.justmyweather.data.gadgetbridge

import io.raylytics.justmyweather.data.GadgetbridgeSettingsRepository
import io.raylytics.justmyweather.data.WeatherSnapshot
import kotlinx.coroutines.flow.first

/**
 * The one entry point the rest of the app uses: hand it a snapshot and, if the
 * user turned the feature on, it reaches the watch.
 *
 * Three collaborators behind one call so no caller has to remember the order —
 * check the setting, build the payload, broadcast. The setting is read per
 * export rather than cached so toggling it off takes effect on the very next
 * refresh.
 */
class GadgetbridgeExporter(
    private val settings: GadgetbridgeSettingsRepository,
    private val broadcaster: GadgetbridgeBroadcaster,
) {
    suspend fun export(snapshot: WeatherSnapshot) {
        if (!settings.enabled.first()) return
        val payload = GadgetbridgeWeather.payloadFor(snapshot) ?: return
        broadcaster.send(payload)
    }
}
