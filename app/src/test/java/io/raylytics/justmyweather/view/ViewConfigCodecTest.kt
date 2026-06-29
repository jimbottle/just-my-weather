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
}
