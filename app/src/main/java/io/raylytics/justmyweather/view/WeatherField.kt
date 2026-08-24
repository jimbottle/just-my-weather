package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ForecastPoint
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

    /** Whether this field is a number you can set an alert threshold on.
     * Conditions is descriptive text, so it isn't. */
    val isNumeric: Boolean get() = this != CONDITIONS

    /**
     * The grid width this field's module ships at (the user resizes from
     * there). Sized to the content: temperature is the hero the app opens on,
     * conditions is free text that wants room to say "Chance Showers And
     * Thunderstorms", pressure's value string is the longest of the numerics,
     * and wind/precip are short enough for a quarter tile.
     */
    val defaultSpan: ModuleSpan
        get() =
            when (this) {
                TEMPERATURE -> ModuleSpan.FULL
                CONDITIONS, PRESSURE -> ModuleSpan.HALF
                WIND, PRECIPITATION -> ModuleSpan.QUARTER
            }

    /** Whether the hourly forecast carries this field, so a forecast-window
     * alert ("overnight low") can be built on it. The NWS hourly forecast only
     * gives temperature and wind. */
    val isForecastable: Boolean get() = this == TEMPERATURE || this == WIND

    /**
     * This field's value for one forecast hour (in its display unit), or null
     * when the forecast doesn't carry it. The single source for forecast-window
     * alert evaluation — exhaustive so adding a field forces a decision, and
     * the non-null cases are exactly [isForecastable].
     */
    fun forecastValue(point: ForecastPoint): Double? =
        when (this) {
            TEMPERATURE -> point.temperatureF
            WIND -> point.windMph
            CONDITIONS, PRECIPITATION, PRESSURE -> null
        }

    /**
     * The raw numeric reading for this field (in its display unit), or null when
     * the snapshot lacks it or the field isn't numeric. This is what the alert
     * evaluator compares against a threshold.
     */
    fun numericValue(snapshot: WeatherSnapshot): Double? =
        when (this) {
            TEMPERATURE -> snapshot.temperatureF
            CONDITIONS -> null
            WIND -> snapshot.windMph
            PRECIPITATION -> snapshot.precipitationIn
            PRESSURE -> snapshot.pressureInHg
        }

    /** Format a numeric value of this field with its unit ("72°", "10 mph"). */
    fun formatValue(value: Double): String =
        when (this) {
            TEMPERATURE -> "${value.roundToInt()}°"
            CONDITIONS -> value.roundToInt().toString()
            WIND -> "${value.roundToInt()} mph"
            PRECIPITATION -> String.format(Locale.US, "%.2f in", value)
            PRESSURE -> String.format(Locale.US, "%.2f inHg", value)
        }

    /**
     * The display value for this field, or null when the snapshot lacks it.
     * A new field adds a branch to [numericValue] / [formatValue] — both are
     * exhaustive (no `else`), so the compiler won't let you forget to handle it.
     */
    fun format(snapshot: WeatherSnapshot): String? =
        when (this) {
            CONDITIONS -> snapshot.conditions
            // "Calm" reads better than "0 mph" on the glance.
            WIND -> snapshot.windMph?.let { if (it < 1.0) "Calm" else formatValue(it) }
            else -> numericValue(snapshot)?.let { formatValue(it) }
        }

    companion object {
        fun byKey(key: String): WeatherField? = entries.firstOrNull { it.key == key }

        /** Fields a user can build an alert on. */
        val alertable: List<WeatherField> get() = entries.filter { it.isNumeric }
    }
}
