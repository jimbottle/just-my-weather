package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AlertTransitionsTest {
    private fun snapshot(temp: Double?) =
        WeatherSnapshot(
            locationLabel = "Test, ST",
            temperatureF = temp,
            conditions = "Clear",
            windMph = 5.0,
            precipitationIn = 0.0,
            pressureInHg = 29.92,
            observedAt = Instant.parse("2026-06-24T18:00:00Z"),
        )

    // These rules are all NOW rules, so the forecast/zone are irrelevant here;
    // the dedup behaviour is what's under test, not the evaluation path.
    private fun context(temp: Double?) =
        WeatherContext(snapshot(temp), now = Instant.parse("2026-06-24T18:00:00Z"))

    private val cold = AlertRule("cold", AlertSubject.Field(WeatherField.TEMPERATURE), Comparison.BELOW, 40.0)

    @Test
    fun `enter fired notifies and records the rule as firing`() {
        val outcome = AlertTransitions.compute(listOf(cold), context(temp = 35.0), previouslyFiring = emptySet())
        assertEquals(listOf("cold"), outcome.toNotify.map { it.rule.id })
        assertEquals(setOf("cold"), outcome.nowFiring)
    }

    @Test
    fun `staying fired does not notify again but stays in the firing set`() {
        val outcome = AlertTransitions.compute(listOf(cold), context(temp = 35.0), previouslyFiring = setOf("cold"))
        assertTrue(outcome.toNotify.isEmpty())
        assertEquals(setOf("cold"), outcome.nowFiring)
    }

    @Test
    fun `exiting fired clears the firing set and does not notify`() {
        val outcome = AlertTransitions.compute(listOf(cold), context(temp = 50.0), previouslyFiring = setOf("cold"))
        assertTrue(outcome.toNotify.isEmpty())
        assertTrue(outcome.nowFiring.isEmpty())
    }

    @Test
    fun `re-entering fired after exiting notifies again`() {
        // exit cleared the set; the next dip re-notifies because it's no longer
        // in previouslyFiring.
        val outcome = AlertTransitions.compute(listOf(cold), context(temp = 35.0), previouslyFiring = emptySet())
        assertEquals(listOf("cold"), outcome.toNotify.map { it.rule.id })
    }

    @Test
    fun `a disabled rule is ignored even if it was firing`() {
        val disabled = cold.copy(enabled = false)
        val outcome =
            AlertTransitions.compute(listOf(disabled), context(temp = 35.0), previouslyFiring = setOf("cold"))
        assertTrue(outcome.toNotify.isEmpty())
        assertTrue(outcome.nowFiring.isEmpty()) // dropped, so re-enabling later re-notifies
    }

    @Test
    fun `an unlimited rule returns unchanged apart from its count`() {
        val outcome = AlertTransitions.compute(listOf(cold), context(temp = 35.0), previouslyFiring = emptySet())
        assertEquals(listOf(cold.copy(firedCount = 1)), outcome.rules)
    }

    @Test
    fun `a once rule fires, switches itself off, and leaves the firing set`() {
        val once = cold.copy(limit = FireLimit.ONCE)
        val outcome = AlertTransitions.compute(listOf(once), context(temp = 35.0), previouslyFiring = emptySet())
        assertEquals(listOf("cold"), outcome.toNotify.map { it.rule.id })
        val saved = outcome.rules.single()
        assertFalse(saved.enabled)
        assertEquals(1, saved.firedCount)
        // Off means off: not in the firing set, so a later re-enable can notify.
        assertTrue(outcome.nowFiring.isEmpty())
    }

    @Test
    fun `an up-to rule keeps notifying per onset until the limit, then stops`() {
        val twice = cold.copy(limit = FireLimit.times(2))
        // Onset 1.
        val first = AlertTransitions.compute(listOf(twice), context(temp = 35.0), previouslyFiring = emptySet())
        assertEquals(1, first.toNotify.size)
        val afterFirst = first.rules.single()
        assertTrue(afterFirst.enabled)
        assertEquals(setOf("cold"), first.nowFiring)
        // Still cold: no second notification, count unchanged.
        val held =
            AlertTransitions.compute(listOf(afterFirst), context(temp = 35.0), previouslyFiring = first.nowFiring)
        assertTrue(held.toNotify.isEmpty())
        assertEquals(afterFirst, held.rules.single())
        // Warms up, then a second onset: the last allowed firing switches it off.
        val warm =
            AlertTransitions.compute(listOf(afterFirst), context(temp = 50.0), previouslyFiring = held.nowFiring)
        val second =
            AlertTransitions.compute(listOf(afterFirst), context(temp = 35.0), previouslyFiring = warm.nowFiring)
        assertEquals(1, second.toNotify.size)
        assertFalse(second.rules.single().enabled)
        assertEquals(2, second.rules.single().firedCount)
    }

    @Test
    fun `a spent rule that is somehow still enabled is ignored`() {
        val spent = cold.copy(limit = FireLimit.ONCE, firedCount = 1)
        val outcome = AlertTransitions.compute(listOf(spent), context(temp = 35.0), previouslyFiring = emptySet())
        assertTrue(outcome.toNotify.isEmpty())
        assertTrue(outcome.nowFiring.isEmpty())
        assertEquals(listOf(spent), outcome.rules)
    }

    @Test
    fun `the notify decision carries a reason for the notification body`() {
        val outcome = AlertTransitions.compute(listOf(cold), context(temp = 35.0), previouslyFiring = emptySet())
        assertTrue(outcome.toNotify.single().decision.reason.contains("below"))
    }
}
