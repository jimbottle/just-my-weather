package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The predicate that decides whether the background worker runs at all.
 *
 * It used to live inline in an Activity lambda where nothing could reach it —
 * flipping it back to a rules-only test left the whole suite green while
 * safety-alert users silently lost their worker. Each clause is asserted
 * independently here so that can't happen again.
 */
class AlertSchedulingTest {
    private val rule =
        AlertRule(
            "a",
            AlertSubject.Field(WeatherField.TEMPERATURE),
            Comparison.BELOW,
            40.0,
            enabled = true,
        )
    private val off = AlertSettings.DEFAULT
    private val safetyOn = AlertSettings.DEFAULT.copy(safetyNotifications = true)

    @Test
    fun `an enabled rule alone is reason enough`() {
        assertTrue(AlertScheduling.hasWork(listOf(rule), off))
    }

    @Test
    fun `safety alerts alone are reason enough, with no rules at all`() {
        // The clause the old rules-only predicate dropped. This is the whole
        // point of the setting: wanting tornado warnings and nothing else.
        assertTrue(AlertScheduling.hasWork(emptyList(), safetyOn))
    }

    @Test
    fun `a disabled rule is not reason enough`() {
        assertFalse(AlertScheduling.hasWork(listOf(rule.copy(enabled = false)), off))
    }

    @Test
    fun `nothing enabled and safety off means the worker should stop`() {
        assertFalse(AlertScheduling.hasWork(emptyList(), off))
    }

    @Test
    fun `turning safety off does not stop a worker a live rule still needs`() {
        assertTrue(AlertScheduling.hasWork(listOf(rule), off))
    }
}
