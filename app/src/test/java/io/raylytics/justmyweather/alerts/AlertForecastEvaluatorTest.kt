package io.raylytics.justmyweather.alerts

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The forecast-window path of the evaluator: BELOW watches the window's low,
 * ABOVE its high, and a window with no forecast data never fires. "now" is
 * pinned and the zone is UTC so the overnight band (local 20:00–08:00) is
 * deterministic.
 */
class AlertForecastEvaluatorTest {
    private val now = Instant.parse("2026-01-15T18:00:00Z") // 18:00 UTC, daytime
    private val zone = ZoneId.of("UTC")

    // The current observation is mild; the interesting values live in the forecast.
    private val mildNow =
        WeatherSnapshot(
            locationLabel = "Test, ST",
            temperatureF = 50.0,
            conditions = "Clear",
            windMph = 5.0,
            precipitationIn = 0.0,
            pressureInHg = 29.92,
            observedAt = now,
        )

    private fun at(hoursAhead: Long, temp: Double? = null, wind: Double? = null) =
        ForecastPoint(now.plus(hoursAhead, ChronoUnit.HOURS), temperatureF = temp, windMph = wind)

    private fun evaluate(rule: AlertRule, forecast: List<ForecastPoint>) =
        AlertEvaluator.evaluate(rule, WeatherContext(mildNow, now, forecast, zone))

    @Test
    fun `overnight low below threshold fires on the coldest overnight hour`() {
        // Hours ahead from 18:00 UTC: +2=20:00 (night), +9=03:00 (night), +20=14:00 (day).
        val forecast =
            listOf(
                // 20:00, night, mild
                at(2, temp = 48.0),
                // 03:00, night, the cold dip
                at(9, temp = 30.0),
                // next afternoon, ignored (daytime)
                at(20, temp = 60.0),
            )
        val rule = AlertRule("r", WeatherField.TEMPERATURE, Comparison.BELOW, 35.0, window = AlertWindow.OVERNIGHT)
        val decision = evaluate(rule, forecast)
        assertTrue(decision.fired)
        assertEquals(30.0, decision.value)
        assertTrue(decision.reason.contains("overnight"))
        assertTrue(decision.reason.contains("below"))
    }

    @Test
    fun `overnight ignores daytime hours even when they are colder`() {
        // The only sub-threshold reading is at a daytime hour, so it must not fire.
        val forecast =
            listOf(
                // 20:00, night, above threshold
                at(2, temp = 50.0),
                // next-day 14:00, daytime — excluded
                at(20, temp = 28.0),
            )
        val rule = AlertRule("r", WeatherField.TEMPERATURE, Comparison.BELOW, 35.0, window = AlertWindow.OVERNIGHT)
        assertFalse(evaluate(rule, forecast).fired)
    }

    @Test
    fun `next 6h wind above threshold fires on the windiest hour in range`() {
        val forecast =
            listOf(
                at(1, wind = 10.0),
                // within 6h, the gust
                at(5, wind = 32.0),
                // beyond 6h, ignored
                at(8, wind = 40.0),
            )
        val rule = AlertRule("r", WeatherField.WIND, Comparison.ABOVE, 30.0, window = AlertWindow.NEXT_6H)
        val decision = evaluate(rule, forecast)
        assertTrue(decision.fired)
        assertEquals(32.0, decision.value)
    }

    @Test
    fun `a hour beyond the window does not fire`() {
        val forecast = listOf(at(10, temp = 20.0)) // 10h ahead, outside next-6h
        val rule = AlertRule("r", WeatherField.TEMPERATURE, Comparison.BELOW, 35.0, window = AlertWindow.NEXT_6H)
        assertFalse(evaluate(rule, forecast).fired)
    }

    @Test
    fun `no forecast data in the window never fires`() {
        val rule = AlertRule("r", WeatherField.TEMPERATURE, Comparison.BELOW, 35.0, window = AlertWindow.NEXT_12H)
        val decision = evaluate(rule, emptyList())
        assertFalse(decision.fired)
        assertEquals(null, decision.value)
        assertTrue(decision.reason.contains("No"))
    }

    @Test
    fun `forecast summary appends the window phrase`() {
        val rule = AlertRule("r", WeatherField.TEMPERATURE, Comparison.BELOW, 35.0, window = AlertWindow.OVERNIGHT)
        assertEquals("Temperature below 35° overnight", rule.summary)
    }
}
