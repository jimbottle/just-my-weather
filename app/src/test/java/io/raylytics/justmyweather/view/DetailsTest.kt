package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DetailsTest {
    private val newYork = ZoneId.of("America/New_York")

    private val snapshot =
        WeatherSnapshot(
            locationLabel = "Louisville, KY",
            temperatureF = 93.2,
            conditions = "Mostly Clear",
            windMph = 12.4,
            precipitationIn = 0.0,
            pressureInHg = 29.92,
            observedAt = Instant.parse("2026-09-05T23:00:00Z"),
            relativeHumidityPercent = 58.0,
            windDirectionDegrees = 225.0,
            timeZone = "America/New_York",
        )

    private fun module(field: WeatherField) =
        ModuleValue(ModuleKey.Reading(field), field.defaultLabel, field.defaultSize, ModuleContent.Reading("x"))

    @Test
    fun `a reading opens the whole observation, its own field first`() {
        val detail = Details.ofModule(module(WeatherField.WIND), snapshot, ZoneId.of("UTC"))!!
        assertEquals("Wind", detail.title)
        // Observed in the PLACE's zone (7 PM in New York), never the fallback.
        assertEquals("Observed 7:00 PM · Louisville, KY", detail.subtitle)
        assertEquals(
            listOf("Wind", "Temperature", "Conditions", "Precip (last hr)", "Pressure", "Humidity"),
            detail.rows.map { it.label },
        )
        // Wind gets the direction the tile has no room for; humidity, which
        // no tile shows at all, is here too.
        assertEquals("12 mph SW", detail.rows.first().value)
        assertEquals("58%", detail.rows.last().value)
        assertEquals("93°", detail.rows.first { it.label == "Temperature" }.value)
    }

    @Test
    fun `a reading with nothing behind it still opens, with dashes`() {
        val empty = WeatherSnapshot("Nowhere", null, null, null, null, null, observedAt = null)
        val detail = Details.ofModule(module(WeatherField.TEMPERATURE), empty, newYork)!!
        assertEquals("Nowhere", detail.subtitle)
        assertEquals(setOf("—"), detail.rows.map { it.value }.toSet())
    }

    @Test
    fun `the sun opens both days with their daylight`() {
        val days =
            listOf(
                SunDay(
                    LocalDate.of(2026, 9, 5),
                    Instant.parse("2026-09-05T11:16:00Z"),
                    Instant.parse("2026-09-06T00:06:00Z"),
                ),
                SunDay(LocalDate.of(2026, 9, 6), null, null),
            )
        val sun = ModuleValue(ModuleKey.Sun, "Sun", ModuleKey.Sun.defaultSize, ModuleContent.Sun(days, newYork))
        val detail = Details.ofModule(sun, snapshot, newYork)!!
        assertEquals(
            listOf(
                "Sunrise · Sep 5", "Sunset · Sep 5", "Daylight · Sep 5",
                "Sunrise · Sep 6", "Sunset · Sep 6", "Daylight · Sep 6",
            ),
            detail.rows.map { it.label },
        )
        assertEquals(listOf("7:16 AM", "8:06 PM", "12 h 50 min", "—", "—", "—"), detail.rows.map { it.value })
    }

    @Test
    fun `the forecast module itself opens nothing — its hours and days do`() {
        val forecast =
            ModuleValue(
                ModuleKey.Forecast,
                "Forecast",
                ModuleKey.Forecast.defaultSize,
                ModuleContent.Forecast(null, null, null, ForecastMode.DEFAULT, DailyStyle.DEFAULT, 24, newYork),
            )
        assertNull(Details.ofModule(forecast, snapshot, newYork))
    }

    @Test
    fun `an hour opens every field it carries, in the place's clock`() {
        val hour =
            ForecastPoint(
                startTime = Instant.parse("2026-09-05T22:00:00Z"),
                temperatureF = 93.6,
                windMph = 12.0,
                precipProbabilityPercent = 28.0,
                shortForecast = "Chance Showers And Thunderstorms",
                windDirection = "SW",
                relativeHumidityPercent = 60.0,
                dewpointF = 70.3,
            )
        val detail = Details.ofHour(hour, newYork)
        assertEquals("6 pm", detail.title)
        assertEquals("Saturday, September 5 · Forecast", detail.subtitle)
        assertEquals(
            listOf(
                "Temperature" to "94°",
                "Chance of precipitation" to "28%",
                "Wind" to "12 mph SW",
                "Humidity" to "60%",
                "Dew point" to "70°",
                "Conditions" to "Chance Showers And Thunderstorms",
            ),
            detail.rows.map { it.label to it.value },
        )
        assertNull(detail.body)
    }

    @Test
    fun `a period opens its fields and NWS's prose`() {
        val night =
            DailyPeriod(
                name = "Friday Night",
                isDaytime = false,
                temperatureF = 70.0,
                shortForecast = "Partly Cloudy",
                precipProbabilityPercent = null,
                windMph = 5.0,
                windDirection = "S",
                detailedForecast = "Partly cloudy, with a low around 70.",
            )
        val detail = Details.ofPeriod(night)
        assertEquals("Friday Night", detail.title)
        assertEquals("Forecast · night", detail.subtitle)
        assertEquals("Low", detail.rows.first().label)
        assertEquals("—", detail.rows[1].value)
        assertEquals("5 mph S", detail.rows[2].value)
        assertEquals("Partly cloudy, with a low around 70.", detail.body)
    }

    @Test
    fun `wind reads the same way everywhere`() {
        assertEquals("—", Details.wind(null, "SW"))
        assertEquals("Calm", Details.wind(0.4, "SW"))
        assertEquals("12 mph", Details.wind(12.4, null))
        assertEquals("12 mph SW", Details.wind(12.4, "SW"))
    }
}
