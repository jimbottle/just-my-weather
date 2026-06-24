package io.raylytics.justmyweather.data.nws

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the client's parsing + retry against a fake transport — no network.
 * The fake is queue-driven so a test can stage a 503-then-200 retry sequence.
 */
class NwsClientTest {
    private class FakeTransport(private val responses: ArrayDeque<HttpResult>) : HttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
            requested += url
            return responses.removeFirst()
        }
    }

    private fun client(vararg results: HttpResult): Pair<NwsClient, FakeTransport> {
        val transport = FakeTransport(ArrayDeque(results.toList()))
        return NwsClient(transport = transport, sleep = {}) to transport
    }

    @Test
    fun `getObservation parses and converts to american units`() = runTest {
        val (client, _) =
            client(
                HttpResult(
                    200,
                    """
                    {"properties":{
                      "timestamp":"2026-06-24T18:00:00+00:00",
                      "temperature":{"value":20.0,"unitCode":"wmoUnit:degC"},
                      "windSpeed":{"value":10.0,"unitCode":"wmoUnit:km_h-1"},
                      "seaLevelPressure":{"value":101325.0,"unitCode":"wmoUnit:Pa"},
                      "textDescription":"Partly Cloudy"
                    }}
                    """.trimIndent(),
                    null,
                ),
            )
        val obs = client.getObservation("KNYC")
        assertEquals(68.0, obs.temperatureF!!, 1e-6)
        assertEquals(6.21371, obs.windMph!!, 1e-4)
        assertEquals(29.92, obs.pressureInHg!!, 0.01)
        assertEquals("Partly Cloudy", obs.conditions)
    }

    @Test
    fun `getJson retries on 503 then succeeds`() = runTest {
        val (client, transport) =
            client(
                HttpResult(503, "", "0"),
                HttpResult(200, """{"properties":{"timestamp":"2026-06-24T18:00:00Z"}}""", null),
            )
        val obs = client.getObservation("KNYC")
        assertEquals(2, transport.requested.size)
        assertEquals(null, obs.temperatureF)
    }

    @Test
    fun `non-retryable error throws NwsHttpException`() = runTest {
        val (client, _) = client(HttpResult(404, "not found", null))
        val ex =
            try {
                client.getObservation("BAD")
                null
            } catch (e: NwsHttpException) {
                e
            }
        assertEquals(404, ex?.status)
    }

    @Test
    fun `resolveLocation derives zone id from forecastZone url`() = runTest {
        val (client, _) =
            client(
                HttpResult(
                    200,
                    """
                    {"properties":{
                      "gridId":"OKX","gridX":33,"gridY":35,
                      "forecastZone":"https://api.weather.gov/zones/forecast/NYZ072"
                    }}
                    """.trimIndent(),
                    null,
                ),
                HttpResult(
                    200,
                    """{"features":[{"properties":{"stationIdentifier":"KNYC"}}]}""",
                    null,
                ),
            )
        val point = client.resolveLocation(40.7128, -74.0060)
        assertEquals("OKX", point.gridId)
        assertEquals("NYZ072", point.forecastZoneId)
        assertEquals("KNYC", point.observationStationId)
        assertTrue(point.gridX == 33 && point.gridY == 35)
    }
}
