package io.raylytics.justmyweather.data.gadgetbridge

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.Units
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.math.roundToInt

/*
 * Builds the JSON Gadgetbridge expects for a weather update, so it can reach a
 * paired watch (here: a Bangle.js 2, whose clock faces read temperature and
 * wind from it).
 *
 * Pure on purpose — snapshot in, string out, no Android types and no clock
 * reads beyond the injected `now`. The Intent side lives in
 * GadgetbridgeBroadcaster; this file is the part worth testing exhaustively,
 * because every field here is a bare number with no unit code attached and a
 * wrong one shows a plausible-looking wrong reading rather than failing.
 *
 * Field names and units are WeatherSpec's, verified 2026-08-02 against
 * WeatherSpec.java and gadgetbridge.org/internals/development/weather-support:
 *
 *   currentTemp / todayMinTemp / todayMaxTemp  KELVIN (not C, not F)
 *   windSpeed                                  KM/H
 *   windDirection                              degrees clockwise from north
 *   currentHumidity                            percent
 *   currentConditionCode                       OpenWeatherMap code (see ConditionCodes)
 *   timestamp                                  epoch SECONDS
 */
object GadgetbridgeWeather {
    /** Compact output: this rides in an Intent extra, not a log. */
    private val json = Json

    /**
     * The WeatherJson payload for [snapshot], or null when there is nothing
     * worth sending.
     *
     * Null when the temperature is unknown: temperature is the one field every
     * watch face reads, and a payload without it would replace a good previous
     * reading on the watch with an empty one. Refusing to send leaves the last
     * known value in place, which is the better failure.
     *
     * [now] is injected for deterministic tests and is used only as the
     * timestamp fallback described below.
     */
    fun payloadFor(snapshot: WeatherSnapshot, now: Instant = Instant.now()): String? {
        val tempF = snapshot.temperatureF ?: return null

        val obj =
            buildJsonObject {
                // Seconds, not millis. Falling back to `now` when the station
                // omitted its timestamp is a deliberate exception to this
                // project's "never substitute the clock for a missing
                // observation time" rule: that rule protects the UI from
                // showing stale data as fresh, whereas here an absent field
                // serialises to 0 and the watch renders 1970 — strictly worse
                // than "about now" for a consumer that keys freshness off it.
                put("timestamp", (snapshot.observedAt ?: now).epochSecond)
                put("location", snapshot.locationLabel)
                put("currentTemp", Units.fahrenheitToKelvin(tempF).roundToInt())

                snapshot.conditions?.takeIf { it.isNotBlank() }?.let { put("currentCondition", it) }
                // Omitted rather than guessed when the mapping has no entry —
                // an unmapped code would put a confidently wrong icon on the
                // watch. See ConditionCodes.
                openWeatherMapCodeFor(snapshot.conditions)?.let { put("currentConditionCode", it) }

                snapshot.relativeHumidityPercent?.let { put("currentHumidity", it.roundToInt()) }
                snapshot.windMph?.let { put("windSpeed", Units.mphToKmh(it)) }
                snapshot.windDirectionDegrees?.let { put("windDirection", it.roundToInt()) }
            }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj)
    }
}
