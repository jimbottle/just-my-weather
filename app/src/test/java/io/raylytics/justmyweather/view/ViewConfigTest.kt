package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ViewConfigTest {
    private val snapshot =
        WeatherSnapshot(
            locationLabel = "Test, ST",
            temperatureF = 71.6,
            conditions = "Mostly Clear",
            windMph = 0.4,
            precipitationIn = 0.0,
            pressureInHg = 29.92,
            observedAt = Instant.parse("2026-06-24T18:00:00Z"),
        )

    @Test
    fun `fields format their values, rounding and labelling sensibly`() {
        assertEquals("72°", WeatherField.TEMPERATURE.format(snapshot))
        assertEquals("Mostly Clear", WeatherField.CONDITIONS.format(snapshot))
        assertEquals("Calm", WeatherField.WIND.format(snapshot)) // < 1 mph
        assertEquals("0.00 in", WeatherField.PRECIPITATION.format(snapshot))
        assertEquals("29.92 inHg", WeatherField.PRESSURE.format(snapshot))
    }

    @Test
    fun `a missing value formats as null, not a fabricated number`() {
        val empty = snapshot.copy(temperatureF = null, conditions = null)
        assertNull(WeatherField.TEMPERATURE.format(empty))
        assertNull(WeatherField.CONDITIONS.format(empty))
    }

    @Test
    fun `effective label falls back to the field default`() {
        assertEquals("Temperature", FieldSetting(WeatherField.TEMPERATURE, true).label)
        assertEquals("Temp", FieldSetting(WeatherField.TEMPERATURE, true, "Temp").label)
        // Blank custom label is treated as "use the default", not an empty label.
        assertEquals("Temperature", FieldSetting(WeatherField.TEMPERATURE, true, "  ").label)
    }

    @Test
    fun `default config reproduces the calm glance`() {
        val visible = ViewConfig.DEFAULT.visible.map { it.field }
        assertEquals(listOf(WeatherField.TEMPERATURE, WeatherField.CONDITIONS), visible)
    }

    @Test
    fun `toggle flips visibility for one field only`() {
        val after = ViewConfig.DEFAULT.toggle(WeatherField.WIND)
        assertTrue(after.items.first { it.field == WeatherField.WIND }.visible)
        // Temperature untouched.
        assertTrue(after.items.first { it.field == WeatherField.TEMPERATURE }.visible)
    }

    @Test
    fun `moveUp reorders and is a no-op at the top`() {
        val moved = ViewConfig.DEFAULT.moveUp(1)
        assertEquals(WeatherField.CONDITIONS, moved.items[0].field)
        assertEquals(WeatherField.TEMPERATURE, moved.items[1].field)
        // Out-of-range is ignored, not a crash.
        assertEquals(ViewConfig.DEFAULT, ViewConfig.DEFAULT.moveUp(0))
    }

    @Test
    fun `normalized appends missing fields as hidden and drops duplicates`() {
        val partial = listOf(FieldSetting(WeatherField.WIND, visible = true))
        val config = ViewConfig.normalized(partial)
        assertEquals(WeatherField.entries.size, config.items.size)
        assertEquals(WeatherField.WIND, config.items.first().field)
        assertTrue(config.items.first().visible)
        // Everything else present and hidden.
        assertFalse(config.items.first { it.field == WeatherField.TEMPERATURE }.visible)
    }

    @Test
    fun `render puts the first visible field as hero and the rest as rows`() {
        val config = ViewConfig.DEFAULT.toggle(WeatherField.WIND) // temp, conditions, wind visible
        val rendered = config.render(snapshot)
        assertEquals("72°", rendered.hero?.value)
        assertEquals(listOf("Mostly Clear", "Calm"), rendered.rows.map { it.value })
    }

    @Test
    fun `render shows an em-dash for an enabled but empty field`() {
        val rendered = ViewConfig.DEFAULT.render(snapshot.copy(temperatureF = null))
        assertEquals("—", rendered.hero?.value)
    }
}
