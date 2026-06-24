package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The catalog of data points the user can put on their view.
 *
 * This is the app's main extension point: **to add a new data point, add one
 * entry here.** Each field owns its stable [key] (used for persistence — never
 * rename it), a human [defaultLabel] the user can override, and how to render
 * its value from a [WeatherSnapshot]. Nothing else in the app needs to change —
 * the home view and the customize screen both iterate this enum.
 */
enum class WeatherField(
    val key: String,
    val defaultLabel: String,
) {
    TEMPERATURE("temperature", "Temperature"),
    CONDITIONS("conditions", "Conditions"),
    WIND("wind", "Wind"),
    PRECIPITATION("precipitation", "Precip (last hr)"),
    PRESSURE("pressure", "Pressure"),
    ;

    /**
     * The display value for this field, or null when the snapshot lacks it.
     * A new field adds a branch here — the `when` is exhaustive (no `else`), so
     * the compiler won't let you forget to format it.
     */
    fun format(snapshot: WeatherSnapshot): String? =
        when (this) {
            TEMPERATURE -> snapshot.temperatureF?.let { "${it.roundToInt()}°" }
            CONDITIONS -> snapshot.conditions
            WIND -> snapshot.windMph?.let { if (it < 1.0) "Calm" else "${it.roundToInt()} mph" }
            PRECIPITATION -> snapshot.precipitationIn?.let { String.format(Locale.US, "%.2f in", it) }
            PRESSURE -> snapshot.pressureInHg?.let { String.format(Locale.US, "%.2f inHg", it) }
        }

    companion object {
        fun byKey(key: String): WeatherField? = entries.firstOrNull { it.key == key }
    }
}
