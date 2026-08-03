package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.nws.ActiveAlert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every case below uses an (event, severity) pair observed in the live NWS
 * feed on 2026-08-03, because this classifier decides what is allowed to
 * interrupt an otherwise calm screen — and the two obvious implementations
 * both fail on real data.
 */
class SafetyAlertsTest {
    private fun alert(event: String, severity: String, id: String = event) =
        ActiveAlert(id = id, event = event, severity = severity, headline = "$event in effect")

    @Test
    fun `air quality qualifies despite carrying Unknown severity`() {
        // The case that rules out a severity-only filter: observed live as
        // severity=Unknown, urgency=Unknown, 26 active.
        assertTrue(SafetyAlerts.isSafety(alert("Air Quality Alert", "Unknown")))
    }

    @Test
    fun `test traffic never qualifies, even though it is also Unknown`() {
        // …and the case that rules out simply allowing Unknown through.
        assertFalse(SafetyAlerts.isSafety(alert("Test Message", "Unknown")))
        assertFalse(SafetyAlerts.isSafety(alert("Test Message", "Extreme")))
    }

    @Test
    fun `heat qualifies at every severity it is issued under`() {
        // Observed live: Heat Advisory is Moderate, Extreme Heat Warning is
        // Severe. A severity cut would show one and hide the other.
        assertTrue(SafetyAlerts.isSafety(alert("Heat Advisory", "Moderate")))
        assertTrue(SafetyAlerts.isSafety(alert("Extreme Heat Warning", "Severe")))
        assertTrue(SafetyAlerts.isSafety(alert("Extreme Heat Watch", "Severe")))
    }

    @Test
    fun `life-threatening events qualify by name whatever the grading`() {
        assertTrue(SafetyAlerts.isSafety(alert("Tornado Warning", "Extreme")))
        assertTrue(SafetyAlerts.isSafety(alert("Hurricane Warning", "Extreme")))
        assertTrue(SafetyAlerts.isSafety(alert("Tropical Storm Watch", "Moderate")))
        assertTrue(SafetyAlerts.isSafety(alert("Tsunami Warning", "Extreme")))
    }

    @Test
    fun `severe and extreme qualify on severity alone`() {
        assertTrue(SafetyAlerts.isSafety(alert("Severe Thunderstorm Warning", "Severe")))
        assertTrue(SafetyAlerts.isSafety(alert("Flash Flood Warning", "Severe")))
        assertTrue(SafetyAlerts.isSafety(alert("High Wind Warning", "Severe")))
        assertTrue(SafetyAlerts.isSafety(alert("Red Flag Warning", "Severe")))
    }

    @Test
    fun `everyday advisories stay off the glance`() {
        // Small Craft Advisory alone was 172 of the 362 live alerts. Surfacing
        // Minor severities would make the banner near-permanent on the coast
        // and train the user to ignore it.
        assertFalse(SafetyAlerts.isSafety(alert("Small Craft Advisory", "Minor")))
        assertFalse(SafetyAlerts.isSafety(alert("Beach Hazards Statement", "Moderate")))
        assertFalse(SafetyAlerts.isSafety(alert("Special Weather Statement", "Moderate")))
        assertFalse(SafetyAlerts.isSafety(alert("Rip Current Statement", "Moderate")))
        assertFalse(SafetyAlerts.isSafety(alert("Dense Fog Advisory", "Minor")))
    }

    @Test
    fun `matching ignores case`() {
        assertTrue(SafetyAlerts.isSafety(alert("TORNADO WARNING", "extreme")))
        assertTrue(SafetyAlerts.isSafety(alert("air quality alert", "unknown")))
    }

    @Test
    fun `filter keeps only safety alerts and puts the worst first`() {
        val input =
            listOf(
                alert("Heat Advisory", "Moderate"),
                alert("Small Craft Advisory", "Minor"),
                alert("Tornado Warning", "Extreme"),
                alert("Air Quality Alert", "Unknown"),
                alert("Flash Flood Warning", "Severe"),
                alert("Test Message", "Unknown"),
            )
        val out = SafetyAlerts.filter(input).map { it.event }
        assertEquals(
            listOf("Tornado Warning", "Flash Flood Warning", "Heat Advisory", "Air Quality Alert"),
            out,
        )
    }

    @Test
    fun `an unrecognised severity sorts last rather than throwing`() {
        assertTrue(SafetyAlerts.rank("banana") > SafetyAlerts.rank("unknown"))
        assertEquals(0, SafetyAlerts.rank("Extreme"))
    }

    @Test
    fun `no alerts means no banner`() {
        assertEquals(emptyList<ActiveAlert>(), SafetyAlerts.filter(emptyList()))
        assertEquals(
            emptyList<ActiveAlert>(),
            SafetyAlerts.filter(listOf(alert("Small Craft Advisory", "Minor"))),
        )
    }
}
