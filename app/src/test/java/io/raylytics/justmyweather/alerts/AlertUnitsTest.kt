package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.region.Regions
import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** Thresholds are typed in the user's units and stored canonical, so a rule
 * keeps meaning the same air when the units change. */
class AlertUnitsTest {
    private val uk = Regions.of("GB").conventions
    private val temperature = AlertSubject.Field(WeatherField.TEMPERATURE)

    @Test
    fun `a Celsius threshold is stored as Fahrenheit and reads back in either`() {
        assertEquals("°C", temperature.unitLabel(uk))
        val stored = temperature.toCanonical(2.0, uk)
        assertEquals(35.6, stored, 1e-9)
        val rule = AlertRule("r", temperature, Comparison.BELOW, stored)
        assertEquals("Temperature below 2°", rule.summary(uk))
        // Read back in Fahrenheit, it says what it really is — not "36°".
        assertEquals("Temperature below 35.6°", rule.summary(Conventions.US))
    }

    @Test
    fun `the comparison is canonical and the notification reads in the user's units`() {
        val rule = AlertRule("r", temperature, Comparison.BELOW, temperature.toCanonical(2.0, uk))
        val snapshot = WeatherSnapshot("London", 33.8, null, null, null, null, null) // 1 °C
        val decision = AlertEvaluator.evaluate(rule, WeatherContext(snapshot, Instant.EPOCH, conventions = uk))
        assertTrue(decision.fired)
        assertEquals("Temperature is 1°, below your 2°", decision.reason)
    }

    @Test
    fun `wind and chance of rain convert, or don't, as they should`() {
        val wind = AlertSubject.Field(WeatherField.WIND)
        val canada = Regions.of("CA").conventions
        assertEquals("km/h", wind.unitLabel(canada))
        assertEquals(31.07, wind.toCanonical(50.0, canada), 0.01)
        assertEquals(40.0, AlertSubject.PrecipChance.toCanonical(40.0, canada))
    }

    @Test
    fun `a rule set in one unit and read in another states when it really fires`() {
        // Set in Fahrenheit at 35°, which is 1.67 °C: "below 2°" would be a lie.
        val rule = AlertRule("r", temperature, Comparison.BELOW, 35.0)
        assertEquals("Temperature below 35°", rule.summary(Conventions.US))
        assertEquals("Temperature below 1.7°", rule.summary(uk))
    }

    @Test
    fun `a reading just past the threshold never reads as equal to it`() {
        // 1.9 °C (35.4 °F) against a 2 °C rule: both round to "2°".
        val rule = AlertRule("r", temperature, Comparison.BELOW, temperature.toCanonical(2.0, uk))
        val snapshot = WeatherSnapshot("London", 35.4, null, null, null, null, null)
        val decision = AlertEvaluator.evaluate(rule, WeatherContext(snapshot, Instant.EPOCH, conventions = uk))
        assertTrue(decision.fired)
        assertEquals("Temperature is 1.9°, below your 2.0°", decision.reason)
    }

    @Test
    fun `every subject, in every prepared region's units, reads back exactly what was typed`() {
        val subjects = AlertSubject.current + AlertSubject.forecast
        val conventions = Regions.prepared.map { it.conventions }.toSet() + Conventions.US
        for (c in conventions) {
            for (subject in subjects) {
                for (typed in listOf(-40, -5, 0, 1, 2, 7, 10, 25, 35, 50, 100)) {
                    val stored = subject.toCanonical(typed.toDouble(), c)
                    assertEquals(typed.toDouble(), subject.fromCanonical(stored, c), 1e-9, "${subject.key} in $c")
                    // What the rule list shows is the number the user typed.
                    val shown = subject.formatThreshold(stored, c)
                    assertEquals(typed.toDouble(), leadingNumber(shown), 1e-9, "$shown for $typed ${subject.key}")
                }
            }
        }
    }

    @Test
    fun `a rule set in other units never reads on the wrong side of its threshold`() {
        // 35 °F is 1.67 °C; 34.9 °F (1.61 °C) fires BELOW. "is 2°, below
        // your 1.7°" was the bug (roborev 5408).
        val rule = AlertRule("r", temperature, Comparison.BELOW, 35.0)
        val snapshot = WeatherSnapshot("London", 34.9, null, null, null, null, null)
        val reason = AlertEvaluator.evaluate(rule, WeatherContext(snapshot, Instant.EPOCH, conventions = uk)).reason
        assertEquals("Temperature is 1.6°, below your 1.7°", reason)
    }

    @Test
    fun `a fired notification's numbers always sit on the firing side of each other`() {
        // Every pairing of the unit a rule was SET in with the unit it is
        // READ in — the cross-unit case is where a threshold gains a decimal.
        val all = Regions.prepared.map { it.conventions }.toSet() + Conventions.US
        for (setIn in all) for (c in all) for (subject in AlertSubject.current) {
            for (threshold in listOf(0.0, 1.0, 2.0, 10.0, 30.0, 35.0, 1013.0)) {
                val stored = subject.toCanonical(threshold, setIn)
                // Readings a hair either side, in canonical units.
                for (delta in listOf(-0.5, -0.06, -0.004, 0.004, 0.06, 0.5)) {
                    val reading = stored + delta
                    for (comparison in Comparison.entries) {
                        if (!comparison.test(reading, stored)) continue
                        val (actual, limit) = subject.formatPair(reading, stored, c)
                        val a = leadingNumber(actual)
                        val l = leadingNumber(limit)
                        val consistent = if (comparison == Comparison.BELOW) a < l else a > l
                        assertTrue(consistent, "${subject.key} $comparison in $c: \"$actual\" vs \"$limit\"")
                    }
                }
            }
        }
    }

    private fun leadingNumber(text: String): Double = Regex("""-?\d+(\.\d+)?""").find(text)!!.value.toDouble()
}
