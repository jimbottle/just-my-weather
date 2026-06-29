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
 * ABOVE its high, chance-of-rain rides the same max-over-window path, and a
 * window with no forecast data never fires. "now" is pinned and the zone is UTC
 * so the overnight band (local 20:00–08:00) is deterministic.
 */
class AlertForecastEvaluatorTest {
    private val now = Instant.parse("2026-01-15T18:00:00Z") // 18:00 UTC, daytime
    private val zone = ZoneId.of("UTC")

    private val temp = AlertSubject.Field(WeatherField.TEMPERATURE)
    private val wind = AlertSubject.Field(WeatherField.WIND)
    private val rain = AlertSubject.PrecipChance

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

    private fun at(hoursAhead: Long, temp: Double? = null, wind: Double? = null, precip: Double? = null) =
        ForecastPoint(
            startTime = now.plus(hoursAhead, ChronoUnit.HOURS),
            temperatureF = temp,
            windMph = wind,
            precipProbabilityPercent = precip,
        )

    private fun rule(subject: AlertSubject, comparison: Comparison, threshold: Double, window: AlertWindow) =
        AlertRule("r", subject, comparison, threshold, window = window)

    private fun evaluate(rule: AlertRule, forecast: List<ForecastPoint>) =
        AlertEvaluator.evaluate(rule, WeatherContext(mildNow, now, forecast, zone))

    @Test
    fun `overnight low below threshold fires on the coldest overnight hour`() {
        // Hours ahead from 18:00 UTC: +2 = 20:00 night (mild), +9 = 03:00 night
        // (the cold dip), +20 = 14:00 daytime (ignored).
        val forecast = listOf(at(2, temp = 48.0), at(9, temp = 30.0), at(20, temp = 60.0))
        val decision = evaluate(rule(temp, Comparison.BELOW, 35.0, AlertWindow.OVERNIGHT), forecast)
        assertTrue(decision.fired)
        assertEquals(30.0, decision.value)
        assertTrue(decision.reason.contains("overnight"))
        assertTrue(decision.reason.contains("below"))
    }

    @Test
    fun `overnight ignores daytime hours even when they are colder`() {
        // The only sub-threshold reading (28°) is at a daytime hour (+20 = 14:00),
        // so it's excluded; the night hour (+2 = 20:00) is above threshold.
        val forecast = listOf(at(2, temp = 50.0), at(20, temp = 28.0))
        assertFalse(evaluate(rule(temp, Comparison.BELOW, 35.0, AlertWindow.OVERNIGHT), forecast).fired)
    }

    @Test
    fun `next 6h wind above threshold fires on the windiest hour in range`() {
        // +5h = the 32 mph gust, within 6h; +8h = 40 mph but beyond the window.
        val forecast = listOf(at(1, wind = 10.0), at(5, wind = 32.0), at(8, wind = 40.0))
        val decision = evaluate(rule(wind, Comparison.ABOVE, 30.0, AlertWindow.NEXT_6H), forecast)
        assertTrue(decision.fired)
        assertEquals(32.0, decision.value)
    }

    @Test
    fun `chance of rain above threshold fires on the wettest hour in the window`() {
        // +5h = the 70% wet hour, within 12h; +20h = 90% but beyond the window.
        val forecast = listOf(at(2, precip = 20.0), at(5, precip = 70.0), at(20, precip = 90.0))
        val precipRule = rule(rain, Comparison.ABOVE, 50.0, AlertWindow.NEXT_12H)
        val decision = evaluate(precipRule, forecast)
        assertTrue(decision.fired)
        assertEquals(70.0, decision.value)
        assertTrue(decision.reason.contains("70%"))
        assertEquals("Chance of rain above 50% within 12 hours", precipRule.summary)
    }

    @Test
    fun `chance of rain below threshold does not fire when every hour is wetter`() {
        val forecast = listOf(at(2, precip = 60.0), at(5, precip = 80.0))
        assertFalse(evaluate(rule(rain, Comparison.ABOVE, 90.0, AlertWindow.NEXT_12H), forecast).fired)
    }

    @Test
    fun `a hour beyond the window does not fire`() {
        val forecast = listOf(at(10, temp = 20.0)) // 10h ahead, outside next-6h
        assertFalse(evaluate(rule(temp, Comparison.BELOW, 35.0, AlertWindow.NEXT_6H), forecast).fired)
    }

    @Test
    fun `no forecast data in the window never fires`() {
        val decision = evaluate(rule(temp, Comparison.BELOW, 35.0, AlertWindow.NEXT_12H), emptyList())
        assertFalse(decision.fired)
        assertEquals(null, decision.value)
        assertTrue(decision.reason.contains("No"))
    }

    @Test
    fun `overnight checked late at night stays on the imminent night, not the next one`() {
        // 23:00 UTC: tonight's band is today 20:00 → tomorrow 08:00. A cold hour
        // tomorrow evening belongs to the *next* night and must not fire; a cold
        // hour later tonight must.
        val lateNight = Instant.parse("2026-01-15T23:00:00Z")
        val cold = rule(temp, Comparison.BELOW, 35.0, AlertWindow.OVERNIGHT)

        val nextEveningOnly =
            listOf(ForecastPoint(lateNight.plus(22, ChronoUnit.HOURS), temperatureF = 25.0, windMph = null))
        assertFalse(AlertEvaluator.evaluate(cold, WeatherContext(mildNow, lateNight, nextEveningOnly, zone)).fired)

        val laterTonight =
            listOf(ForecastPoint(lateNight.plus(5, ChronoUnit.HOURS), temperatureF = 25.0, windMph = null))
        assertTrue(AlertEvaluator.evaluate(cold, WeatherContext(mildNow, lateNight, laterTonight, zone)).fired)
    }

    @Test
    fun `forecast summary appends the window phrase`() {
        assertEquals(
            "Temperature below 35° overnight",
            rule(temp, Comparison.BELOW, 35.0, AlertWindow.OVERNIGHT).summary,
        )
    }
}
