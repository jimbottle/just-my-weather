package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class WidgetDataTest {
    private fun data(latitude: Double, longitude: Double) =
        WidgetData(WeatherLocation(latitude, longitude, "Here"), snapshot = null, fetchedAt = Instant.EPOCH)

    @Test
    fun `previous data is about the same place within a grid cell's drift`() {
        val fetched = data(38.25, -85.76)
        assertTrue(fetched.isAbout(WeatherLocation(38.25, -85.76, "Here")))
        assertTrue(fetched.isAbout(WeatherLocation(38.29, -85.72, "A drifted fix")))
    }

    @Test
    fun `a forecast is fresh within the window, measured from its own fetch`() {
        val fetched = Instant.parse("2026-10-05T14:00:00Z")
        val data = data(38.25, -85.76).copy(forecastFetchedAt = fetched, fetchedAt = fetched.plusSeconds(3600))
        val window = Duration.ofMinutes(30)
        assertTrue(data.isForecastFresh(fetched.plusSeconds(600), window))
        assertFalse(data.isForecastFresh(fetched.plusSeconds(3600), window), "an hour old is past a 30 min window")
        assertFalse(data.isForecastFresh(fetched.minusSeconds(60), window), "a clock that went backwards")
        assertFalse(data(38.25, -85.76).isForecastFresh(fetched, window), "never fetched")
    }

    @Test
    fun `previous data is not about another place`() {
        val fetched = data(38.25, -85.76)
        assertFalse(fetched.isAbout(WeatherLocation(47.61, -122.33, "Seattle")))
        assertFalse(fetched.isAbout(WeatherLocation(38.25, -85.85, "Across town, past the gate")))
    }
}
