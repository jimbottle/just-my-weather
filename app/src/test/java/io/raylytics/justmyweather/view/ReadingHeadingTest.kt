package io.raylytics.justmyweather.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReadingHeadingTest {
    @Test
    fun `a station reading is observed and a forecast hour never is`() {
        assertEquals("Observed 12:40 PM", ReadingHeading.of(fromForecast = false, time = "12:40 PM"))
        assertEquals("Observed", ReadingHeading.of(fromForecast = false, time = null))
        assertEquals("Forecast for 1:00 PM", ReadingHeading.of(fromForecast = true, time = "1:00 PM"))
        assertEquals("Forecast", ReadingHeading.of(fromForecast = true, time = null))
    }
}
