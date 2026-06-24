package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AlertEvaluatorTest {
    private fun snapshot(
        temp: Double? = 50.0,
        wind: Double? = 5.0,
    ) = WeatherSnapshot(
        locationLabel = "Test, ST",
        temperatureF = temp,
        conditions = "Clear",
        windMph = wind,
        precipitationIn = 0.0,
        pressureInHg = 29.92,
        observedAt = Instant.parse("2026-06-24T18:00:00Z"),
    )

    private fun fires(
        field: WeatherField,
        comparison: Comparison,
        threshold: Double,
        temp: Double? = 50.0,
        wind: Double? = 5.0,
    ) = AlertEvaluator.evaluate(AlertRule("r", field, comparison, threshold), snapshot(temp, wind))

    @Test
    fun `below fires when the reading is under the threshold`() {
        val decision = fires(WeatherField.TEMPERATURE, Comparison.BELOW, 40.0, temp = 35.0)
        assertTrue(decision.fired)
        assertEquals(35.0, decision.value)
        assertTrue(decision.reason.contains("below"))
    }

    @Test
    fun `below does not fire at or above the threshold`() {
        assertFalse(fires(WeatherField.TEMPERATURE, Comparison.BELOW, 40.0, temp = 40.0).fired)
        assertFalse(fires(WeatherField.TEMPERATURE, Comparison.BELOW, 40.0, temp = 45.0).fired)
    }

    @Test
    fun `above fires only strictly above the threshold`() {
        assertTrue(fires(WeatherField.WIND, Comparison.ABOVE, 20.0, wind = 25.0).fired)
        assertFalse(fires(WeatherField.WIND, Comparison.ABOVE, 20.0, wind = 20.0).fired)
    }

    @Test
    fun `a missing reading never fires`() {
        val decision = fires(WeatherField.TEMPERATURE, Comparison.BELOW, 40.0, temp = null)
        assertFalse(decision.fired)
        assertEquals(null, decision.value)
    }

    @Test
    fun `rule summary reads naturally with the field unit`() {
        assertEquals("Temperature below 32°", AlertRule("r", WeatherField.TEMPERATURE, Comparison.BELOW, 32.0).summary)
        assertEquals("Wind above 20 mph", AlertRule("r", WeatherField.WIND, Comparison.ABOVE, 20.0).summary)
    }
}
