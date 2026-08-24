package io.raylytics.justmyweather.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewConfigCodecTest {
    /** Shorthand: every reading module is `ModuleKey.Reading(field)`, and
     * spelling that out inline costs more width than it earns in clarity. */
    private fun reading(field: WeatherField) = ModuleKey.Reading(field)

    @Test
    fun `round-trips a config through encode then decode`() {
        val original =
            ViewConfig.DEFAULT
                .toggle(reading(WeatherField.WIND))
                .relabel(reading(WeatherField.TEMPERATURE), "Temp")
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
    fun `round-trips whether the forecast shows and which framing it opens on`() {
        val original =
            ViewConfig.DEFAULT
                .setDefaultForecastMode(ForecastMode.DAILY)
                .setDailyStyle(DailyStyle.HALF_DAY)
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(original))
        assertEquals(ForecastMode.DAILY, restored.defaultForecastMode)
        assertEquals(DailyStyle.HALF_DAY, restored.dailyStyle)
        assertTrue(restored.showForecast)

        val hidden = ViewConfigCodec.decode(ViewConfigCodec.encode(original.setShowForecast(false)))
        assertFalse(hidden.showForecast)
        // Hiding the grid must not forget which framing to reopen on.
        assertEquals(ForecastMode.DAILY, hidden.defaultForecastMode)
    }

    @Test
    fun `a legacy view mode splits into show-the-forecast plus a framing`() {
        // The old shape was one screen-wide mode, where "now" meant no forecast
        // at all. Each value has to land on the arrangement that looks the same
        // to its owner after the update, or the app silently rearranges itself.
        fun decode(mode: String) =
            ViewConfigCodec.decode("""{"mode":"$mode","items":[{"key":"temperature","visible":true}]}""")

        assertFalse(decode("now").showForecast, "now meant no forecast")
        assertTrue(decode("hourly").showForecast)
        assertEquals(ForecastMode.HOURLY, decode("hourly").defaultForecastMode)
        assertTrue(decode("daily").showForecast)
        assertEquals(ForecastMode.DAILY, decode("daily").defaultForecastMode)
        // An unknown legacy mode still means "a forecast was showing".
        assertTrue(decode("biweekly").showForecast)
        assertEquals(ForecastMode.DEFAULT, decode("biweekly").defaultForecastMode)
    }

    @Test
    fun `the new keys win over a stale legacy mode, and neither means the default`() {
        // A config written by this build carries both if it was migrated and
        // re-saved; the pair is the authority, so a leftover "mode":"now" can
        // never re-hide a forecast the user has since switched back on.
        val both =
            """{"mode":"now","showForecast":true,"forecastMode":"daily",
               "items":[{"key":"temperature","visible":true}]}"""
        assertTrue(ViewConfigCodec.decode(both).showForecast)
        assertEquals(ForecastMode.DAILY, ViewConfigCodec.decode(both).defaultForecastMode)

        // Older than either key: the shipped default, which shows an hourly
        // forecast — what the app has always done out of the box.
        val neither = """{"density":"comfortable","items":[{"key":"temperature","visible":true}]}"""
        assertTrue(ViewConfigCodec.decode(neither).showForecast)
        assertEquals(ForecastMode.DEFAULT, ViewConfigCodec.decode(neither).defaultForecastMode)
    }

    @Test
    fun `unknown framing and daily-style keys fall back rather than failing the config`() {
        val unknown =
            """{"showForecast":true,"forecastMode":"biweekly","dailyStyle":"spiral",
               "items":[{"key":"wind","visible":true}]}"""
        val config = ViewConfigCodec.decode(unknown)
        assertEquals(ForecastMode.DEFAULT, config.defaultForecastMode)
        assertEquals(DailyStyle.DEFAULT, config.dailyStyle)
        // The rest of the config still came through — one bad token must not
        // cost the user their field layout.
        assertTrue(config.items.first { it.module == reading(WeatherField.WIND) }.visible)
    }

    @Test
    fun `the retired forecast-layout keys are ignored, not fatal`() {
        // ForecastLayout (side-by-side vs stacked) was subsumed by the grid.
        // Configs still carrying it must decode, dropping only that choice.
        val legacy =
            """{"dailyLayout":"column","hourlyLayout":"row","mode":"daily",
               "items":[{"key":"temperature","visible":true}]}"""
        val config = ViewConfigCodec.decode(legacy)
        assertEquals(ForecastMode.DAILY, config.defaultForecastMode)
        assertTrue(config.showForecast)
    }

    @Test
    fun `a config saved before density existed decodes at the default density`() {
        // The legacy on-disk shape: a bare array of settings, no density wrapper.
        val raw = """[{"key":"temperature","visible":true},{"key":"conditions","visible":true}]"""
        val config = ViewConfigCodec.decode(raw)
        assertEquals(Density.DEFAULT, config.density)
        // …and the field settings still come through.
        assertEquals(reading(WeatherField.TEMPERATURE), config.items.first().module)
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
        assertEquals(ModuleKey.catalog.size, config.items.size)
        assertEquals(reading(WeatherField.WIND), config.items.first().module)
        assertEquals(emptyList<ModuleKey>(), config.items.map { it.module } - ModuleKey.catalog.toSet())
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

    @Test
    fun `module spans round-trip, and older or unknown spans fall back per-field`() {
        val resized = ViewConfig.DEFAULT.setSpan(reading(WeatherField.TEMPERATURE), ModuleSpan.HALF)
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(resized))
        assertEquals(
            ModuleSpan.HALF,
            restored.items.first { it.module == reading(WeatherField.TEMPERATURE) }.span,
        )

        // A config written before modules had widths carries no span key. Each
        // field falls back to ITS default — temperature full, wind quarter —
        // so an update reproduces the old hero-and-rows proportions.
        val legacy = """[{"key":"temperature","visible":true},{"key":"wind","visible":true}]"""
        val decoded = ViewConfigCodec.decode(legacy)
        assertEquals(
            ModuleSpan.FULL,
            decoded.items.first { it.module == reading(WeatherField.TEMPERATURE) }.span,
        )
        assertEquals(ModuleSpan.QUARTER, decoded.items.first { it.module == reading(WeatherField.WIND) }.span)

        // An unknown span token (a future size this build doesn't know) falls
        // back the same way rather than failing the whole config.
        val unknown = """{"items":[{"key":"wind","visible":true,"span":"three-quarters"}]}"""
        val wind = ViewConfigCodec.decode(unknown).items.first { it.module == reading(WeatherField.WIND) }
        assertEquals(ModuleSpan.QUARTER, wind.span)
    }

    @Test
    fun `the sun module round-trips, and stays off for a config written before it`() {
        val on = ViewConfig.DEFAULT.toggle(ModuleKey.Sun)
        assertTrue(ViewConfigCodec.decode(ViewConfigCodec.encode(on)).shows(ModuleKey.Sun))
        assertFalse(ViewConfigCodec.decode(ViewConfigCodec.encode(ViewConfig.DEFAULT)).shows(ModuleKey.Sun))

        // A config persisted before the module existed carries neither the key
        // nor the retired switch. It must decode to OFF — an opt-in that
        // switches itself on during an app update is not opt-in.
        val legacy = """{"density":"cozy","items":[{"key":"temperature","visible":true}]}"""
        assertFalse(ViewConfigCodec.decode(legacy).shows(ModuleKey.Sun))
    }

    @Test
    fun `the retired sun-times switch becomes the sun module, once`() {
        // Someone who had sun times on must still see them after the update —
        // as a tile they can now move and resize.
        val legacy =
            """{"showSunTimes":true,"items":[{"key":"temperature","visible":true}]}"""
        val migrated = ViewConfigCodec.decode(legacy)
        assertTrue(migrated.shows(ModuleKey.Sun))
        assertEquals(1, migrated.items.count { it.module == ModuleKey.Sun }, "exactly one sun module")

        // But a config that already carries its own sun entry is the authority
        // on its own layout: a leftover switch must not re-show a tile the
        // user has since hidden.
        val bothWaysHidden =
            """{"showSunTimes":true,"items":[
               {"key":"temperature","visible":true},{"key":"sun","visible":false}]}"""
        assertFalse(ViewConfigCodec.decode(bothWaysHidden).shows(ModuleKey.Sun))
    }
}
