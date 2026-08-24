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
    fun `render projects the visible fields, in order, with their spans`() {
        val config = ViewConfig.DEFAULT.toggle(WeatherField.WIND) // temp, conditions, wind visible
        val rendered = config.render(snapshot)
        assertEquals(listOf("72°", "Mostly Clear", "Calm"), rendered.modules.map { it.value })
        assertEquals(
            listOf(ModuleSpan.FULL, ModuleSpan.HALF, ModuleSpan.QUARTER),
            rendered.modules.map { it.span },
        )
    }

    @Test
    fun `render shows an em-dash for an enabled but empty field`() {
        val rendered = ViewConfig.DEFAULT.render(snapshot.copy(temperatureF = null))
        assertEquals("—", rendered.modules.first().value)
    }

    @Test
    fun `fields ship at their default spans and setSpan changes one field only`() {
        assertEquals(ModuleSpan.FULL, ViewConfig.DEFAULT.items.first { it.field == WeatherField.TEMPERATURE }.span)
        val resized = ViewConfig.DEFAULT.setSpan(WeatherField.TEMPERATURE, ModuleSpan.QUARTER)
        assertEquals(ModuleSpan.QUARTER, resized.items.first { it.field == WeatherField.TEMPERATURE }.span)
        assertEquals(ModuleSpan.HALF, resized.items.first { it.field == WeatherField.CONDITIONS }.span)
    }

    @Test
    fun `cycleSpan steps around the size ring`() {
        val once = ViewConfig.DEFAULT.cycleSpan(WeatherField.WIND) // quarter -> half
        assertEquals(ModuleSpan.HALF, once.items.first { it.field == WeatherField.WIND }.span)
        val around =
            once.cycleSpan(WeatherField.WIND) // -> full
                .cycleSpan(WeatherField.WIND) // -> back to quarter
        assertEquals(ModuleSpan.QUARTER, around.items.first { it.field == WeatherField.WIND }.span)
    }

    @Test
    fun `moveVisible lands a field at the requested visible slot`() {
        // temp, conditions, wind visible; precipitation and pressure hidden between them.
        val config = ViewConfig.DEFAULT.toggle(WeatherField.WIND)
        val moved = config.moveVisible(WeatherField.TEMPERATURE, 2)
        assertEquals(
            listOf(WeatherField.CONDITIONS, WeatherField.WIND, WeatherField.TEMPERATURE),
            moved.visible.map { it.field },
        )
        // Hidden fields are still present exactly once each.
        assertEquals(WeatherField.entries.size, moved.items.size)
    }

    @Test
    fun `moveVisible clamps out-of-range targets and ignores hidden fields`() {
        val config = ViewConfig.DEFAULT // temp, conditions visible
        // Past the end clamps to the last slot.
        val toEnd = config.moveVisible(WeatherField.TEMPERATURE, 99)
        assertEquals(
            listOf(WeatherField.CONDITIONS, WeatherField.TEMPERATURE),
            toEnd.visible.map { it.field },
        )
        // A hidden field has no slot on the grid to move to.
        assertEquals(config, config.moveVisible(WeatherField.PRESSURE, 0))
        // Same slot is a no-op.
        assertEquals(config, config.moveVisible(WeatherField.TEMPERATURE, 0))
    }

    @Test
    fun `the default config ships at the comfortable density`() {
        assertEquals(Density.COMFORTABLE, ViewConfig.DEFAULT.density)
    }

    @Test
    fun `field edits preserve the chosen density`() {
        // Density and field layout are independent axes — editing one must not
        // reset the other.
        val config = ViewConfig.DEFAULT.setDensity(Density.SPACIOUS).toggle(WeatherField.WIND).moveUp(2)
        assertEquals(Density.SPACIOUS, config.density)
    }
}
