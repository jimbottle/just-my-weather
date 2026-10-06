package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.view.Conventions

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
 * One personal alert: "tell me when [subject] is [above/below] [threshold]",
 * optionally over a forecast [window] instead of the current reading.
 *
 * Deliberately small — a single numeric threshold covers the everyday cases
 * (cold snap, jacket weather, wind picking up). The [window] extends it to the
 * hourly forecast ("overnight low below 35°", "chance of rain above 50% within
 * 12 hours") without a new rule shape, and the [subject] is usually a view
 * field, so the things you watch are the things you can see.
 */
data class AlertRule(
    val id: String,
    val subject: AlertSubject,
    val comparison: Comparison,
    val threshold: Double,
    val enabled: Boolean = true,
    val window: AlertWindow = AlertWindow.NOW,
    /** How many times this rule may fire before switching itself off. */
    val limit: FireLimit = FireLimit.UNLIMITED,
    /** Notifications sent so far under the current [limit]. Reset to zero when
     * a spent rule is switched back on. Meaningless for an unlimited rule. */
    val firedCount: Int = 0,
) {
    /** A plain-language description for the rule list: "Temperature above 75°",
     * or "Chance of rain above 50% within 12 hours" for a forecast window —
     * the threshold in the user's units. */
    fun summary(conventions: Conventions): String {
        val core = "${subject.label} ${comparison.word} ${subject.format(threshold, conventions)}"
        return if (window.isForecast) "$core ${window.phrase}" else core
    }

    /** True once the rule has fired as many times as its [limit] allows. */
    val isSpent: Boolean
        get() = limit.times?.let { firedCount >= it } == true

    /** The second line of the rule row: the limit and how far along it is. */
    val limitSummary: String
        get() {
            val times = limit.times ?: return limit.label
            return when {
                isSpent -> "Done · fired $times of $times"
                times == 1 -> limit.label
                else -> "${limit.label} · fired $firedCount so far"
            }
        }

    /**
     * The rule after one notification: the count moves on, and a rule that has
     * just reached its limit switches itself off. Off, not merely quiet, so the
     * list shows it as done, the worker stops polling for it like any other
     * disabled rule, and the toggle is the one obvious way to arm it again.
     */
    fun afterFiring(): AlertRule {
        val next = copy(firedCount = firedCount + 1)
        return if (next.isSpent) next.copy(enabled = false) else next
    }
}
