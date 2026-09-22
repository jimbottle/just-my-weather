package io.raylytics.justmyweather.data.openmeteo

import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class OpenMeteoClientTest {
    private val requested = mutableListOf<String>()

    private fun client(status: Int, body: String) =
        OpenMeteoClient(
            transport =
                HttpTransport { url, _ ->
                    requested += url
                    HttpResult(status, body, null)
                },
        )

    @Test
    fun `parses the daily forecast into American units and NWS-style words`() = runTest {
        val body =
            """
            {"daily":{
              "time":["2026-09-29","2026-09-30","bad-date"],
              "temperature_2m_max":[84.2,null,70.0],
              "temperature_2m_min":[61.0,58.5,50.0],
              "precipitation_probability_max":[40,null,0],
              "weather_code":[80,3,0],
              "wind_speed_10m_max":[12.3,5.0,1.0],
              "wind_direction_10m_dominant":[225,null,0]
            }}
            """.trimIndent()
        val days = client(200, body).getDailyForecast(38.25, -85.76)
        // The unparseable date is dropped; nulls stay null, never zero.
        assertEquals(2, days.size)
        assertEquals(
            ExtendedDay(LocalDate.of(2026, 9, 29), 84.2, 61.0, 40.0, "Rain Showers", 12.3, "SW"),
            days[0],
        )
        assertNull(days[1].highF)
        assertNull(days[1].precipChancePercent)
        assertNull(days[1].windDirection)
        assertEquals("Cloudy", days[1].conditions)
        // Asked in the units the rest of the app uses, the place's calendar,
        // and far enough ahead to reach day fourteen.
        val url = requested.single()
        assertTrue(url.contains("temperature_unit=fahrenheit"), url)
        assertTrue(url.contains("wind_speed_unit=mph"), url)
        assertTrue(url.contains("timezone=auto"), url)
        assertTrue(url.contains("forecast_days=16"), url)
        assertTrue(url.contains("latitude=38.2500&longitude=-85.7600"), url)
    }

    @Test
    fun `a failed request is an error, and an empty body is no days`() = runTest {
        assertThrows<IllegalStateException> { client(500, "boom").getDailyForecast(0.0, 0.0) }
        assertEquals(emptyList<ExtendedDay>(), client(200, "{}").getDailyForecast(0.0, 0.0))
    }

    @Test
    fun `WMO codes read like NWS, and an unknown code is no words`() {
        assertEquals("Clear", WmoCodes.describe(0))
        assertEquals("Thunderstorms", WmoCodes.describe(95))
        assertNull(WmoCodes.describe(42))
    }
}
