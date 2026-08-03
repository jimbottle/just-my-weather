package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.nws.ActiveAlert
import java.util.Locale

/*
 * Which active NWS alerts are safety concerns worth putting on the glance.
 *
 * Pure — alerts in, alerts out — so the rule that decides what interrupts a
 * calm screen is unit-tested rather than discovered in a storm.
 *
 * The shape of this comes from real data, not intuition: 362 alerts active
 * nationwide on 2026-08-03 (api.weather.gov/alerts/active) showed that neither
 * obvious approach works on its own.
 *
 *  - Severity alone MISSES the hazards people ask for. "Air Quality Alert"
 *    carries severity=Unknown and urgency=Unknown (26 active), so any
 *    severity >= Severe filter drops it entirely.
 *  - Including Unknown to catch it lets "Test Message" through — also Unknown,
 *    and the one thing that must never appear as a safety warning.
 *  - Heat spans severities: "Heat Advisory" is Moderate (33 active) while
 *    "Extreme Heat Warning" is Severe (25). A severity cut splits one hazard
 *    in half.
 *  - Minor is mostly noise: "Small Craft Advisory" alone was 172 of the 362.
 *
 * So: named hazards match by event name at any severity, everything else has
 * to clear NWS's own Extreme/Severe bar, and test traffic is excluded first.
 */
object SafetyAlerts {
    /**
     * Hazards that qualify on name alone, because their severity is unreliable
     * or spans the cut. Matched as lowercase substrings of the event name,
     * which is how NWS composes them ("Excessive Heat Warning", "Extreme Heat
     * Watch", "Air Quality Alert").
     */
    private val SAFETY_EVENTS =
        listOf(
            // Named by Evan, and the reason a severity-only filter fails.
            "air quality",
            "heat",
            // Life-threatening regardless of how a given office grades them.
            "tornado",
            "hurricane",
            "typhoon",
            "tropical storm",
            "tsunami",
            "blizzard",
            // Air you can't breathe, same intent as air quality.
            "smoke",
            "dust storm",
        )

    /** NWS severities that clear the bar on their own, most severe first. */
    private val SEVERE_ENOUGH = setOf("extreme", "severe")

    /**
     * Display order: worst first, so the top line of the banner is the thing
     * most likely to hurt you. Unrecognised severities sort last rather than
     * throwing.
     */
    private val SEVERITY_RANK = listOf("extreme", "severe", "moderate", "minor", "unknown")

    /**
     * True when [alert] is a safety concern.
     *
     * The test-message exclusion comes FIRST and beats every other rule: NWS
     * pushes real "Test Message" alerts through the live feed, and a test that
     * renders as a tornado warning is worse than any alert this misses. No
     * current NWS event name contains "test" other than test traffic.
     */
    fun isSafety(alert: ActiveAlert): Boolean {
        val event = alert.event.lowercase(Locale.US)
        if ("test" in event) return false
        if (SAFETY_EVENTS.any { it in event }) return true
        return alert.severity.lowercase(Locale.US) in SEVERE_ENOUGH
    }

    /** The safety alerts among [alerts], worst severity first. */
    fun filter(alerts: List<ActiveAlert>): List<ActiveAlert> =
        alerts.filter(::isSafety).sortedBy { rank(it.severity) }

    /** Position in [SEVERITY_RANK]; unknown values sort after everything known. */
    fun rank(severity: String): Int =
        SEVERITY_RANK.indexOf(severity.lowercase(Locale.US)).takeIf { it >= 0 } ?: SEVERITY_RANK.size
}
