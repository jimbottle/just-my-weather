package io.raylytics.justmyweather.data.metno

import io.raylytics.justmyweather.data.nws.CurrentObservation
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.Units
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/*
 * The second weather source, for what NWS does not forecast. In NWS territory
 * that is days eight and nine: NWS's daily forecast ends after seven days
 * (fourteen half-day periods; its raw gridpoint data stops at the same place).
 * Everywhere else it is everything — the "now" reading, the hourly and the
 * daily — routed here by WeatherRepository when NWS says a point is not its
 * own. MET
 * Norway's Locationforecast — the API behind Yr — reaches nine days for the
 * whole world, is keyless, and its data is CC BY 4.0 with commercial use
 * allowed, which is why it replaced Open-Meteo (whose free tier is
 * non-commercial, and the Play build carries ads; Evan, 2026-10-03). The
 * detail sheet for an extended day names it as the source.
 *
 * MET's terms ask for three things, all honoured here or in the wiring: an
 * identifying User-Agent with a contact (sent on every request), no polling
 * more often than their Expires header allows (the transport AppContainer
 * gives this client has an HTTP cache, so OkHttp revalidates with
 * If-Modified-Since), and attribution (the detail sheet and App settings).
 *
 * Kept to the same shape as NwsClient — an injectable HttpTransport, wire
 * shapes private to this file, a cleaned-up model out — so it tests on the
 * JVM with a fake and swapping it touches only WeatherRepository.
 */

/** One day of the extended forecast, in American units. */
data class ExtendedDay(
    /** The day, in the PLACE's calendar. */
    val date: LocalDate,
    val highF: Double?,
    val lowF: Double?,
    /** The day's highest chance of precipitation, 0–100. MET only reports a
     * probability for some regions; elsewhere this is null and the tile shows
     * no chance line. */
    val precipChancePercent: Double?,
    /** Plain-language conditions ("Rain Showers"), from the midday symbol. */
    val conditions: String?,
    val windMph: Double?,
    /** Compass point the wind blows FROM ("SW"), as NWS words it. */
    val windDirection: String?,
    /** Total precipitation forecast for the day, in inches. */
    val precipIn: Double? = null,
    /** True for a day built from only the REST of today's hours (see
     * dailyView): its high and low are what is left of the day, and the
     * screen says so. MET's own fold never produces one. */
    val partial: Boolean = false,
)

