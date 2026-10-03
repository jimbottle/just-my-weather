package io.raylytics.justmyweather.data.metno

import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.ZoneId

class MetNoClientTest {
    private val requested = mutableListOf<Pair<String, Map<String, String>>>()

    private fun client(status: Int, body: String) =
        MetNoClient(
            transport =
                HttpTransport { url, headers ->
                    requested += url to headers
                    HttpResult(status, body, null)
                },
        )

    private val louisville: ZoneId = ZoneId.of("America/New_York") // UTC-4 in October

    private fun entry(time: String, tempC: Double, windMps: Double, windFrom: Double, six: String?) =
        """{"time":"$time","data":{"instant":{"details":""" +
            """{"air_temperature":$tempC,"wind_speed":$windMps,"wind_from_direction":$windFrom}}""" +
            (six?.let { ""","next_6_hours":$it""" } ?: "") + "}}"

    private fun six(symbol: String, max: Double, min: Double, mm: Double) =
        """{"summary":{"symbol_code":"$symbol"},"details":""" +
            """{"air_temperature_max":$max,"air_temperature_min":$min,"precipitation_amount":$mm}}"""

    @Test
    fun `folds six-hour blocks into the place's days, in American units and NWS-style words`() = runTest {
        // Two full days of six-hourly entries (the series' tail), then a lone
        // instant with no block: the ragged end MET sends.
        val body =
            """{"properties":{"timeseries":[
            ${entry("2026-10-10T00:00:00Z", 20.0, 1.0, 10.0, six("cloudy", 21.0, 15.0, 0.0))},
            ${entry("2026-10-10T06:00:00Z", 15.0, 2.0, 20.0, six("fair_day", 18.0, 14.0, 0.0))},
            ${entry("2026-10-10T12:00:00Z", 18.0, 5.0, 225.0, six("lightrainshowers_day", 25.0, 17.0, 1.2))},
            ${entry("2026-10-10T18:00:00Z", 25.0, 3.0, 200.0, six("partlycloudy_night", 24.0, 19.0, 0.5))},
            ${entry("2026-10-11T00:00:00Z", 19.0, 1.5, 30.0, six("clearsky_night", 19.0, 12.0, 0.0))},
            ${entry("2026-10-11T06:00:00Z", 12.0, 1.0, 40.0, six("clearsky_day", 16.0, 11.0, 0.0))},
            ${entry("2026-10-11T12:00:00Z", 16.0, 2.0, 50.0, six("clearsky_day", 27.0, 16.0, 0.0))},
            ${entry("2026-10-11T18:00:00Z", 27.0, 2.5, 60.0, six("clearsky_night", 26.0, 20.0, 0.0))},
            ${entry("2026-10-12T00:00:00Z", 21.0, 1.0, 0.0, null)}
            ]}}"""
        val days = client(200, body).getDailyForecast(38.25, -85.76, louisville)

        // 00Z is 8 pm the previous local day, so the first block lands on
        // Oct 9 — one block, not a day, dropped — and Oct 11's 00Z block is
        // Oct 10's evening. Oct 11 keeps three blocks (2 am to 8 pm) and
        // stays; Oct 12 has no block at all.
        assertEquals(listOf("2026-10-10", "2026-10-11"), days.map { it.date.toString() })
        val sat = days[0]
        assertEquals(LocalDate.of(2026, 10, 10), sat.date)
        assertEquals(77.0, sat.highF!!, 0.01) // 25 °C, the day's block max
        assertEquals(53.6, sat.lowF!!, 0.01) // 12 °C, from the evening block
        assertEquals("Rain Showers", sat.conditions) // the 8 am–2 pm block holds noon
        assertEquals(11.18, sat.windMph!!, 0.01) // 5 m/s, the windiest instant
        assertEquals("SW", sat.windDirection) // its direction, 225°
        assertEquals(1.7 / 25.4, sat.precipIn!!, 0.0001) // 1.2 + 0.5 + 0 mm
        assertNull(sat.precipChancePercent) // MET gives amounts here, not odds

        // Identified, as MET's terms require, and asked for the complete feed.
        val (url, headers) = requested.single()
        assertTrue(url.startsWith("https://api.met.no/weatherapi/locationforecast/2.0/complete?"), url)
        assertTrue(url.contains("lat=38.2500&lon=-85.7600"), url)
        assertEquals("just-my-weather (dev@raylytics.io)", headers["User-Agent"])
    }

