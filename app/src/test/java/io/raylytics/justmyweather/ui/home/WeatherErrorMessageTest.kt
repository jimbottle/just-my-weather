package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.nws.NwsHttpException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

/**
 * The failed-fetch sentence reaches the screen and a bug report's diagnostics,
 * and the privacy policy promises a bug report carries no location. The data
 * layer's messages embed `/points/lat,lon`, so none of them may pass through.
 */
class WeatherErrorMessageTest {
    private val lat = "38.2527"
    private val lon = "-85.7585"

    private val leaky =
        listOf(
            NwsHttpException(404, "/points/$lat,$lon", """{"detail":"Unable to provide data for point $lat,$lon"}"""),
            NwsHttpException(500, "/points/$lat,$lon", "upstream error at $lat,$lon"),
            IllegalStateException("no observation stations near $lat,$lon"),
            IllegalStateException("nws /points/$lat,$lon missing gridId"),
        )

    @Test
    fun `no failure puts the coordinates into the message`() {
        for (e in leaky) {
            val message = weatherErrorMessage(e)
            assertFalse(lat in message || lon in message || "/points" in message, "leaked: $message")
        }
    }

    @Test
    fun `a points 404 says the place is outside NWS coverage`() {
        assertEquals(
            "The National Weather Service doesn't forecast for this place. It covers the US.",
            weatherErrorMessage(leaky[0]),
        )
    }

    @Test
    fun `other NWS errors keep the status, which is what a bug report needs`() {
        assertEquals(
            "The weather service returned an error (HTTP 500). Try again shortly.",
            weatherErrorMessage(leaky[1]),
        )
    }

    @Test
    fun `network failures still read as a connection problem`() {
        assertEquals(
            "Couldn't reach the weather service. Check your connection.",
            weatherErrorMessage(UnknownHostException("api.weather.gov")),
        )
    }
}
