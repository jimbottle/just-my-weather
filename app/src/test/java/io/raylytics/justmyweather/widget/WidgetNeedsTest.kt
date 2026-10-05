package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ThemeConfig
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.WeatherField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WidgetNeedsTest {
    private fun widget(module: ModuleKey, edit: (ViewConfig) -> ViewConfig = { it }) =
        WidgetConfig(edit(ViewConfig.DEFAULT.showingOnly(module)), ThemeConfig.DEFAULT)

    @Test
    fun `readings and the sun need no forecast`() {
        val configs = listOf(widget(ModuleKey.Reading(WeatherField.TEMPERATURE)), widget(ModuleKey.Sun))
        assertEquals(WidgetNeeds.NONE, WidgetNeeds.of(configs))
        assertEquals(WidgetNeeds.NONE, WidgetNeeds.of(emptyList()))
    }

    @Test
    fun `an hourly forecast needs the hours`() {
        val hourly = widget(ModuleKey.Forecast) { it.setDefaultForecastMode(ForecastMode.HOURLY) }
        val needs = WidgetNeeds.of(listOf(hourly))
        assertEquals(WidgetNeeds(hours = true), needs)
    }

    @Test
    fun `a daily forecast within NWS's week needs the periods and the hours`() {
        val needs =
            WidgetNeeds.of(
                listOf(widget(ModuleKey.Forecast) { it.setDefaultForecastMode(ForecastMode.DAILY).setDailyDays(7) }),
            )
        assertEquals(WidgetNeeds(hours = true, periods = true, extended = false), needs)
    }

    @Test
    fun `a daily forecast past NWS's week needs the extended source too`() {
        val needs =
            WidgetNeeds.of(
                listOf(widget(ModuleKey.Forecast) { it.setDefaultForecastMode(ForecastMode.DAILY).setDailyDays(9) }),
            )
        assertEquals(WidgetNeeds(hours = true, periods = true, extended = true), needs)
    }

    @Test
    fun `needs are the union across widgets`() {
        val configs =
            listOf(
                widget(ModuleKey.Reading(WeatherField.WIND)),
                widget(ModuleKey.Forecast) { it.setDefaultForecastMode(ForecastMode.HOURLY) },
                widget(ModuleKey.Forecast) { it.setDefaultForecastMode(ForecastMode.DAILY).setDailyDays(9) },
            )
        assertEquals(WidgetNeeds(hours = true, periods = true, extended = true), WidgetNeeds.of(configs))
    }
}
