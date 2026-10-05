package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.view.Density
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ThemeMood
import io.raylytics.justmyweather.view.TypeChoice
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewConfigCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WidgetConfigCodecTest {
    @Test
    fun `round-trips a widget config`() {
        val original =
            WidgetConfig
                .seededFrom(ViewConfig.DEFAULT.setDensity(Density.COMPACT).setSunDays(5), ThemeConfig.DEFAULT)
                .show(ModuleKey.Sun)
                .withTheme(ThemeConfig(ThemeMood.DARK, type = TypeChoice.SERIF))
        assertEquals(original, WidgetConfigCodec.decode(WidgetConfigCodec.encode(original)))
    }

    @Test
    fun `absent or corrupt data is no config at all`() {
        assertNull(WidgetConfigCodec.decode(null))
        assertNull(WidgetConfigCodec.decode(""))
        assertNull(WidgetConfigCodec.decode("not json"))
        assertNull(WidgetConfigCodec.decode("""{"theme":{"mood":"dark"}}"""))
    }

    @Test
    fun `a missing theme decodes to the default look`() {
        val view = ViewConfigCodec.encode(ViewConfig.DEFAULT.showingOnly(ModuleKey.Forecast))
        val decoded = WidgetConfigCodec.decode("""{"view":$view}""")
        assertEquals(ThemeConfig.DEFAULT, decoded?.theme)
        assertEquals(ModuleKey.Forecast, decoded?.module)
    }

    @Test
    fun `unknown keys are ignored`() {
        val encoded = WidgetConfigCodec.encode(WidgetConfig.seededFrom(ViewConfig.DEFAULT, ThemeConfig.DEFAULT))
        val withExtra = encoded.dropLast(1) + ""","future":true}"""
        assertEquals(WidgetConfigCodec.decode(encoded), WidgetConfigCodec.decode(withExtra))
    }
}
