package io.raylytics.justmyweather.alerts

/** A rule that fired plus why — what the notifier needs. */
data class FiredAlert(
    val rule: AlertRule,
    val decision: FireDecision,
)

/**
 * The result of one evaluation tick: which rules to notify *now* (only those
 * that just entered the fired state), the full set of rule ids currently
 * firing (to persist as the next tick's "previously firing"), and the rule
 * list as it should be saved afterwards — fire counts moved on, and any rule
 * that just reached its limit switched off.
 */
data class AlertOutcome(
    val toNotify: List<FiredAlert>,
    val nowFiring: Set<String>,
    val rules: List<AlertRule>,
)

/**
 * The transition-dedup logic, extracted as a pure function so the behavior the
 * whole feature rests on — "notify once when a rule *enters* fired, stay quiet
 * while it stays fired" — is unit-testable without WorkManager or DataStore.
 * The worker is just the I/O shell that feeds this and dispatches its output.
 *
 * Disabled rules are ignored entirely: they neither notify nor stay in the
 * firing set, so disabling a firing rule cleanly resets it. A spent rule (its
 * [FireLimit] reached) is treated the same way — it should already be off,
 * since reaching the limit switches it off, but the guard holds either way.
 *
 * A rule that fires for the last time its limit allows comes back disabled in
 * [AlertOutcome.rules] and is left out of the firing set, exactly as if the
 * user had switched it off: re-enabling it later starts a fresh count.
 */
object AlertTransitions {
    fun compute(
        rules: List<AlertRule>,
        context: WeatherContext,
        previouslyFiring: Set<String>,
    ): AlertOutcome {
        val toNotify = mutableListOf<FiredAlert>()
        val nowFiring = mutableSetOf<String>()
        val updated =
            rules.map { rule ->
                if (!rule.enabled || rule.isSpent) return@map rule
                val decision = AlertEvaluator.evaluate(rule, context)
                if (!decision.fired) return@map rule
                if (rule.id in previouslyFiring) {
                    nowFiring += rule.id
                    return@map rule
                }
                toNotify += FiredAlert(rule, decision)
                val next = rule.afterFiring()
                if (next.enabled) nowFiring += rule.id
                next
            }
        return AlertOutcome(toNotify, nowFiring, updated)
    }
}
