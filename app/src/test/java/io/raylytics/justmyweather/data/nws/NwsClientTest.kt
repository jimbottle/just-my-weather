package io.raylytics.justmyweather.data.nws

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
                      "relativeHumidity":{"value":73.6,"unitCode":"wmoUnit:percent"},
                      "windDirection":{"value":340.0,"unitCode":"wmoUnit:degree_(angle)"},
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
        // The unit codes here are the ones the live API actually sends,
        // checked against api.weather.gov on 2026-08-02. Both pass through
        // unconverted, so a wrong assumption would be invisible without this.
        assertEquals(73.6, obs.relativeHumidityPercent!!, 1e-6)
        assertEquals(340.0, obs.windDirectionDegrees!!, 1e-6)
    }

    @Test
    fun `getObservation drops humidity and wind direction on an unexpected unit`() = runTest {
        // These two are the only source of two exported payload fields and are
        // passed through rather than converted, so the guard has to drop a
        // value it can't vouch for. Sending a bare number in an unknown scale
        // would put a confident wrong humidity on a watch face.
        val (client, _) =
            client(
                HttpResult(
                    200,
                    """
                    {"properties":{
                      "timestamp":"2026-06-24T18:00:00+00:00",
                      "temperature":{"value":20.0,"unitCode":"wmoUnit:degC"},
                      "relativeHumidity":{"value":0.736,"unitCode":"wmoUnit:one"},
                      "windDirection":{"value":5.93,"unitCode":"wmoUnit:rad"},
                      "textDescription":"Partly Cloudy"
                    }}
                    """.trimIndent(),
                    null,
                ),
            )
        val obs = client.getObservation("KNYC")
        assertNull(obs.relativeHumidityPercent)
        assertNull(obs.windDirectionDegrees)
        // The rest of the observation still parses — one odd unit must not
        // cost the reading.
        assertEquals(68.0, obs.temperatureF!!, 1e-6)
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
    fun `getActiveAlertsAt queries by point, not by forecast zone`() = runTest {
        // Storm-based warnings (tornado, severe thunderstorm, flash flood) are
        // issued against county zones/polygons, not the forecast zone, so a
        // zone query silently drops them. Verified live on 2026-08-03: for this
        // coordinate ?zone=NYZ072 returned only a Flood Watch while ?point=
        // also returned a Flash Flood Warning. Assert the URL so a refactor
        // back to zones fails here rather than in a storm.
        val (client, transport) = client(HttpResult(200, """{"features":[]}""", null))
        client.getActiveAlertsAt(40.71, -74.01)
        assertTrue(
            transport.requested.last().contains("point=40.71,-74.01"),
            "expected a point query, got ${transport.requested.last()}",
        )
        assertFalse(transport.requested.last().contains("zone="))
    }

    @Test
    fun `getActiveAlerts maps features and skips entries missing id or event`() = runTest {
        val (client, _) =
            client(
                HttpResult(
                    200,
                    """
                    {"features":[
                      {"properties":{"id":"A1","event":"Heat Advisory","severity":"Moderate","headline":"Hot"}},
                      {"properties":{"severity":"Severe"}}
                    ]}
                    """.trimIndent(),
                    null,
                ),
            )
        val alerts = client.getActiveAlertsAt(40.71, -74.01)
        assertEquals(1, alerts.size)
        assertEquals("A1", alerts[0].id)
        assertEquals("Heat Advisory", alerts[0].event)
    }

    @Test
    fun `getHourlyForecast parses periods and the max of a wind range`() = runTest {
        val (client, _) =
            client(
                HttpResult(
                    200,
                    """
                    {"properties":{"periods":[
                      {"startTime":"2026-06-24T18:00:00+00:00","temperature":72,"temperatureUnit":"F","windSpeed":"5 to 10 mph"},
                      {"startTime":"bad-time","temperature":70,"temperatureUnit":"F","windSpeed":"5 mph"}
                    ]}}
                    """.trimIndent(),
                    null,
                ),
            )
        val forecast = client.getHourlyForecast("OKX", 33, 35)
        // The unparseable startTime drops that period.
        assertEquals(1, forecast.size)
        assertEquals(72.0, forecast[0].temperatureF!!, 1e-6)
        assertEquals(10.0, forecast[0].windMph!!, 1e-6)
    }

    @Test
    fun `getHourlyForecast reads precip probability and converts celsius temps`() = runTest {
        val (client, _) =
            client(
                HttpResult(
                    200,
                    """
                    {"properties":{"periods":[
                      {"startTime":"2026-06-24T18:00:00+00:00","temperature":20,"temperatureUnit":"C",
                       "windSpeed":"5 mph",
                       "probabilityOfPrecipitation":{"unitCode":"wmoUnit:percent","value":40}},
                      {"startTime":"2026-06-24T19:00:00+00:00","temperature":68,"temperatureUnit":"F",
                       "windSpeed":"5 mph"}
                    ]}}
                    """.trimIndent(),
                    null,
                ),
            )
        val forecast = client.getHourlyForecast("OKX", 33, 35)
        assertEquals(2, forecast.size)
        // 20°C → 68°F, and the precip chance comes through.
        assertEquals(68.0, forecast[0].temperatureF!!, 1e-6)
        assertEquals(40.0, forecast[0].precipProbabilityPercent!!, 1e-6)
        // A period without probabilityOfPrecipitation leaves it null, not zero.
        assertEquals(null, forecast[1].precipProbabilityPercent)
    }

    @Test
    fun `getDailyForecast parses named half-day periods and drops nameless ones`() = runTest {
        val (client, transport) =
            client(
                HttpResult(
                    200,
                    """
                    {"properties":{"periods":[
                      {"name":"Tonight","isDaytime":false,"temperature":18,"temperatureUnit":"C",
                       "shortForecast":"Partly Cloudy",
                       "probabilityOfPrecipitation":{"unitCode":"wmoUnit:percent","value":30}},
                      {"name":"Friday","isDaytime":true,"temperature":85,"temperatureUnit":"F",
                       "shortForecast":"Sunny"},
                      {"temperature":70,"temperatureUnit":"F","shortForecast":"No Name"}
                    ]}}
                    """.trimIndent(),
                    null,
                ),
            )
        val daily = client.getDailyForecast("OKX", 33, 35)
        // The nameless period is dropped — the name is the framing.
        assertEquals(2, daily.size)
        assertEquals("Tonight", daily[0].name)
        assertEquals(false, daily[0].isDaytime)
        // 18°C → 64.4°F, and the precip chance and summary come through.
        assertEquals(64.4, daily[0].temperatureF!!, 1e-6)
        assertEquals(30.0, daily[0].precipProbabilityPercent!!, 1e-6)
        assertEquals("Partly Cloudy", daily[0].shortForecast)
        assertEquals("Friday", daily[1].name)
        assertEquals(true, daily[1].isDaytime)
        // …and it hits the daily endpoint, not the hourly one.
        assertTrue(transport.requested.last().endsWith("/gridpoints/OKX/33,35/forecast"))
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
