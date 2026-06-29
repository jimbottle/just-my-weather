package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.WeatherField

/** The outcome of testing one rule against one context. [reason] is written
 * for the user — it becomes the notification body. */
data class FireDecision(
    val fired: Boolean,
    val reason: String,
    val value: Double?,
)

/**
 * The heart of alerting, ported in spirit from almanac-bell's evaluator: a pure
 * function `(rule, weather) -> fire | no_fire`. No I/O, no clock, no Android —
 * so it's exhaustively testable and the worker that calls it stays a thin shell.
 *
 * A [NOW][AlertWindow.NOW] rule tests the current observation; a forecast-window
 * rule tests the relevant aggregate of the hourly forecast over its window. In
 * both cases a rule with no usable reading does not fire (we never alert on
 * absent data), and the [FireDecision.reason] always states the actual value so
 * the notification is self-explaining.
 */
object AlertEvaluator {
    fun evaluate(rule: AlertRule, context: WeatherContext): FireDecision =
        if (rule.window.isForecast) {
            evaluateForecast(rule, context)
        } else {
            evaluateNow(rule, context.snapshot)
        }

    private fun evaluateNow(rule: AlertRule, snapshot: WeatherSnapshot): FireDecision {
        val value =
            rule.field.numericValue(snapshot)
                ?: return FireDecision(false, "No ${rule.field.defaultLabel.lowercase()} reading", null)

        val fired = rule.comparison.test(value, rule.threshold)
        val actual = rule.field.formatValue(value)
        val limit = rule.field.formatValue(rule.threshold)
        val reason =
            if (fired) {
                "${rule.field.defaultLabel} is $actual, ${rule.comparison.word} your $limit"
            } else {
                "${rule.field.defaultLabel} $actual is within range"
            }
        return FireDecision(fired, reason, value)
    }

    private fun evaluateForecast(rule: AlertRule, context: WeatherContext): FireDecision {
        val values =
            context.forecast
                .filter { rule.window.contains(it.startTime, context.now, context.zone) }
                .mapNotNull { forecastValue(rule.field, it) }

        if (values.isEmpty()) {
            val label = rule.field.defaultLabel.lowercase()
            return FireDecision(false, "No $label forecast ${rule.window.phrase}", null)
        }

        // BELOW watches the window's low, ABOVE its high — the extreme that could
        // trip the threshold. Evaluating the extreme means a single dip (or spike)
        // anywhere in the window fires the rule.
        val extreme =
            when (rule.comparison) {
                Comparison.BELOW -> values.min()
                Comparison.ABOVE -> values.max()
            }
        val fired = rule.comparison.test(extreme, rule.threshold)
        val actual = rule.field.formatValue(extreme)
        val limit = rule.field.formatValue(rule.threshold)
        val reason =
            if (fired) {
                "${rule.field.defaultLabel} ${rule.window.phrase} reaches $actual, ${rule.comparison.word} your $limit"
            } else {
                "${rule.field.defaultLabel} ${rule.window.phrase} stays within range"
            }
        return FireDecision(fired, reason, extreme)
    }

    /**
     * The forecast carries only temperature and wind, so only those fields have a
     * window value; the rest return null and no forecast rule is offered for them.
     * Exhaustive (no `else`) so adding a [WeatherField] forces a decision here —
     * keep this in sync with [WeatherField.isForecastable].
     */
    private fun forecastValue(field: WeatherField, point: ForecastPoint): Double? =
        when (field) {
            WeatherField.TEMPERATURE -> point.temperatureF
            WeatherField.WIND -> point.windMph
            WeatherField.CONDITIONS, WeatherField.PRECIPITATION, WeatherField.PRESSURE -> null
        }
}
