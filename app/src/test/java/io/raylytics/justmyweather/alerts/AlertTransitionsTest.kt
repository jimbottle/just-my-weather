package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
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

    private val cold = AlertRule("cold", WeatherField.TEMPERATURE, Comparison.BELOW, 40.0)

    @Test
    fun `enter fired notifies and records the rule as firing`() {
        val outcome = AlertTransitions.compute(listOf(cold), snapshot(temp = 35.0), previouslyFiring = emptySet())
        assertEquals(listOf("cold"), outcome.toNotify.map { it.rule.id })
        assertEquals(setOf("cold"), outcome.nowFiring)
    }

    @Test
    fun `staying fired does not notify again but stays in the firing set`() {
        val outcome = AlertTransitions.compute(listOf(cold), snapshot(temp = 35.0), previouslyFiring = setOf("cold"))
        assertTrue(outcome.toNotify.isEmpty())
        assertEquals(setOf("cold"), outcome.nowFiring)
    }

    @Test
    fun `exiting fired clears the firing set and does not notify`() {
        val outcome = AlertTransitions.compute(listOf(cold), snapshot(temp = 50.0), previouslyFiring = setOf("cold"))
        assertTrue(outcome.toNotify.isEmpty())
        assertTrue(outcome.nowFiring.isEmpty())
    }

    @Test
    fun `re-entering fired after exiting notifies again`() {
        // exit cleared the set; the next dip re-notifies because it's no longer
        // in previouslyFiring.
        val outcome = AlertTransitions.compute(listOf(cold), snapshot(temp = 35.0), previouslyFiring = emptySet())
        assertEquals(listOf("cold"), outcome.toNotify.map { it.rule.id })
    }

    @Test
    fun `a disabled rule is ignored even if it was firing`() {
        val disabled = cold.copy(enabled = false)
        val outcome =
            AlertTransitions.compute(listOf(disabled), snapshot(temp = 35.0), previouslyFiring = setOf("cold"))
        assertTrue(outcome.toNotify.isEmpty())
        assertTrue(outcome.nowFiring.isEmpty()) // dropped, so re-enabling later re-notifies
    }

    @Test
    fun `the notify decision carries a reason for the notification body`() {
        val outcome = AlertTransitions.compute(listOf(cold), snapshot(temp = 35.0), previouslyFiring = emptySet())
        assertTrue(outcome.toNotify.single().decision.reason.contains("below"))
    }
}
