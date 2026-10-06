package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.WeatherField
import kotlin.math.roundToInt

/**
 * What a rule watches. Most subjects are a [WeatherField] from the view catalog
 * — "watch what you can see". [PrecipChance] is the exception: chance of rain
 * comes only from the hourly forecast (there's no current-conditions reading),
 * so it's offered only with a forecast window and never fires on a NOW rule.
 *
 * A subject knows how to read itself from the present observation and from a
 * forecast hour, and how to format its value — so the evaluator stays a single
 * comparison against a threshold regardless of what's being watched.
 *
 * [key] is the stable persistence token (a field subject reuses the field's
 * key, so rules written before precip existed decode unchanged).
 */
sealed class AlertSubject(
    val key: String,
    val label: String,
) {
    /** The reading right now, or null when the subject has no current value. */
    abstract fun currentValue(snapshot: WeatherSnapshot): Double?

    /** The reading for one forecast hour, or null when the forecast lacks it. */
    abstract fun forecastValue(point: ForecastPoint): Double?

    /** Format a value — canonical, like every stored threshold — in the
     * user's unit ("72°" / "22°", "60%"). */
    abstract fun format(value: Double, conventions: Conventions): String

    /** The unit a threshold is typed in ("°C", "mph", "%"), for the form. */
    abstract fun unitLabel(conventions: Conventions): String

    /**
     * A threshold as the user typed it, in their unit, converted to the
     * canonical unit the rule stores and the evaluator compares in. Rules
     * are stored canonical so a rule set in Celsius keeps meaning the same
     * air after a switch to Fahrenheit — and every rule written before
     * regions existed is already canonical, so nothing migrates.
     */
    abstract fun toCanonical(entered: Double, conventions: Conventions): Double

    /** A view-catalog field — watchable now, and in the forecast if forecastable. */
    data class Field(val field: WeatherField) : AlertSubject(field.key, field.defaultLabel) {
        override fun currentValue(snapshot: WeatherSnapshot) = field.numericValue(snapshot)

        override fun forecastValue(point: ForecastPoint) = field.forecastValue(point)

        override fun format(value: Double, conventions: Conventions) = field.formatValue(value, conventions)

        override fun unitLabel(conventions: Conventions): String {
            val units = conventions.units
            return when (field) {
                WeatherField.TEMPERATURE, WeatherField.FEELS_LIKE -> units.temperature.label
                WeatherField.WIND -> units.wind.label
                WeatherField.PRECIPITATION -> units.precipitation.label
                WeatherField.PRESSURE -> units.pressure.label
                WeatherField.CONDITIONS -> ""
            }
        }

        override fun toCanonical(entered: Double, conventions: Conventions): Double {
            val units = conventions.units
            return when (field) {
                WeatherField.TEMPERATURE, WeatherField.FEELS_LIKE -> units.temperature.toFahrenheit(entered)
                WeatherField.WIND -> units.wind.toMph(entered)
                WeatherField.PRECIPITATION -> units.precipitation.toInches(entered)
                WeatherField.PRESSURE -> units.pressure.toInHg(entered)
                WeatherField.CONDITIONS -> entered
            }
        }
    }

    /** Chance of precipitation (0–100%), from the hourly forecast only. */
    data object PrecipChance : AlertSubject("precip_chance", "Chance of rain") {
        override fun currentValue(snapshot: WeatherSnapshot): Double? = null

        override fun forecastValue(point: ForecastPoint) = point.precipProbabilityPercent

        override fun format(value: Double, conventions: Conventions) = "${value.roundToInt()}%"

        override fun unitLabel(conventions: Conventions) = "%"

        override fun toCanonical(entered: Double, conventions: Conventions) = entered
    }

    companion object {
        fun byKey(key: String): AlertSubject? =
            if (key == PrecipChance.key) PrecipChance else WeatherField.byKey(key)?.let(::Field)

        /** Subjects offerable for a current-conditions (NOW) rule. */
        val current: List<AlertSubject> get() = WeatherField.alertable.map(::Field)

        /** Subjects offerable for a forecast-window rule: the forecastable fields
         * plus chance of rain. */
        val forecast: List<AlertSubject>
            get() = WeatherField.alertable.filter { it.isForecastable }.map(::Field) + PrecipChance
    }
}
