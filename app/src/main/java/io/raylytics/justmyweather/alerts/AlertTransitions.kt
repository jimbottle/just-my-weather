package io.raylytics.justmyweather.alerts

/** A rule that fired plus why — what the notifier needs. */
data class FiredAlert(
    val rule: AlertRule,
    val decision: FireDecision,
)

/**
 * The result of one evaluation tick: which rules to notify *now* (only those
 * that just entered the fired state) and the full set of rule ids currently
 * firing (to persist as the next tick's "previously firing").
 */
data class AlertOutcome(
    val toNotify: List<FiredAlert>,
    val nowFiring: Set<String>,
)

/**
 * The transition-dedup logic, extracted as a pure function so the behavior the
 * whole feature rests on — "notify once when a rule *enters* fired, stay quiet
 * while it stays fired" — is unit-testable without WorkManager or DataStore.
 * The worker is just the I/O shell that feeds this and dispatches its output.
 *
 * Disabled rules are ignored entirely: they neither notify nor stay in the
 * firing set, so disabling a firing rule cleanly resets it.
 */
object AlertTransitions {
    fun compute(
        rules: List<AlertRule>,
        context: WeatherContext,
        previouslyFiring: Set<String>,
    ): AlertOutcome {
        val toNotify = mutableListOf<FiredAlert>()
        val nowFiring = mutableSetOf<String>()
        rules.filter { it.enabled }.forEach { rule ->
            val decision = AlertEvaluator.evaluate(rule, context)
            if (decision.fired) {
                nowFiring += rule.id
                if (rule.id !in previouslyFiring) {
                    toNotify += FiredAlert(rule, decision)
                }
            }
        }
        return AlertOutcome(toNotify, nowFiring)
    }
}
