package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot

/** The outcome of testing one rule against one snapshot. [reason] is written
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
 * A rule with no current reading for its field does not fire (we never alert on
 * absent data), and the [FireDecision.reason] always states the actual value so
 * the notification is self-explaining.
 */
object AlertEvaluator {
    fun evaluate(rule: AlertRule, snapshot: WeatherSnapshot): FireDecision {
        val value =
            rule.field.numericValue(snapshot)
                ?: return FireDecision(false, "No ${rule.field.defaultLabel.lowercase()} reading", null)

        val fired =
            when (rule.comparison) {
                Comparison.ABOVE -> value > rule.threshold
                Comparison.BELOW -> value < rule.threshold
            }

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
}
