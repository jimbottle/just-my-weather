package io.raylytics.justmyweather.data

import io.raylytics.justmyweather.data.nws.HttpResult
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.NwsClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers the repository's label-fallback and point-caching behaviour — the
 * logic most likely to regress — by driving a real [NwsClient] over a routing
 * fake transport (no network).
 */
class WeatherRepositoryTest {
    /** Routes by endpoint so the same instance serves a full load and records
     * every URL, letting tests assert on cache reuse across calls. */
    private class RoutingTransport : HttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            requested += url
            val body =
                when {
                    "/observations/latest" in url -> OBSERVATION
                    url.endsWith("/stations") -> STATIONS
                    "/points/" in url -> POINTS
                    else -> error("unexpected url $url")
                }
            return HttpResult(200, body, null)
        }

        fun pointsLookups() = requested.count { "/points/" in it && !it.endsWith("/stations") }
    }

    private fun repo(transport: RoutingTransport, cache: PointCache = InMemoryPointCache()) =
        WeatherRepository(NwsClient(transport = transport), cache)

    @Test
    fun `blank label is filled from the points relativeLocation`() = runTest {
        val snapshot =
            repo(RoutingTransport()).load(WeatherLocation(40.71, -74.0, label = ""))
        assertEquals("Brooklyn, NY", snapshot.locationLabel)
        assertEquals(68.0, snapshot.temperatureF!!, 1e-6)
    }

    @Test
    fun `a provided label is kept verbatim`() = runTest {
        val snapshot =
            repo(RoutingTransport()).load(WeatherLocation(40.71, -74.0, label = "Home"))
        assertEquals("Home", snapshot.locationLabel)
    }

    @Test
    fun `point resolution is cached across repeated loads of the same coordinate`() = runTest {
        val transport = RoutingTransport()
        val repository = repo(transport)
        val location = WeatherLocation(40.71, -74.0, label = "Home")
        repository.load(location)
        repository.load(location)
        // Two loads, but /points (and /stations) resolved only once; the second
        // load only re-fetches the observation.
        assertEquals(1, transport.pointsLookups())
    }

    @Test
    fun `a point from a shared cache is reused by a fresh repository without re-resolving`() = runTest {
        // A durable PointCache shared across two repository instances simulates the
        // cache surviving process death: the second cold start must not re-resolve.
        val cache = InMemoryPointCache()
        val location = WeatherLocation(40.71, -74.0, label = "Home")
        repo(RoutingTransport(), cache).load(location)

        val secondStart = RoutingTransport()
        repo(secondStart, cache).load(location)
        assertEquals(0, secondStart.pointsLookups())
    }

    private companion object {
        const val POINTS =
            """
            {"properties":{
              "gridId":"OKX","gridX":33,"gridY":35,
              "forecastZone":"https://api.weather.gov/zones/forecast/NYZ072",
              "relativeLocation":{"properties":{"city":"Brooklyn","state":"NY"}}
            }}
            """

        const val STATIONS = """{"features":[{"properties":{"stationIdentifier":"KNYC"}}]}"""

        const val OBSERVATION =
            """
            {"properties":{
              "timestamp":"2026-06-24T18:00:00Z",
              "temperature":{"value":20.0,"unitCode":"wmoUnit:degC"},
              "textDescription":"Partly Cloudy"
            }}
            """
    }
}
