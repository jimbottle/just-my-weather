package io.raylytics.justmyweather.alerts

/**
 * Whether the background alert check has any reason to run.
 *
 * One pure function rather than the same `||` repeated at each call site. It
 * lived inline in an Activity lambda, in the app's `onCreate`, and — wrongly —
 * as a rules-only test in `setPollCadence`, which is how a safety-alerts user
 * with no personal rules ended up saving a new cadence that never reached
 * WorkManager. Anything that can drift out of sync across four call sites
 * belongs in one place with a test.
 */
object AlertScheduling {
    /**
     * True when something wants the periodic worker alive: a personal rule the
     * user enabled, OR official safety alerts being switched on. The second
     * clause is the whole point of the safety setting — that user may well have
     * no personal rules at all, and treating "no rules" as "nothing to do"
     * would cancel the very worker that delivers their tornado warnings.
     */
    fun hasWork(rules: List<AlertRule>, settings: AlertSettings): Boolean =
        rules.any { it.enabled } || settings.safetyNotifications
}
