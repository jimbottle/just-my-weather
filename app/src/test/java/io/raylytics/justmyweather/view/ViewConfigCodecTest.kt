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
        assertTrue(restored.shows(ModuleKey.Forecast))

        val hidden = ViewConfigCodec.decode(ViewConfigCodec.encode(original.toggle(ModuleKey.Forecast)))
        assertFalse(hidden.shows(ModuleKey.Forecast))
        // Hiding the module must not forget which framing to reopen on.
        assertEquals(ForecastMode.DAILY, hidden.defaultForecastMode)
        // And the switch is the module's own visibility now: the retired key
        // is not written.
        assertTrue(!ViewConfigCodec.encode(original).contains("showForecast"))
    }

    @Test
    fun `hourly hours round-trip, default to a day, and clamp a stored value`() {
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(ViewConfig.DEFAULT.setHourlyHours(72)))
        assertEquals(72, restored.hourlyHours)
        val older = """{"density":"comfortable","items":[{"key":"temperature","visible":true}]}"""
        assertEquals(24, ViewConfigCodec.decode(older).hourlyHours)
        val wild = """{"hourlyHours":5000,"items":[{"key":"temperature","visible":true}]}"""
        assertEquals(168, ViewConfigCodec.decode(wild).hourlyHours)
    }

    @Test
    fun `forecast elements round-trip, an absent key means the default, and an empty list is kept`() {
        val chosen = ViewConfig.DEFAULT.toggleForecastElement(ForecastElement.HUMIDITY)
        assertEquals(chosen.forecastElements, ViewConfigCodec.decode(ViewConfigCodec.encode(chosen)).forecastElements)
        val older = """{"items":[{"key":"temperature","visible":true}]}"""
        assertEquals(ForecastElement.DEFAULT, ViewConfigCodec.decode(older).forecastElements)
        val none = """{"forecastElements":[],"items":[{"key":"temperature","visible":true}]}"""
        assertEquals(emptySet<ForecastElement>(), ViewConfigCodec.decode(none).forecastElements)
        val unknown = """{"forecastElements":["wind","moonphase"],"items":[{"key":"temperature","visible":true}]}"""
        assertEquals(setOf(ForecastElement.WIND), ViewConfigCodec.decode(unknown).forecastElements)
    }

    @Test
    fun `the forecast tile layout round-trips and defaults to spread`() {
        val stacked = ViewConfig.DEFAULT.setForecastTileLayout(ForecastTileLayout.STACKED)
        assertEquals(
            ForecastTileLayout.STACKED,
            ViewConfigCodec.decode(ViewConfigCodec.encode(stacked)).forecastTileLayout,
        )
        val older = """{"items":[{"key":"temperature","visible":true}]}"""
        assertEquals(ForecastTileLayout.SPREAD, ViewConfigCodec.decode(older).forecastTileLayout)
    }

    @Test
    fun `times-in round-trips, and an absent or unknown key reads as the phone's clock`() {
        val place = ViewConfigCodec.decode(ViewConfigCodec.encode(ViewConfig.DEFAULT.setTimesIn(TimesIn.PLACE)))
        assertEquals(TimesIn.PLACE, place.timesIn)
        val older = """{"items":[{"key":"temperature","visible":true}]}"""
        assertEquals(TimesIn.DEVICE, ViewConfigCodec.decode(older).timesIn)
        val unknown = """{"timesIn":"mars","items":[{"key":"temperature","visible":true}]}"""
        assertEquals(TimesIn.DEVICE, ViewConfigCodec.decode(unknown).timesIn)
    }

    @Test
    fun `tap-for-details round-trips, and a config from before it reads as on`() {
        val off = ViewConfigCodec.decode(ViewConfigCodec.encode(ViewConfig.DEFAULT.setTapForDetails(false)))
        assertFalse(off.tapForDetails)
        // Absent key: the shipped behaviour, not the opt-out.
        val older = """{"density":"comfortable","items":[{"key":"temperature","visible":true}]}"""
        assertTrue(ViewConfigCodec.decode(older).tapForDetails)
    }

    @Test
    fun `a legacy view mode splits into show-the-forecast plus a framing`() {
        // The old shape was one screen-wide mode, where "now" meant no forecast
        // at all. Each value has to land on the arrangement that looks the same
        // to its owner after the update, or the app silently rearranges itself.
        fun decode(mode: String) =
            ViewConfigCodec.decode("""{"mode":"$mode","items":[{"key":"temperature","visible":true}]}""")

        assertFalse(decode("now").shows(ModuleKey.Forecast), "now meant no forecast")
        assertTrue(decode("hourly").shows(ModuleKey.Forecast))
        assertEquals(ForecastMode.HOURLY, decode("hourly").defaultForecastMode)
        assertTrue(decode("daily").shows(ModuleKey.Forecast))
        assertEquals(ForecastMode.DAILY, decode("daily").defaultForecastMode)
        // An unknown legacy mode still means "a forecast was showing".
        assertTrue(decode("biweekly").shows(ModuleKey.Forecast))
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
        assertTrue(ViewConfigCodec.decode(both).shows(ModuleKey.Forecast))
        assertEquals(ForecastMode.DAILY, ViewConfigCodec.decode(both).defaultForecastMode)

        // Older than either key: the shipped default, which shows an hourly
        // forecast — what the app has always done out of the box.
        val neither = """{"density":"comfortable","items":[{"key":"temperature","visible":true}]}"""
        assertTrue(ViewConfigCodec.decode(neither).shows(ModuleKey.Forecast))
        assertEquals(ForecastMode.DEFAULT, ViewConfigCodec.decode(neither).defaultForecastMode)
    }

    @Test
    fun `a config with its own forecast entry is the authority over the legacy switch`() {
        // A config written by this build carries the module; a leftover
        // `showForecast:true` from before must not re-show a forecast the user
        // has since hidden — and the module's stored size comes through.
        val stored =
            """{"showForecast":true,"items":[
                {"key":"temperature","visible":true},
                {"key":"forecast","visible":false,"columns":2,"rows":3}
            ]}"""
        val config = ViewConfigCodec.decode(stored)
        assertFalse(config.shows(ModuleKey.Forecast))
        assertEquals(ModuleSize(2, 3), config.items.first { it.module == ModuleKey.Forecast }.size)
        // With no entry, the legacy switch decides — off stays off.
        val legacyOff = """{"showForecast":false,"items":[{"key":"temperature","visible":true}]}"""
        assertFalse(ViewConfigCodec.decode(legacyOff).shows(ModuleKey.Forecast))
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
        assertTrue(config.shows(ModuleKey.Forecast))
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
    fun `module sizes round-trip, and a config without them falls back per-module`() {
        val resized = ViewConfig.DEFAULT.resize(reading(WeatherField.TEMPERATURE), ModuleSize(3, 1))
        val restored = ViewConfigCodec.decode(ViewConfigCodec.encode(resized))
        assertEquals(
            ModuleSize(3, 1),
            restored.items.first { it.module == reading(WeatherField.TEMPERATURE) }.size,
        )

        // A config written before modules had sizes carries neither span nor
        // columns. Each module falls back to ITS default — temperature 4×2,
        // wind a cell — so an update reproduces the old hero-and-rows look.
        val legacy = """[{"key":"temperature","visible":true},{"key":"wind","visible":true}]"""
        val decoded = ViewConfigCodec.decode(legacy)
        assertEquals(ModuleSize(4, 2), decoded.items.first { it.module == reading(WeatherField.TEMPERATURE) }.size)
        assertEquals(ModuleSize.CELL, decoded.items.first { it.module == reading(WeatherField.WIND) }.size)
    }

    @Test
    fun `the legacy width token becomes columns, with the rows its content needed`() {
        // Widths carry over. Rows did not exist — a row was as tall as its
        // content — so a module at its default width keeps the default's
        // height (the hero needed two rows; so does the sun table), and one
        // the user had narrowed was a one-liner and stays one row.
        val legacy =
            """{"items":[
                {"key":"temperature","visible":true,"span":"full"},
                {"key":"conditions","visible":true,"span":"quarter"},
                {"key":"sun","visible":true,"span":"half"},
                {"key":"wind","visible":true,"span":"three-quarters"}
            ]}"""
        val decoded = ViewConfigCodec.decode(legacy)

        fun sizeOf(module: ModuleKey) = decoded.items.first { it.module == module }.size
        assertEquals(ModuleSize(4, 2), sizeOf(reading(WeatherField.TEMPERATURE)))
        // Narrowed below its minimum in the old model's terms: clamped up.
        assertEquals(ModuleSize(2, 1), sizeOf(reading(WeatherField.CONDITIONS)))
        assertEquals(ModuleSize(2, 1), sizeOf(ModuleKey.Sun))
        // An unknown token (a size some other build knew) falls back to the
        // module's default rather than failing the whole config.
        assertEquals(ModuleSize.CELL, sizeOf(reading(WeatherField.WIND)))
    }

    @Test
    fun `a stored size outside what the module can fill is clamped, not rejected`() {
        val stored = """{"items":[{"key":"conditions","visible":true,"columns":1,"rows":9}]}"""
        val conditions = ViewConfigCodec.decode(stored).items.first { it.module == reading(WeatherField.CONDITIONS) }
        assertEquals(ModuleSize(2, ModuleSize.MAX_ROWS), conditions.size)
        // The new pair is what this build writes; the legacy token is not.
        val written = ViewConfigCodec.encode(ViewConfig.DEFAULT)
        assertTrue(written.contains("\"columns\":4"), written)
        assertTrue(!written.contains("\"span\""), written)
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
