package io.raylytics.justmyweather.data.metno

import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.Units
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/*
 * The second weather source, and only for what NWS does not forecast: days
 * eight and nine. NWS's daily forecast ends after seven days (fourteen
 * half-day periods; its raw gridpoint data stops at the same place). MET
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
        val entries = json.decodeFromString<Response>(result.body).properties?.timeseries ?: return emptyList()
        return entries
            .mapNotNull { entry ->
                val at = runCatching { Instant.parse(entry.time) }.getOrNull() ?: return@mapNotNull null
                val block = entry.data.nextSixHours ?: return@mapNotNull null
                val local = at.atZone(zone)
                if (at.atZone(UTC).hour % 6 != 0) return@mapNotNull null
                SixHours(local.toLocalDate(), local.hour, block, entry.data.instant?.details)
            }
            .groupBy { it.date }
            .toSortedMap()
            .filterValues { blocks -> blocks.size >= 3 && blocks.any { it.holdsNoon } }
            .map { (date, blocks) -> fold(date, blocks) }
    }

    private class SixHours(val date: LocalDate, val localHour: Int, val block: Block, val instant: Details?) {
        /** Six-hour blocks start six hours apart, so exactly one per day has
         * its midpoint within three hours of noon: the one noon falls in. */
        val holdsNoon: Boolean get() = abs(localHour + 3 - 12) < 3
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
