package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.view.WeatherField

/** Which side of the threshold fires the alert. */
enum class Comparison(val key: String, val word: String) {
    ABOVE("above", "above"),
    BELOW("below", "below"),
    ;

    companion object {
        fun byKey(key: String): Comparison? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One personal alert: "tell me when [field] is [above/below] [threshold]".
 *
 * Deliberately small — a single numeric threshold on a current reading covers
 * the everyday cases (cold snap, jacket weather, wind picking up, rain on the
 * ground). Forecast-window and overnight-low rules build on this later. Rules
 * reuse the same [WeatherField] catalog the view does, so the things you watch
 * are the things you can see.
 */
data class AlertRule(
    val id: String,
    val field: WeatherField,
    val comparison: Comparison,
    val threshold: Double,
    val enabled: Boolean = true,
) {
    /** A plain-language description for the rule list: "Temperature above 75°".
     * `this.field` is required — a bare `field` in an accessor is the backing-
     * field keyword, not this property. */
    val summary: String
        get() = "${this.field.defaultLabel} ${comparison.word} ${this.field.formatValue(threshold)}"
}
