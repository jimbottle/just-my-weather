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
        assertEquals("Temperature below 36°", rule.summary(Conventions.US))
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
}
