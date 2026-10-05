package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.view.AccentChoice
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetConfigTest {
    private fun reading(field: WeatherField) = ModuleKey.Reading(field)

    @Test
    fun `a new widget shows the first module on the glance, with the glance's options`() {
        val glance =
            ViewConfig.DEFAULT
                .setHourlyHours(48)
                .relabel(reading(WeatherField.TEMPERATURE), "Temp")
                .moveVisible(ModuleKey.Forecast, 0)
        val theme = ThemeConfig.DEFAULT.withAccent(AccentChoice.SKY)
        val widget = WidgetConfig.seededFrom(glance, theme)
        assertEquals(ModuleKey.Forecast, widget.module)
        assertEquals(listOf(ModuleKey.Forecast), widget.view.visible.map { it.module })
        assertEquals(48, widget.view.hourlyHours)
        assertEquals(AccentChoice.SKY, widget.theme.accent)
        // The hidden temperature kept its name for when the widget switches to it.
        assertEquals("Temp", widget.show(reading(WeatherField.TEMPERATURE)).setting.label)
    }

    @Test
    fun `an all-hidden glance seeds a temperature widget`() {
        val bare = ViewConfig.DEFAULT.items.fold(ViewConfig.DEFAULT) { c, s -> c.setVisible(s.module, false) }
        assertEquals(WidgetConfig.DEFAULT_MODULE, WidgetConfig.seededFrom(bare, ThemeConfig.DEFAULT).module)
    }

    @Test
    fun `show swaps the one visible module`() {
        val widget = WidgetConfig.seededFrom(ViewConfig.DEFAULT, ThemeConfig.DEFAULT).show(ModuleKey.Sun)
        assertEquals(ModuleKey.Sun, widget.module)
        assertEquals(1, widget.view.visible.size)
        assertTrue(widget.view.items.size == ModuleKey.catalog.size)
    }

    @Test
    fun `a config with nothing visible still names a module`() {
        val none = ViewConfig.DEFAULT.items.fold(ViewConfig.DEFAULT) { c, s -> c.setVisible(s.module, false) }
        assertEquals(WidgetConfig.DEFAULT_MODULE, WidgetConfig(none, ThemeConfig.DEFAULT).module)
    }
}
