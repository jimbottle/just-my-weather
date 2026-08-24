package io.raylytics.justmyweather.data.nws

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Client for the public National Weather Service API (api.weather.gov).
 *
 * Ported from almanac-bell's `mobile/src/nws/client.ts`: same User-Agent and
 * `Accept: application/geo+json` headers, same retry-on-429/503 policy honouring
 * `Retry-After`, same endpoints. No API key — NWS is free US-government data.
 *
 * The HTTP call itself is behind [HttpTransport] (production: OkHttp) so the
 * retry/parse logic can be unit-tested with a fake transport, no network. This
 * mirrors the TypeScript client's injectable `fetchImpl`.
 */
class NwsClient(
    private val transport: HttpTransport,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val maxAttempts: Int = 3,
    private val json: Json = defaultJson,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun resolveLocation(lat: Double, lon: Double): PointsLookup {
        val points = getJson<NwsPointsResponse>("/points/$lat,$lon")
        val props = points.properties ?: error("nws /points/$lat,$lon response missing properties")
        val stationId = firstStation(lat, lon)
        // forecastZone is a URL like ".../zones/forecast/OHZ001"; we want the id.
        val zoneId = (props.forecastZone ?: "").substringAfterLast('/')
        if (zoneId.isEmpty()) {
            error("nws /points/$lat,$lon response missing forecastZone")
        }
        return PointsLookup(
            gridId = props.gridId ?: error("nws /points/$lat,$lon missing gridId"),
            gridX = props.gridX ?: error("nws /points/$lat,$lon missing gridX"),
            gridY = props.gridY ?: error("nws /points/$lat,$lon missing gridY"),
            forecastZoneId = zoneId,
            observationStationId = stationId,
            // Not fatal if absent: everything still works, times just fall
            // back to the device's zone.
            timeZone = props.timeZone,
            // The /points response already carries the nearest city/state, so
            // we keep it here rather than re-fetching /points just for a label.
            relativeLocation = props.toRelativeLocation(),
        )
    }

    suspend fun getObservation(stationId: String): CurrentObservation {
        val body = getJson<NwsObservationResponse>("/stations/$stationId/observations/latest")
        val props = body.properties ?: error("nws observation for $stationId missing properties")
        val temp = props.temperature
        val precip = props.precipitationLastHour
        val wind = props.windSpeed
        // Prefer sea-level adjusted pressure (the "29.92 inHg" TV reports
        // quote); fall back to the raw barometric reading.
        val slp = props.seaLevelPressure
        val bp = props.barometricPressure
        return CurrentObservation(
            // Null when the station omits a timestamp — never substitute the
            // current clock, which would render absent data as freshly observed.
            observedAt = props.timestamp?.let(Instant::parse),
            temperatureF = Units.toFahrenheit(temp?.value, temp?.unitCode ?: ""),
            precipitationIn = Units.toInches(precip?.value, precip?.unitCode ?: ""),
            windMph = Units.toMph(wind?.value, wind?.unitCode ?: ""),
            pressureInHg = Units.toInchesOfMercury(slp?.value, slp?.unitCode ?: "")
                ?: Units.toInchesOfMercury(bp?.value, bp?.unitCode ?: ""),
            conditions = props.textDescription?.takeIf { it.isNotBlank() },
            // Percent and degrees respectively, both already in the units we
            // want, so they pass through rather than going via Units. Guarded
            // anyway: NWS sends `wmoUnit:percent` and `wmoUnit:degree_(angle)`,
            // and a station reporting something else would otherwise arrive as
            // a bare number in the wrong scale.
            relativeHumidityPercent = props.relativeHumidity?.takeIf {
                it.unitCode?.contains("percent") == true
            }?.value,
            windDirectionDegrees = props.windDirection?.takeIf {
                it.unitCode?.contains("degree") == true
            }?.value,
        )
    }

    /**
     * Active alerts covering a coordinate.
     *
     * Queried by POINT, not by forecast zone, and that distinction decides
     * whether tornado warnings ever appear. NWS issues storm-based warnings
     * — tornado, severe thunderstorm, flash flood — against COUNTY zones
     * (NYC005) or polygons, while the forecast zone is a different set
     * (NYZ072). Verified against the live API on 2026-08-03: for 40.71,-74.01
     * `?zone=NYZ072` returned only a Flood Watch, while `?point=` returned
     * that AND a Flash Flood Warning whose affectedZones were all county
     * codes. Asking by zone silently drops the most urgent category there is.
     */
    suspend fun getActiveAlertsAt(latitude: Double, longitude: Double): List<ActiveAlert> {
        val body = getJson<NwsAlertsResponse>("/alerts/active?point=$latitude,$longitude")
        return body.features.mapNotNull { feature ->
            val props = feature.properties
            val alertId = props?.id ?: feature.id ?: return@mapNotNull null
            val event = props?.event ?: return@mapNotNull null
            ActiveAlert(
                id = alertId,
                event = event,
                severity = props.severity ?: "Unknown",
                headline = props.headline ?: "",
            )
        }
    }

    suspend fun getHourlyForecast(gridId: String, gridX: Int, gridY: Int): List<ForecastPoint> {
        val body = getJson<NwsForecastResponse>("/gridpoints/$gridId/$gridX,$gridY/forecast/hourly")
        return body.properties?.periods.orEmpty().mapNotNull { p ->
            val start = p.startTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val temperatureF =
                p.temperature?.let { if (p.temperatureUnit == "C") Units.celsiusToFahrenheit(it) else it }
            ForecastPoint(
                startTime = start,
                temperatureF = temperatureF,
                windMph = Units.parseWindSpeedString(p.windSpeed),
                precipProbabilityPercent = p.probabilityOfPrecipitation?.value,
                shortForecast = p.shortForecast?.takeIf { it.isNotBlank() },
            )
        }
    }

    /** The daily forecast: half-day periods NWS pre-names ("Tonight",
     * "Friday"), a daytime high or overnight low each. Periods missing a name
     * are dropped — the name is the whole point of this framing. */
    suspend fun getDailyForecast(gridId: String, gridX: Int, gridY: Int): List<DailyPeriod> {
        val body = getJson<NwsForecastResponse>("/gridpoints/$gridId/$gridX,$gridY/forecast")
        return body.properties?.periods.orEmpty().mapNotNull { p ->
            val name = p.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val temperatureF =
                p.temperature?.let { if (p.temperatureUnit == "C") Units.celsiusToFahrenheit(it) else it }
            DailyPeriod(
                name = name,
                isDaytime = p.isDaytime ?: true,
                temperatureF = temperatureF,
                shortForecast = p.shortForecast?.takeIf { it.isNotBlank() },
                precipProbabilityPercent = p.probabilityOfPrecipitation?.value,
            )
        }
    }

    private suspend fun firstStation(lat: Double, lon: Double): String {
        val body = getJson<NwsStationsResponse>("/points/$lat,$lon/stations")
        return body.features.firstOrNull()?.properties?.stationIdentifier
            ?: error("no observation stations near $lat,$lon")
    }

    /** City/state from the /points block, or null when NWS omits it (offshore
     * points, lat/lon outside CONUS). */
    private fun NwsPointsResponse.PointsProperties.toRelativeLocation(): RelativeLocation? {
        val rel = relativeLocation?.properties
        val city = rel?.city
        val state = rel?.state
        if (city.isNullOrEmpty() || state.isNullOrEmpty()) return null
        return RelativeLocation(city, state)
    }

    private suspend inline fun <reified T> getJson(path: String): T {
        repeat(maxAttempts) { attempt ->
            val resp = transport.get(baseUrl + path, requestHeaders)
            val retryable = resp.status == 429 || resp.status == 503
            if (retryable && attempt + 1 < maxAttempts) {
                val delaySec = minOf(Units.parseRetryAfter(resp.retryAfter), RETRY_CAP_SECONDS)
                sleep((delaySec * 1000).toLong())
                return@repeat
            }
            if (resp.status !in 200..299) {
                throw NwsHttpException(resp.status, path, resp.body)
            }
            return json.decodeFromString<T>(resp.body)
        }
        error("nws getJson exhausted $maxAttempts attempts for $path")
    }

    private val requestHeaders
        get() = mapOf("User-Agent" to userAgent, "Accept" to "application/geo+json")

    companion object {
        const val DEFAULT_BASE_URL = "https://api.weather.gov"
        const val DEFAULT_USER_AGENT = "just-my-weather (dev@raylytics.io)"
        private const val RETRY_CAP_SECONDS = 5.0

        val defaultJson = Json { ignoreUnknownKeys = true }
    }
}

/** Non-2xx (after retries) from NWS. Carries enough to surface a useful error. */
class NwsHttpException(
    val status: Int,
    val path: String,
    val body: String,
) : Exception("nws $status on $path: $body")
