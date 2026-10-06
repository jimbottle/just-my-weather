package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherLocation
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.metno.ExtendedDay
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class WidgetDataCodecTest {
    private val location = WeatherLocation(38.25, -85.76, "Louisville")

    private val snapshot =
        WeatherSnapshot(
            locationLabel = "Louisville",
            temperatureF = 72.4,
            conditions = "Partly Cloudy",
            windMph = 8.0,
            precipitationIn = null,
            pressureInHg = 30.12,
            observedAt = Instant.parse("2026-10-05T14:08:00Z"),
            relativeHumidityPercent = 55.0,
            windDirectionDegrees = 230.0,
            feelsLikeF = null,
            timeZone = "America/New_York",
        )

    private val full =
        WidgetData(
            location = location,
            snapshot = snapshot,
            hours =
                listOf(
                    ForecastPoint(
                        startTime = Instant.parse("2026-10-05T15:00:00Z"),
                        temperatureF = 74.0,
                        windMph = 9.0,
                        precipProbabilityPercent = 20.0,
                        shortForecast = "Mostly Sunny",
                        windDirection = "SW",
                        relativeHumidityPercent = 50.0,
                        dewpointF = 54.0,
                    ),
                ),
            periods =
                listOf(
                    DailyPeriod(
                        name = "Tonight",
                        startTime = Instant.parse("2026-10-05T22:00:00Z"),
                        isDaytime = false,
                        temperatureF = 58.0,
                        shortForecast = "Clear",
                        precipProbabilityPercent = null,
                        windMph = 5.0,
                        windDirection = "S",
                        detailedForecast = "Clear, with a low around 58.",
                    ),
                ),
            extended =
                listOf(
                    ExtendedDay(
                        date = LocalDate.of(2026, 10, 13),
                        highF = 70.0,
                        lowF = 50.0,
                        precipChancePercent = 10.0,
                        conditions = "Fair",
                        windMph = 4.0,
                        windDirection = "N",
                        precipIn = 0.0,
                    ),
                ),
            fetchedAt = Instant.parse("2026-10-05T14:10:00Z"),
            error = null,
            forecastFetchedAt = Instant.parse("2026-10-05T14:10:00Z"),
        )

    @Test
    fun `round-trips everything the widgets draw from`() {
        assertEquals(full, WidgetDataCodec.decode(WidgetDataCodec.encode(full)))
    }

    @Test
    fun `round-trips the sparse case - a failed first fetch with nothing but an error`() {
        val sparse = WidgetData(location, snapshot = null, fetchedAt = Instant.EPOCH, error = "Couldn't reach it.")
        assertEquals(sparse, WidgetDataCodec.decode(WidgetDataCodec.encode(sparse)))
    }

    @Test
    fun `absent or corrupt data decodes to nothing`() {
        assertNull(WidgetDataCodec.decode(null))
        assertNull(WidgetDataCodec.decode(""))
        assertNull(WidgetDataCodec.decode("{"))
        assertNull(WidgetDataCodec.decode("""{"latitude":1}"""))
    }

    @Test
    fun `an unparseable extended date drops that day, not the data`() {
        val encoded = WidgetDataCodec.encode(full).replace("2026-10-13", "someday")
        val decoded = WidgetDataCodec.decode(encoded)
        assertEquals(emptyList<ExtendedDay>(), decoded?.extended)
        assertEquals(full.hours, decoded?.hours)
    }
}
