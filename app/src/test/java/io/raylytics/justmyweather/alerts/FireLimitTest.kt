package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FireLimitTest {
    private val temp = AlertSubject.Field(WeatherField.TEMPERATURE)

    @Test
    fun `times is bounded to 1 through 99`() {
        assertEquals(1, FireLimit.times(1).times)
        assertEquals(99, FireLimit.times(99).times)
        assertThrows(IllegalArgumentException::class.java) { FireLimit.times(0) }
        assertThrows(IllegalArgumentException::class.java) { FireLimit.times(100) }
    }

    @Test
    fun `stored form round-trips and anything out of range reads as unlimited`() {
        assertEquals(0, FireLimit.UNLIMITED.stored)
        assertEquals(FireLimit.UNLIMITED, FireLimit.fromStored(0))
        assertEquals(FireLimit.ONCE, FireLimit.fromStored(1))
        assertEquals(FireLimit.times(7), FireLimit.fromStored(7))
        assertEquals(FireLimit.UNLIMITED, FireLimit.fromStored(-3))
        assertEquals(FireLimit.UNLIMITED, FireLimit.fromStored(500))
    }

    @Test
    fun `labels read as the builder shows them`() {
        assertEquals("Every time", FireLimit.UNLIMITED.label)
        assertEquals("Once", FireLimit.ONCE.label)
        assertEquals("Up to 5 times", FireLimit.times(5).label)
    }

    @Test
    fun `an unlimited rule is never spent and keeps its count moving`() {
        val rule = AlertRule("a", temp, Comparison.BELOW, 40.0)
        val after = rule.afterFiring().afterFiring().afterFiring()
        assertFalse(after.isSpent)
        assertTrue(after.enabled)
        assertEquals(3, after.firedCount)
        assertEquals("Every time", after.limitSummary)
    }

    @Test
    fun `a once rule switches itself off after its single firing`() {
        val rule = AlertRule("a", temp, Comparison.BELOW, 40.0, limit = FireLimit.ONCE)
        assertEquals("Once", rule.limitSummary)
        val after = rule.afterFiring()
        assertTrue(after.isSpent)
        assertFalse(after.enabled)
        assertEquals("Done · fired 1 of 1", after.limitSummary)
    }

    @Test
    fun `an up-to rule reports progress and goes off exactly at the limit`() {
        val rule = AlertRule("a", temp, Comparison.BELOW, 40.0, limit = FireLimit.times(3))
        val once = rule.afterFiring()
        assertTrue(once.enabled)
        assertEquals("Up to 3 times · fired 1 so far", once.limitSummary)
        val twice = once.afterFiring()
        assertTrue(twice.enabled)
        val thrice = twice.afterFiring()
        assertTrue(thrice.isSpent)
        assertFalse(thrice.enabled)
        assertEquals("Done · fired 3 of 3", thrice.limitSummary)
    }
}
