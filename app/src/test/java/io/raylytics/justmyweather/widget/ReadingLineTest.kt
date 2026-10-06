package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.view.Conventions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ReadingLineTest {
    private val zone = ZoneId.of("Europe/London")
    private val hour = Instant.parse("2026-10-06T19:00:00Z") // 8:00 PM in London
    private val forecast =
        WeatherSnapshot("London", 63.0, "Cloudy", 5.0, null, 30.0, observedAt = hour, fromForecast = true)

    @Test
    fun `a current forecast hour names its hour and its source, never an age`() {
        val now = hour.plusSeconds(20 * 60)
        assertEquals("Forecast for 8:00 PM · MET Norway", readingLine(forecast, zone, now, wide = true, Conventions.US))
        assertEquals("Forecast for 8:00 PM", readingLine(forecast, zone, now, wide = false, Conventions.US))
    }

    @Test
    fun `a forecast carried past its hour by failed ticks says how old it is`() {
        val now = hour.plusSeconds(5 * 3600)
        assertEquals(
            "Forecast for 8:00 PM · 5 hr ago · MET Norway",
            readingLine(forecast, zone, now, wide = true, Conventions.US),
        )
        assertEquals("Forecast · 5 hr ago", readingLine(forecast, zone, now, wide = false, Conventions.US))
    }

    @Test
    fun `an observation is unchanged - its time and its age`() {
        val observed = forecast.copy(fromForecast = false, observedAt = hour.plusSeconds(40 * 60))
        val now = hour.plusSeconds(52 * 60)
        assertEquals("Observed 8:40 PM · 12 min ago", readingLine(observed, zone, now, wide = true, Conventions.US))
        assertEquals("Observed 12 min ago", readingLine(observed, zone, now, wide = false, Conventions.US))
        assertEquals("Observed", readingLine(observed.copy(observedAt = null), zone, now, wide = true, Conventions.US))
    }
}
