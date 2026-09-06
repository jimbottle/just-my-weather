package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ViewConfigTest {
    /** Shorthand: every reading module is `ModuleKey.Reading(field)`, and
     * spelling that out inline costs more width than it earns in clarity. */
    private fun reading(field: WeatherField) = ModuleKey.Reading(field)

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
        assertEquals("Temperature", ModuleSetting(reading(WeatherField.TEMPERATURE), true).label)
        assertEquals("Temp", ModuleSetting(reading(WeatherField.TEMPERATURE), true, "Temp").label)
        // Blank custom label is treated as "use the default", not an empty label.
        assertEquals("Temperature", ModuleSetting(reading(WeatherField.TEMPERATURE), true, "  ").label)
    }

    @Test
    fun `default config reproduces the calm glance`() {
        val visible = ViewConfig.DEFAULT.visible.map { it.module }
        assertEquals(
            listOf(reading(WeatherField.TEMPERATURE), reading(WeatherField.CONDITIONS), ModuleKey.Forecast),
            visible,
        )
    }

    @Test
    fun `toggle flips visibility for one field only`() {
        val after = ViewConfig.DEFAULT.toggle(reading(WeatherField.WIND))
        assertTrue(after.items.first { it.module == reading(WeatherField.WIND) }.visible)
        // Temperature untouched.
        assertTrue(after.items.first { it.module == reading(WeatherField.TEMPERATURE) }.visible)
    }

    @Test
    fun `moveUp reorders and is a no-op at the top`() {
        // Catalog order opens temperature, feels-like; moving the second up
        // swaps the two.
        val moved = ViewConfig.DEFAULT.moveUp(1)
        assertEquals(reading(WeatherField.FEELS_LIKE), moved.items[0].module)
        assertEquals(reading(WeatherField.TEMPERATURE), moved.items[1].module)
        // Out-of-range is ignored, not a crash.
        assertEquals(ViewConfig.DEFAULT, ViewConfig.DEFAULT.moveUp(0))
    }

    @Test
    fun `normalized appends missing fields as hidden and drops duplicates`() {
        val partial = listOf(ModuleSetting(reading(WeatherField.WIND), visible = true))
        val config = ViewConfig.normalized(partial)
        assertEquals(ModuleKey.catalog.size, config.items.size)
        assertEquals(reading(WeatherField.WIND), config.items.first().module)
        assertTrue(config.items.first().visible)
        // Everything else present and hidden.
        assertFalse(config.items.first { it.module == reading(WeatherField.TEMPERATURE) }.visible)
    }

    @Test
    fun `render projects the visible modules, in order, with their sizes`() {
        // temp, conditions, wind and the forecast visible, in catalog order
        val config = ViewConfig.DEFAULT.toggle(reading(WeatherField.WIND))
        val rendered = config.render(snapshot)
        assertEquals(
            listOf("72°", "Mostly Clear", "Calm"),
            rendered.modules.mapNotNull { (it.content as? ModuleContent.Reading)?.text },
        )
        assertEquals(
            listOf(ModuleSize(4, 2), ModuleSize(2, 1), ModuleSize(1, 1), ModuleSize(4, 4)),
            rendered.modules.map { it.size },
        )
        // The forecast is a module like the others: last in a fresh config,
        // carrying the framing and data it was handed rather than a string.
        val forecast = rendered.modules.last()
        assertEquals(ModuleKey.Forecast, forecast.module)
        assertEquals(ForecastMode.DEFAULT, (forecast.content as ModuleContent.Forecast).mode)
    }

    @Test
    fun `render shows an em-dash for an enabled but empty module`() {
        val rendered = ViewConfig.DEFAULT.render(snapshot.copy(temperatureF = null))
        assertEquals("—", (rendered.modules.first().content as ModuleContent.Reading).text)
    }

    @Test
    fun `the sun module carries the days rather than a formatted string`() {
        // The tile decides how much of the table it can draw at its width, so
        // the projection hands it the days themselves — flattening here is
        // exactly what would lose the two-day distinction.
        val days = listOf(SunDay(LocalDate.of(2026, 6, 24), null, null))
        val config = ViewConfig.DEFAULT.toggle(ModuleKey.Sun)
        val zone = ZoneId.of("America/New_York")
        val sun = config.render(snapshot, days, zone).modules.first { it.module == ModuleKey.Sun }
        // The zone travels WITH the days: they are instants, and the same
        // sunrise formats as a different clock time depending where you ask.
        assertEquals(ModuleContent.Sun(days, zone), sun.content)
        // Full width and two rows by default: that is the size the table needs.
        assertEquals(ModuleSize(4, 2), sun.size)
    }

    @Test
    fun `fields ship at their default sizes and resize changes one field only`() {
        assertEquals(
            ModuleSize(4, 2),
            ViewConfig.DEFAULT.items.first {
                it.module == reading(WeatherField.TEMPERATURE)
            }.size,
        )
        // The hero down to a single cell: the ask that started the lattice.
        val resized = ViewConfig.DEFAULT.resize(reading(WeatherField.TEMPERATURE), ModuleSize.CELL)
        assertEquals(
            ModuleSize.CELL,
            resized.items.first { it.module == reading(WeatherField.TEMPERATURE) }.size,
        )
        assertEquals(
            ModuleSize(2, 1),
            resized.items.first { it.module == reading(WeatherField.CONDITIONS) }.size,
        )
    }

    @Test
    fun `resize stops at the module's own minimum and at the lattice`() {
        // Conditions is prose and will not go below two cells wide, however
        // small a rectangle the finger draws.
        val squeezed = ViewConfig.DEFAULT.resize(reading(WeatherField.CONDITIONS), ModuleSize(1, 1))
        assertEquals(ModuleSize(2, 1), squeezed.items.first { it.module == reading(WeatherField.CONDITIONS) }.size)
        // Sun needs its pair side by side.
        val sun = ViewConfig.DEFAULT.resize(ModuleKey.Sun, ModuleSize(1, 3))
        assertEquals(ModuleSize(2, 3), sun.items.first { it.module == ModuleKey.Sun }.size)
        // And nothing outgrows the grid.
        val huge = ViewConfig.DEFAULT.resize(reading(WeatherField.WIND), ModuleSize(9, 9))
        assertEquals(
            ModuleSize(ModuleSize.COLUMNS, ModuleSize.MAX_ROWS),
            huge.items.first { it.module == reading(WeatherField.WIND) }.size,
        )
    }

    @Test
    fun `every module ships at or above its own minimum`() {
        // A default below the floor would be a tile the codec clamps the
        // moment it is written back — a config that cannot round-trip.
        ModuleKey.catalog.forEach { module ->
            assertTrue(module.defaultSize.fits(module.minSize)) {
                "${module.key} default ${module.defaultSize} vs min ${module.minSize}"
            }
        }
    }

    @Test
    fun `moveVisible lands a field at the requested visible slot`() {
        // temp, conditions, wind, forecast visible; precipitation, pressure
        // and sun hidden between them.
        val config = ViewConfig.DEFAULT.toggle(reading(WeatherField.WIND))
        val moved = config.moveVisible(reading(WeatherField.TEMPERATURE), 2)
        assertEquals(
            listOf(
                reading(WeatherField.CONDITIONS),
                reading(WeatherField.WIND),
                reading(WeatherField.TEMPERATURE),
                ModuleKey.Forecast,
            ),
            moved.visible.map { it.module },
        )
        // Hidden fields are still present exactly once each.
        assertEquals(ModuleKey.catalog.size, moved.items.size)
    }

    @Test
    fun `moveVisible clamps out-of-range targets and ignores hidden fields`() {
        val config = ViewConfig.DEFAULT // temp, conditions, forecast visible
        // Past the end clamps to the last slot.
        val toEnd = config.moveVisible(reading(WeatherField.TEMPERATURE), 99)
        assertEquals(
            listOf(reading(WeatherField.CONDITIONS), ModuleKey.Forecast, reading(WeatherField.TEMPERATURE)),
            toEnd.visible.map { it.module },
        )
        // A hidden field has no slot on the grid to move to.
        assertEquals(config, config.moveVisible(reading(WeatherField.PRESSURE), 0))
        // Same slot is a no-op.
        assertEquals(config, config.moveVisible(reading(WeatherField.TEMPERATURE), 0))
    }

    @Test
    fun `the shipped default shows an hourly forecast, and hiding it keeps the framing`() {
        assertTrue(ViewConfig.DEFAULT.shows(ModuleKey.Forecast))
        assertEquals(ForecastMode.HOURLY, ViewConfig.DEFAULT.defaultForecastMode)
        // Turning the module off is not the same as forgetting the choice: it
        // must reopen the way the user left it.
        val hidden = ViewConfig.DEFAULT.setDefaultForecastMode(ForecastMode.DAILY).toggle(ModuleKey.Forecast)
        assertFalse(hidden.shows(ModuleKey.Forecast))
        assertEquals(ForecastMode.DAILY, hidden.defaultForecastMode)
    }

    @Test
    fun `the forecast is movable and resizable like any module, down to two by two`() {
        // The ask: every tile carries the same interaction. So the forecast
        // moves in the visible order and resizes, floored where a forecast
        // stops being one (two hour tiles under a header).
        val moved = ViewConfig.DEFAULT.moveVisible(ModuleKey.Forecast, 0)
        assertEquals(ModuleKey.Forecast, moved.visible.first().module)
        val squeezed = ViewConfig.DEFAULT.resize(ModuleKey.Forecast, ModuleSize.CELL)
        assertEquals(ModuleSize(2, 2), squeezed.items.first { it.module == ModuleKey.Forecast }.size)
    }

    @Test
    fun `hourly hours ship at a day and clamp to the range`() {
        assertEquals(24, ViewConfig.DEFAULT.hourlyHours)
        assertEquals(168, ViewConfig.DEFAULT.setHourlyHours(999).hourlyHours)
        assertEquals(4, ViewConfig.DEFAULT.setHourlyHours(0).hourlyHours)
        assertEquals(48, ViewConfig.DEFAULT.setHourlyHours(48).hourlyHours)
        // …and the rendered forecast carries the setting to the tile.
        val rendered = ViewConfig.DEFAULT.setHourlyHours(48).render(snapshot)
        val forecast = rendered.modules.last().content as ModuleContent.Forecast
        assertEquals(48, forecast.hourlyHours)
    }

    @Test
    fun `forecast elements ship as chance and conditions, toggle one at a time, and reach the tile`() {
        assertEquals(
            setOf(ForecastElement.PRECIP_CHANCE, ForecastElement.CONDITIONS),
            ViewConfig.DEFAULT.forecastElements,
        )
        val bare =
            ViewConfig.DEFAULT
                .toggleForecastElement(ForecastElement.CONDITIONS)
                .toggleForecastElement(ForecastElement.PRECIP_CHANCE)
        assertEquals(emptySet<ForecastElement>(), bare.forecastElements)
        val windy = bare.toggleForecastElement(ForecastElement.WIND)
        assertEquals(setOf(ForecastElement.WIND), windy.forecastElements)
        val forecast = windy.render(snapshot).modules.last().content as ModuleContent.Forecast
        assertEquals(setOf(ForecastElement.WIND), forecast.elements)
    }

    @Test
    fun `tap-for-details ships on and is a plain switch`() {
        assertTrue(ViewConfig.DEFAULT.tapForDetails)
        assertFalse(ViewConfig.DEFAULT.setTapForDetails(false).tapForDetails)
    }

    @Test
    fun `the default config ships at the comfortable density`() {
        assertEquals(Density.COMFORTABLE, ViewConfig.DEFAULT.density)
    }

    @Test
    fun `field edits preserve the chosen density`() {
        // Density and field layout are independent axes — editing one must not
        // reset the other.
        val config = ViewConfig.DEFAULT.setDensity(Density.SPACIOUS).toggle(reading(WeatherField.WIND)).moveUp(2)
        assertEquals(Density.SPACIOUS, config.density)
    }
}
