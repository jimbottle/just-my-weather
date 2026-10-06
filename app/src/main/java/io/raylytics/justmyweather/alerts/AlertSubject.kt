package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.WeatherField
import java.util.Locale
import kotlin.math.abs
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

    /** A canonical value — like every stored threshold — as a bare number
     * in the user's unit (35.6 °F → 2.0 in Celsius). */
    abstract fun fromCanonical(value: Double, conventions: Conventions): Double

    /** What follows the number: "°", " km/h", "%". */
    protected abstract fun suffix(conventions: Conventions): String

    /** The decimals the glance shows this value at (0 for degrees and
     * speeds, the unit's own for pressure and precipitation). */
    protected abstract fun decimals(conventions: Conventions): Int

    /** A value as the glance shows it ("72°" / "22°", "60%"). */
    fun format(value: Double, conventions: Conventions): String = formatAt(value, conventions, decimals(conventions))

    /**
     * A threshold at the precision it was SET at. A rule made in Fahrenheit
     * ("below 35°") and read in Celsius is below 1.7°, and calling it "below
     * 2°" misstates when it fires; a rule typed as 2 °C stays "2°". One
     * decimal past the glance's is enough for any unit to show what was set.
     */
    fun formatThreshold(threshold: Double, conventions: Conventions): String =
        formatAt(threshold, conventions, thresholdDecimals(threshold, conventions))

    /**
     * A reading and the threshold it crossed, worded so the notification can
     * never say "is 2°, below your 2°": when rounding makes them read the
     * same, both gain a decimal until they don't (up to three). The rule
     * compares canonical values exactly; this only keeps the words honest.
     */
    fun formatPair(value: Double, threshold: Double, conventions: Conventions): Pair<String, String> {
        var valueDecimals = decimals(conventions)
        var thresholdDecimals = thresholdDecimals(threshold, conventions)
        repeat(MAX_EXTRA_DECIMALS) {
            val actual = formatAt(value, conventions, valueDecimals)
            val limit = formatAt(threshold, conventions, thresholdDecimals)
            if (actual != limit) return actual to limit
            valueDecimals = maxOf(valueDecimals, thresholdDecimals) + 1
            thresholdDecimals = valueDecimals
        }
        return formatAt(value, conventions, valueDecimals) to formatAt(threshold, conventions, thresholdDecimals)
    }

    private fun thresholdDecimals(threshold: Double, conventions: Conventions): Int {
        val base = decimals(conventions)
        val scaled = fromCanonical(threshold, conventions) * Math.pow(10.0, base.toDouble())
        return if (abs(scaled - Math.round(scaled)) < ROUND_ENOUGH) base else base + 1
    }

    private fun formatAt(value: Double, conventions: Conventions, decimals: Int): String {
        val shown = fromCanonical(value, conventions)
        val number =
            if (decimals == 0) {
                shown.roundToInt().toString()
            } else {
                // A value that rounds to zero must not read "-0.0".
                String.format(Locale.US, "%.${decimals}f", shown).removePrefix("-").let { unsigned ->
                    if (shown < 0 && unsigned.any { it in '1'..'9' }) "-$unsigned" else unsigned
                }
            }
        return number + suffix(conventions)
    }

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

        override fun fromCanonical(value: Double, conventions: Conventions): Double {
            val units = conventions.units
            return when (field) {
                WeatherField.TEMPERATURE, WeatherField.FEELS_LIKE -> units.temperature.fromFahrenheit(value)
                WeatherField.WIND -> units.wind.fromMph(value)
                WeatherField.PRECIPITATION -> units.precipitation.fromInches(value)
                WeatherField.PRESSURE -> units.pressure.fromInHg(value)
                WeatherField.CONDITIONS -> value
            }
        }

        override fun suffix(conventions: Conventions): String =
            when (field) {
                WeatherField.TEMPERATURE, WeatherField.FEELS_LIKE -> "°"
                WeatherField.CONDITIONS -> ""
                else -> " " + unitLabel(conventions)
            }

        override fun decimals(conventions: Conventions): Int =
            when (field) {
                WeatherField.PRECIPITATION -> conventions.units.precipitation.decimals
                WeatherField.PRESSURE -> conventions.units.pressure.decimals
                else -> 0
            }

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

        override fun fromCanonical(value: Double, conventions: Conventions) = value

        override fun suffix(conventions: Conventions) = "%"

        override fun decimals(conventions: Conventions) = 0

        override fun unitLabel(conventions: Conventions) = "%"

        override fun toCanonical(entered: Double, conventions: Conventions) = entered
    }

    companion object {
        private const val MAX_EXTRA_DECIMALS = 3
        private const val ROUND_ENOUGH = 1e-6

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
