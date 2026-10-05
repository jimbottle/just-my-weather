package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
    fun `previous data is not about another place`() {
        val fetched = data(38.25, -85.76)
        assertFalse(fetched.isAbout(WeatherLocation(47.61, -122.33, "Seattle")))
        assertFalse(fetched.isAbout(WeatherLocation(38.25, -85.85, "Across town, past the gate")))
    }
}
