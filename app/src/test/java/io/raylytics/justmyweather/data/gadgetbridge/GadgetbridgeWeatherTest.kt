package io.raylytics.justmyweather.data.gadgetbridge

import io.raylytics.justmyweather.data.WeatherSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Every field in this payload is a bare number with no unit code attached, so
 * a wrong conversion shows a plausible-looking wrong reading on the watch
 * rather than failing. These tests assert the units explicitly.
 */
class GadgetbridgeWeatherTest {
    private val now = Instant.parse("2026-08-02T18:00:00Z")
    private val observed = Instant.parse("2026-08-02T17:45:00Z")

    private fun snapshot(
        temperatureF: Double? = 72.0,
        conditions: String? = "Mostly Cloudy",
        windMph: Double? = 10.0,
        humidity: Double? = 64.0,
        windDir: Double? = 210.0,
        observedAt: Instant? = observed,
    ) = WeatherSnapshot(
        locationLabel = "Louisville, KY",
        temperatureF = temperatureF,
        conditions = conditions,
        windMph = windMph,
        precipitationIn = null,
        pressureInHg = null,
        observedAt = observedAt,
        relativeHumidityPercent = humidity,
        windDirectionDegrees = windDir,
    )

    private fun fields(json: String?) = Json.parseToJsonElement(json!!).jsonObject

    @Test
    fun `temperature is Kelvin, not Fahrenheit or Celsius`() {
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(), now))
        // 72F = 22.2C = 295.4K. Guards against shipping 72 or 22.
        assertEquals(295, f["currentTemp"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `wind speed is km per hour, not mph`() {
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(windMph = 10.0), now))
        assertEquals(16.09344, f["windSpeed"]!!.jsonPrimitive.content.toDouble(), 1e-5)
    }

    @Test
    fun `timestamp is epoch seconds from the observation, not millis`() {
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(), now))
        assertEquals(observed.epochSecond, f["timestamp"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `timestamp falls back to now when the station omitted one`() {
        // Deliberate exception to the app's "never substitute the clock" rule:
        // an absent field serialises to 0 and the watch renders 1970.
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(observedAt = null), now))
        assertEquals(now.epochSecond, f["timestamp"]!!.jsonPrimitive.content.toLong())
    }

    @Test
    fun `condition text and mapped OWM code both travel`() {
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(), now))
        assertEquals("Mostly Cloudy", f["currentCondition"]!!.jsonPrimitive.content)
        assertEquals(803, f["currentConditionCode"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `humidity and wind direction are carried through`() {
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(), now))
        assertEquals(64, f["currentHumidity"]!!.jsonPrimitive.content.toInt())
        assertEquals(210, f["windDirection"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `unknown fields are omitted rather than sent as zero`() {
        // A watch reading 0% humidity or a 1970 timestamp would present
        // absence as a confident measurement.
        val json = GadgetbridgeWeather.payloadFor(snapshot(humidity = null, windDir = null, windMph = null), now)
        val f = fields(json)
        assertFalse(f.containsKey("currentHumidity"))
        assertFalse(f.containsKey("windDirection"))
        assertFalse(f.containsKey("windSpeed"))
        // The fields we do know are still present.
        assertTrue(f.containsKey("currentTemp"))
    }

    @Test
    fun `an unmapped condition omits the code but keeps the text`() {
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(conditions = "Blowing Widgets"), now))
        assertEquals("Blowing Widgets", f["currentCondition"]!!.jsonPrimitive.content)
        assertFalse(f.containsKey("currentConditionCode"))
    }

    @Test
    fun `no temperature means no payload at all`() {
        // Sending a templess payload would blank out a good previous reading
        // on the watch; refusing leaves the last known value in place.
        assertNull(GadgetbridgeWeather.payloadFor(snapshot(temperatureF = null), now))
    }

    @Test
    fun `location label travels verbatim`() {
        val f = fields(GadgetbridgeWeather.payloadFor(snapshot(), now))
        assertEquals("Louisville, KY", f["location"]!!.jsonPrimitive.content)
    }
}
