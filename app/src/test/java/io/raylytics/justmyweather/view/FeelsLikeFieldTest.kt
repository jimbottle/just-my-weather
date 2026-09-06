package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeelsLikeFieldTest {
    private val snapshot = WeatherSnapshot("Test", 93.0, "Sunny", 5.0, 0.0, 29.9, observedAt = null, feelsLikeF = 101.4)

    @Test
    fun `feels like is a degree reading that ships as one cell and is alertable`() {
        assertEquals("101°", WeatherField.FEELS_LIKE.format(snapshot))
        assertNull(WeatherField.FEELS_LIKE.format(snapshot.copy(feelsLikeF = null)))
        assertEquals(101.4, WeatherField.FEELS_LIKE.numericValue(snapshot))
        assertEquals(ModuleSize.CELL, WeatherField.FEELS_LIKE.defaultSize)
        assertTrue(WeatherField.FEELS_LIKE.isNumeric)
        assertTrue(WeatherField.FEELS_LIKE in WeatherField.alertable)
        // The hourly forecast carries no apparent temperature.
        assertFalse(WeatherField.FEELS_LIKE.isForecastable)
    }

    @Test
    fun `feels like is in the catalog, hidden by default, and persists by key`() {
        assertEquals(ModuleKey.Reading(WeatherField.FEELS_LIKE), ModuleKey.byKey("feels_like"))
        assertFalse(ViewConfig.DEFAULT.shows(ModuleKey.Reading(WeatherField.FEELS_LIKE)))
    }
}
