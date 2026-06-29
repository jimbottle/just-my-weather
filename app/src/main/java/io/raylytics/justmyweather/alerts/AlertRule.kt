package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.view.WeatherField

/** Which side of the threshold fires the alert. */
enum class Comparison(val key: String, val word: String) {
    ABOVE("above", "above"),
    BELOW("below", "below"),
    ;

    /** True when [value] is on the firing side of [threshold]. Strict on both
     * sides, so a reading exactly at the threshold does not fire. */
    fun test(value: Double, threshold: Double): Boolean =
        when (this) {
            ABOVE -> value > threshold
            BELOW -> value < threshold
        }

    companion object {
        fun byKey(key: String): Comparison? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One personal alert: "tell me when [field] is [above/below] [threshold]",
 * optionally over a forecast [window] instead of the current reading.
 *
 * Deliberately small — a single numeric threshold covers the everyday cases
 * (cold snap, jacket weather, wind picking up). The [window] extends it to the
 * hourly forecast ("overnight low below 35°") without a new rule shape. Rules
 * reuse the same [WeatherField] catalog the view does, so the things you watch
 * are the things you can see.
 */
data class AlertRule(
    val id: String,
    val field: WeatherField,
    val comparison: Comparison,
    val threshold: Double,
    val enabled: Boolean = true,
    val window: AlertWindow = AlertWindow.NOW,
) {
    /** A plain-language description for the rule list: "Temperature above 75°",
     * or "Temperature below 35° overnight" for a forecast window.
     * `this.field` is required — a bare `field` in an accessor is the backing-
     * field keyword, not this property. */
    val summary: String
        get() {
            val core = "${this.field.defaultLabel} ${comparison.word} ${this.field.formatValue(threshold)}"
            return if (window.isForecast) "$core ${window.phrase}" else core
        }
}
