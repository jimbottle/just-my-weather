package io.raylytics.justmyweather.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewConfigCodecTest {
    @Test
    fun `round-trips a config through encode then decode`() {
        val original =
            ViewConfig.DEFAULT
                .toggle(WeatherField.WIND)
                .relabel(WeatherField.TEMPERATURE, "Temp")
                .moveUp(2)
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(original))
        assertEquals(original, restored)
    }

    @Test
    fun `round-trips the chosen density`() {
        val original = ViewConfig.DEFAULT.setDensity(Density.COMPACT)
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(original))
        assertEquals(Density.COMPACT, restored.density)
    }

    @Test
    fun `round-trips the default view mode`() {
        val original = ViewConfig.DEFAULT.setDefaultMode(ViewMode.DAILY)
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(original))
        assertEquals(ViewMode.DAILY, restored.defaultMode)
    }

    @Test
    fun `a config saved before view modes existed opens on the shipped default`() {
        val raw = """{"density":"comfortable","items":[{"key":"temperature","visible":true}]}"""
        assertEquals(ViewMode.DEFAULT, ViewConfigCodec.decode(raw).defaultMode)
    }

    @Test
    fun `round-trips the daily style and both layouts independently`() {
        val original =
            ViewConfig.DEFAULT
                .setDailyStyle(DailyStyle.HALF_DAY)
                .setDailyLayout(ForecastLayout.COLUMN)
                .setHourlyLayout(ForecastLayout.ROW)
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(original))
        assertEquals(DailyStyle.HALF_DAY, restored.dailyStyle)
        assertEquals(ForecastLayout.COLUMN, restored.dailyLayout)
        // The two layouts persist separately — stacking daily must not stack hourly.
        assertEquals(ForecastLayout.ROW, restored.hourlyLayout)
        val flipped = ViewConfigCodec.decode(ViewConfigCodec.encode(original.setHourlyLayout(ForecastLayout.COLUMN)))
        assertEquals(ForecastLayout.COLUMN, flipped.hourlyLayout)
    }

    @Test
    fun `a config saved before layout options existed gets the defaults, and unknown keys fall back`() {
        val legacy = """{"mode":"daily","items":[{"key":"temperature","visible":true}]}"""
        assertEquals(DailyStyle.DEFAULT, ViewConfigCodec.decode(legacy).dailyStyle)
        assertEquals(ForecastLayout.DEFAULT, ViewConfigCodec.decode(legacy).dailyLayout)
        assertEquals(ForecastLayout.DEFAULT, ViewConfigCodec.decode(legacy).hourlyLayout)
        val unknown =
            """{"dailyStyle":"spiral","dailyLayout":"3d","hourlyLayout":"4d",
               "items":[{"key":"temperature","visible":true}]}"""
        assertEquals(DailyStyle.DEFAULT, ViewConfigCodec.decode(unknown).dailyStyle)
        assertEquals(ForecastLayout.DEFAULT, ViewConfigCodec.decode(unknown).dailyLayout)
        assertEquals(ForecastLayout.DEFAULT, ViewConfigCodec.decode(unknown).hourlyLayout)
    }

    @Test
    fun `an unknown mode key falls back to the shipped default`() {
        val raw = """{"mode":"biweekly","items":[{"key":"temperature","visible":true}]}"""
        assertEquals(ViewMode.DEFAULT, ViewConfigCodec.decode(raw).defaultMode)
    }

    @Test
    fun `a config saved before density existed decodes at the default density`() {
        // The legacy on-disk shape: a bare array of settings, no density wrapper.
        val raw = """[{"key":"temperature","visible":true},{"key":"conditions","visible":true}]"""
        val config = ViewConfigCodec.decode(raw)
        assertEquals(Density.DEFAULT, config.density)
        // …and the field settings still come through.
        assertEquals(WeatherField.TEMPERATURE, config.items.first().field)
    }

    @Test
    fun `an unknown density key falls back to the default`() {
        val raw = """{"density":"holographic","items":[{"key":"temperature","visible":true}]}"""
        assertEquals(Density.DEFAULT, ViewConfigCodec.decode(raw).density)
    }

    @Test
    fun `absent or corrupt data decodes to the default`() {
        assertEquals(ViewConfig.DEFAULT, ViewConfigCodec.decode(null))
        assertEquals(ViewConfig.DEFAULT, ViewConfigCodec.decode(""))
        assertEquals(ViewConfig.DEFAULT, ViewConfigCodec.decode("{ not json"))
    }

    @Test
    fun `a valid but empty or foreign object decodes to the default, not an all-hidden glance`() {
        // ignoreUnknownKeys would otherwise accept these as a config with no
        // items, normalizing to every field hidden (hero "—", no rows).
        assertEquals(ViewConfig.DEFAULT, ViewConfigCodec.decode("{}"))
        assertEquals(ViewConfig.DEFAULT, ViewConfigCodec.decode("""{"version":2,"theme":"dark"}"""))
        assertEquals(ViewConfig.DEFAULT, ViewConfigCodec.decode("[]"))
    }

    @Test
    fun `decode drops unknown field keys and fills in missing ones`() {
        // A config saved by a future/older build: one unknown key, and only
        // wind among the known fields.
        val raw = """[{"key":"wind","visible":true},{"key":"humidity","visible":true}]"""
        val config = ViewConfigCodec.decode(raw)
        // Unknown "humidity" dropped; all real fields present.
        assertEquals(WeatherField.entries.size, config.items.size)
        assertEquals(WeatherField.WIND, config.items.first().field)
        assertEquals(emptyList<WeatherField>(), config.items.map { it.field } - WeatherField.entries.toSet())
    }

    @Test
    fun `alert banner position round-trips and defaults for older configs`() {
        // The Maestro flow deliberately does not assert which chip is selected
        // (Compose chip selection isn't reliably readable there), so the
        // persistence promise is kept here instead.
        val moved = ViewConfig.DEFAULT.setAlertBannerPosition(AlertBannerPosition.BOTTOM)
        assertEquals(
            AlertBannerPosition.BOTTOM,
            ViewConfigCodec.decode(ViewConfigCodec.encode(moved)).alertBannerPosition,
        )
        // A config written before the banner existed must default to TOP, not
        // fail to decode and reset every other choice with it.
        val legacy = ViewConfigCodec.encode(ViewConfig.DEFAULT).replace(
            """"alertBannerPosition":"top",""",
            "",
        )
        assertEquals(AlertBannerPosition.TOP, ViewConfigCodec.decode(legacy).alertBannerPosition)
    }
}