class MetNoClient(
    private val transport: HttpTransport,
    private val baseUrl: String = "https://api.met.no",
    /** MET requires a User-Agent that identifies the app and a contact; a
     * generic one is throttled, then blocked. */
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    @Serializable
    private data class Response(val properties: Properties? = null)

    @Serializable
    private data class Properties(val timeseries: List<Entry> = emptyList())

    @Serializable
    private data class Entry(val time: String, val data: Data = Data())

    @Serializable
    private data class Data(
        val instant: Block? = null,
        @SerialName("next_1_hours") val nextHour: Block? = null,
        @SerialName("next_6_hours") val nextSixHours: Block? = null,
    )

    @Serializable
    private data class Block(val summary: Summary? = null, val details: Details = Details())

    @Serializable
    private data class Summary(
        @SerialName("symbol_code") val symbolCode: String? = null,
    )

    @Serializable
    private data class Details(
        @SerialName("air_temperature") val airTemperatureC: Double? = null,
        @SerialName("air_temperature_max") val maxTemperatureC: Double? = null,
        @SerialName("air_temperature_min") val minTemperatureC: Double? = null,
        @SerialName("precipitation_amount") val precipitationMm: Double? = null,
        @SerialName("probability_of_precipitation") val precipitationChance: Double? = null,
        @SerialName("wind_speed") val windSpeedMps: Double? = null,
        @SerialName("wind_from_direction") val windFromDegrees: Double? = null,
        @SerialName("relative_humidity") val relativeHumidityPercent: Double? = null,
        @SerialName("dew_point_temperature") val dewPointC: Double? = null,
        @SerialName("air_pressure_at_sea_level") val seaLevelPressureHpa: Double? = null,
        @SerialName("apparent_air_temperature") val apparentTemperatureC: Double? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Up to nine days from today in [zone], the place's own calendar. The
     * series is hourly for the first days and six-hourly after, each entry
     * carrying a `next_6_hours` block, so the day is folded from the
     * NON-overlapping six-hour blocks (those starting at 00/06/12/18 UTC) —
     * in the six-hourly tail that is every entry, and in the hourly head it
     * is one entry in six. The series starts and ends partway through a
     * local day, so a day is kept only when it has at least three of its
     * four blocks AND the one holding local noon — otherwise a "day 9" built
     * from two morning blocks would show the overnight low as its high.
     */
    suspend fun getDailyForecast(latitude: Double, longitude: Double, zone: ZoneId): List<ExtendedDay> {
        val entries = timeseries(latitude, longitude)
        return entries
            .mapNotNull { entry ->
                val at = entry.at() ?: return@mapNotNull null
                val block = entry.data.nextSixHours ?: return@mapNotNull null
                val local = at.atZone(zone)
                if (at.atZone(UTC).hour % 6 != 0) return@mapNotNull null
                SixHours(local.toLocalDate(), local.toLocalTime(), block, entry.data.instant?.details)
            }
            .groupBy { it.date }
            .toSortedMap()
            .filterValues { blocks -> blocks.size >= 3 && blocks.any { it.holdsNoon } }
            .map { (date, blocks) -> fold(date, blocks) }
    }

    /**
     * A "now" reading for a place NWS does not cover: MET's forecast for the
     * current hour, since MET has no station observations to offer. It is the
     * entry whose hour has begun most recently; the series starts at the top
     * of the current or the coming hour, so that is nearly always the first.
     *
     * [CurrentObservation.observedAt] is that hour's start — what the reading
     * is FOR, not when anyone measured it — and the caller marks the snapshot
     * as a forecast so the glance doesn't call it observed.
     */
    suspend fun getNow(latitude: Double, longitude: Double, now: Instant): CurrentObservation {
        val entries = timeseries(latitude, longitude).mapNotNull { entry -> entry.at()?.let { it to entry.data } }
        val (at, data) =
            entries.lastOrNull { (at, _) -> !at.isAfter(now) }
                ?: entries.firstOrNull()
                ?: error("met.no returned no forecast for $latitude,$longitude")
        val details = data.instant?.details ?: Details()
        val airC = details.airTemperatureC
        // MET's apparent temperature is the same air temperature whenever no
        // heat or wind adjustment applies. The snapshot's contract is "null
        // when it feels like the temperature", which NWS meets by sending no
        // heat index or wind chill; meet it here the same way, at the
        // precision the glance shows (whole degrees).
        val feelsLikeC =
            details.apparentTemperatureC?.takeIf { apparent ->
                airC == null || Math.round(Units.celsiusToFahrenheit(apparent)) !=
                    Math.round(Units.celsiusToFahrenheit(airC))
            }
        val hour = data.nextHour ?: data.nextSixHours
        return CurrentObservation(
            observedAt = at,
            temperatureF = airC?.let(Units::celsiusToFahrenheit),
            precipitationIn = Units.toInches(data.nextHour?.details?.precipitationMm, "mm"),
            windMph = Units.toMph(details.windSpeedMps, "m/s"),
            pressureInHg = Units.toInchesOfMercury(details.seaLevelPressureHpa, "hPa"),
            conditions = hour?.summary?.symbolCode?.let(MetNoSymbols::describe),
            relativeHumidityPercent = details.relativeHumidityPercent,
            windDirectionDegrees = details.windFromDegrees,
            feelsLikeF = feelsLikeC?.let(Units::celsiusToFahrenheit),
        )
    }

    /**
     * The hourly forecast for a place NWS does not cover, in the same shape
     * NWS's hourly takes. Only the hourly head of MET's series is used —
     * about the next two and a half days. Past that MET steps every six
     * hours, and a six-hour step drawn as one more "hour" tile would read as
     * an hour it isn't; the Daily view is where those days belong.
     */
    suspend fun getHourlyForecast(latitude: Double, longitude: Double): List<ForecastPoint> =
        timeseries(latitude, longitude).mapNotNull { entry ->
            val at = entry.at() ?: return@mapNotNull null
            val hour = entry.data.nextHour ?: return@mapNotNull null
            val details = entry.data.instant?.details ?: Details()
            ForecastPoint(
                startTime = at,
                temperatureF = details.airTemperatureC?.let(Units::celsiusToFahrenheit),
                windMph = Units.toMph(details.windSpeedMps, "m/s"),
                // Only reported for some regions (the Nordics); null elsewhere,
                // and the tile then shows no chance line — as for a NWS hour
                // without one.
                precipProbabilityPercent = hour.details.precipitationChance,
                shortForecast = hour.summary?.symbolCode?.let(MetNoSymbols::describe),
                windDirection = Units.compassPoint(details.windFromDegrees),
                relativeHumidityPercent = details.relativeHumidityPercent,
                dewpointF = details.dewPointC?.let(Units::celsiusToFahrenheit),
            )
        }

    /** The whole series for a coordinate. Every read goes through here, so
     * the request — and MET's terms that ride on it — has one definition. A
     * glance, an hourly and a daily asked for together cost one download:
     * the HTTP cache the transport carries answers the second and third from
     * disk until MET's Expires header says the forecast has moved on. */
    private suspend fun timeseries(latitude: Double, longitude: Double): List<Entry> {
        val url =
            String.format(
                Locale.US,
                "%s/weatherapi/locationforecast/2.0/complete?lat=%.4f&lon=%.4f",
                baseUrl,
                latitude,
                longitude,
            )
        val result = transport.get(url, mapOf("User-Agent" to userAgent, "Accept" to "application/json"))
        check(result.status == 200) { "met.no returned ${result.status}" }
        return json.decodeFromString<Response>(result.body).properties?.timeseries.orEmpty()
    }

    private fun Entry.at(): Instant? = runCatching { Instant.parse(time) }.getOrNull()

    private class SixHours(val date: LocalDate, val start: LocalTime, val block: Block, val instant: Details?) {
        /** Whether noon falls in this block's half-open [start, start + 6h).
         * Checked at minute precision: a zone six hours off UTC starts its
         * blocks at 06:00 and 12:00 exactly, and one at 06:30 (Myanmar)
         * holds noon while a whole-hour test would say it doesn't. */
        val holdsNoon: Boolean get() = !start.isAfter(LocalTime.NOON) && start.plusHours(6).isAfter(LocalTime.NOON)
    }

    private fun fold(date: LocalDate, blocks: List<SixHours>): ExtendedDay {
        val highC = blocks.mapNotNull { it.block.details.maxTemperatureC ?: it.instant?.airTemperatureC }.maxOrNull()
        val lowC = blocks.mapNotNull { it.block.details.minTemperatureC ?: it.instant?.airTemperatureC }.minOrNull()
        val windiest =
            blocks.mapNotNull { it.instant }.filter { it.windSpeedMps != null }.maxByOrNull { it.windSpeedMps!! }
        val precipMm = blocks.mapNotNull { it.block.details.precipitationMm }.takeIf { it.isNotEmpty() }?.sum()
        // The block noon falls in describes the day the way a person would.
        val midday = blocks.firstOrNull { it.holdsNoon }
        return ExtendedDay(
            date = date,
            highF = highC?.let(Units::celsiusToFahrenheit),
            lowF = lowC?.let(Units::celsiusToFahrenheit),
            precipChancePercent = blocks.mapNotNull { it.block.details.precipitationChance }.maxOrNull(),
            conditions = midday?.block?.summary?.symbolCode?.let(MetNoSymbols::describe),
            windMph = Units.toMph(windiest?.windSpeedMps, "m/s"),
            windDirection = Units.compassPoint(windiest?.windFromDegrees),
            precipIn = Units.toInches(precipMm, "mm"),
        )
    }

    companion object {
        const val DEFAULT_USER_AGENT = "just-my-weather (dev@raylytics.io)"
        private val UTC: ZoneId = ZoneId.of("UTC")
    }
}

/**
 * MET's weather symbols ("lightrainshowers_day") in words close to NWS's own,
 * so an extended day reads like the NWS days before it. The `_day`, `_night`
 * and `_polartwilight` suffixes only choose an icon and are dropped. Pure; an
 * unknown symbol is null rather than a guess.
 */
object MetNoSymbols {
    fun describe(symbol: String): String? =
        when (symbol.substringBefore('_')) {
            "clearsky" -> "Clear"
            "fair" -> "Mostly Clear"
            "partlycloudy" -> "Partly Cloudy"
            "cloudy" -> "Cloudy"
            "fog" -> "Fog"
            "lightrain" -> "Light Rain"
            "rain" -> "Rain"
            "heavyrain" -> "Heavy Rain"
            "lightrainshowers", "rainshowers" -> "Rain Showers"
            "heavyrainshowers" -> "Heavy Rain Showers"
            "lightsleet", "sleet", "heavysleet" -> "Sleet"
            "lightsleetshowers", "sleetshowers", "heavysleetshowers" -> "Sleet Showers"
            "lightsnow" -> "Light Snow"
            "snow" -> "Snow"
            "heavysnow" -> "Heavy Snow"
            "lightsnowshowers", "snowshowers", "heavysnowshowers" -> "Snow Showers"
            "lightrainandthunder", "rainandthunder", "heavyrainandthunder",
            "lightrainshowersandthunder", "rainshowersandthunder", "heavyrainshowersandthunder",
            -> "Thunderstorms"
            "lightsleetandthunder", "sleetandthunder", "heavysleetandthunder",
            "lightssleetshowersandthunder", "sleetshowersandthunder", "heavysleetshowersandthunder",
            "lightsnowandthunder", "snowandthunder", "heavysnowandthunder",
            "lightssnowshowersandthunder", "snowshowersandthunder", "heavysnowshowersandthunder",
            -> "Wintry Mix With Thunder"
            else -> null
        }
}
