package io.raylytics.justmyweather.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeConfigCodecTest {
    @Test
    fun `round-trips every choice`() {
        val original = ThemeConfig(ThemeMood.DARK, AccentChoice.VIOLET, TypeChoice.SERIF)
        assertEquals(original, ThemeConfigCodec.decode(ThemeConfigCodec.encode(original)))
    }

    @Test
    fun `absent or corrupt data decodes to the default`() {
        assertEquals(ThemeConfig.DEFAULT, ThemeConfigCodec.decode(null))
        assertEquals(ThemeConfig.DEFAULT, ThemeConfigCodec.decode(""))
        assertEquals(ThemeConfig.DEFAULT, ThemeConfigCodec.decode("{ not json"))
    }

    @Test
    fun `a partial blob fills missing fields with defaults`() {
        // Only mood present — accent and type fall back to the shipped defaults.
        val config = ThemeConfigCodec.decode("""{"mood":"dark"}""")
        assertEquals(ThemeMood.DARK, config.mood)
        assertEquals(AccentChoice.DEFAULT, config.accent)
        assertEquals(TypeChoice.DEFAULT, config.type)
    }

    @Test
    fun `unknown keys fall back to defaults rather than crashing`() {
        val raw = """{"mood":"sepia","accent":"chartreuse","type":"comic"}"""
        assertEquals(ThemeConfig.DEFAULT, ThemeConfigCodec.decode(raw))
    }
}