    @Test
    fun `hourly entries only count once in six, so nothing overlaps`() = runTest {
        // Hourly head: 12Z and 13Z both carry a six-hour block; only 12Z's
        // block (a 00/06/12/18 start) folds in, so its 3 mm is not doubled.
        // 18Z and the next 00Z complete the day (8 am, 2 pm, 8 pm New York).
        val body =
            """{"properties":{"timeseries":[
            ${entry("2026-10-04T12:00:00Z", 18.0, 1.0, 0.0, six("rain", 20.0, 16.0, 3.0))},
            ${entry("2026-10-04T13:00:00Z", 18.5, 1.0, 0.0, six("rain", 20.0, 16.0, 3.0))},
            ${entry("2026-10-04T18:00:00Z", 20.0, 1.0, 0.0, six("cloudy", 20.0, 17.0, 0.0))},
            ${entry("2026-10-05T00:00:00Z", 17.0, 1.0, 0.0, six("cloudy", 17.0, 14.0, 0.0))}
            ]}}"""
        val day = client(200, body).getDailyForecast(0.0, 0.0, louisville).single()
        assertEquals(3.0 / 25.4, day.precipIn!!, 0.0001)
        assertEquals("Rain", day.conditions)
    }

    @Test
    fun `a day the series only half covers is dropped, one it covers to the evening is kept`() = runTest {
        // The series' tail: 06Z and 12Z are 2 am and 8 am in New York — a
        // high from those would be the morning's. With 18Z (2 pm) too the
        // afternoon is in, and the day stands.
        val head = entry("2026-10-12T06:00:00Z", 12.0, 1.0, 0.0, six("fair_day", 16.0, 11.0, 0.0)) + "," +
            entry("2026-10-12T12:00:00Z", 16.0, 1.0, 0.0, six("clearsky_day", 27.0, 16.0, 0.0))
        val twoBlocks = """{"properties":{"timeseries":[$head]}}"""
        assertEquals(emptyList<ExtendedDay>(), client(200, twoBlocks).getDailyForecast(0.0, 0.0, louisville))
        val threeBlocks =
            """{"properties":{"timeseries":[$head,
            ${entry("2026-10-12T18:00:00Z", 27.0, 1.0, 0.0, six("clearsky_night", 26.0, 20.0, 0.0))}
            ]}}"""
        val day = client(200, threeBlocks).getDailyForecast(0.0, 0.0, louisville).single()
        assertEquals(LocalDate.of(2026, 10, 12), day.date)
        assertEquals(80.6, day.highF!!, 0.01) // 27 °C, the afternoon's
    }

    @Test
    fun `a failed request is an error, and an empty body is no days`() = runTest {
        assertThrows<IllegalStateException> { client(500, "boom").getDailyForecast(0.0, 0.0, louisville) }
        assertEquals(emptyList<ExtendedDay>(), client(200, "{}").getDailyForecast(0.0, 0.0, louisville))
    }

    @Test
    fun `symbols read like NWS, drop their icon suffix, and an unknown one is no words`() {
        assertEquals("Clear", MetNoSymbols.describe("clearsky_day"))
        assertEquals("Clear", MetNoSymbols.describe("clearsky_polartwilight"))
        assertEquals("Thunderstorms", MetNoSymbols.describe("heavyrainshowersandthunder_night"))
        assertEquals("Snow Showers", MetNoSymbols.describe("lightsnowshowers_day"))
        assertNull(MetNoSymbols.describe("plague_of_frogs"))
    }
}
