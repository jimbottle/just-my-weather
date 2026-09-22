package io.raylytics.justmyweather.data.openmeteo

import io.raylytics.justmyweather.data.nws.HttpTransport
import io.raylytics.justmyweather.data.nws.Units
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale

/*
 * The second weather source, and only for what NWS does not forecast: days
 * eight through fourteen. NWS's daily forecast ends after seven days (fourteen
 * half-day periods; its raw gridpoint data stops at the same place), and the
 * user asked for fourteen. Open-Meteo is free and keyless like NWS, with a
 * sixteen-day daily forecast. Its data is CC BY 4.0: the detail sheet for an
 * extended day names it as the source.
 *
 * Kept to the same shape as NwsClient — an injectable HttpTransport, wire
 * shapes private to this file, a cleaned-up model out — so it tests on the
 * JVM with a fake and swapping it touches only WeatherRepository.
 */

/** One day of Open-Meteo's daily forecast, in American units. */
data class ExtendedDay(
    /** The day, in the PLACE's calendar (the request asks for the place's
     * zone). */
    val date: LocalDate,
    val highF: Double?,
    val lowF: Double?,
    /** The day's highest hourly chance of precipitation, 0–100. */
    val precipChancePercent: Double?,
    /** Plain-language conditions from the WMO weather code ("Rain showers"). */
    val conditions: String?,
    val windMph: Double?,
    /** Compass point the wind blows FROM ("SW"), as NWS words it. */
    val windDirection: String?,
)

class OpenMeteoClient(
    private val transport: HttpTransport,
    private val baseUrl: String = "https://api.open-meteo.com",
) {
    @Serializable
    private data class Response(val daily: Daily? = null)

    @Serializable
    private data class Daily(
        val time: List<String> = emptyList(),
        @kotlinx.serialization.SerialName("temperature_2m_max") val high: List<Double?> = emptyList(),
        @kotlinx.serialization.SerialName("temperature_2m_min") val low: List<Double?> = emptyList(),
        @kotlinx.serialization.SerialName("precipitation_probability_max") val chance: List<Double?> = emptyList(),
        @kotlinx.serialization.SerialName("weather_code") val code: List<Int?> = emptyList(),
        @kotlinx.serialization.SerialName("wind_speed_10m_max") val wind: List<Double?> = emptyList(),
        @kotlinx.serialization.SerialName("wind_direction_10m_dominant") val windFrom: List<Double?> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Sixteen days from today in the place's own calendar. A day whose date
     * does not parse is dropped; every other field may be null. */
    suspend fun getDailyForecast(latitude: Double, longitude: Double): List<ExtendedDay> {
        val url =
            String.format(
                Locale.US,
                "%s/v1/forecast?latitude=%.4f&longitude=%.4f&daily=%s" +
                    "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=16",
                baseUrl,
                latitude,
                longitude,
                DAILY_FIELDS,
            )
        val result = transport.get(url, emptyMap())
        check(result.status == 200) { "open-meteo returned ${result.status}" }
        val daily = json.decodeFromString<Response>(result.body).daily ?: return emptyList()
        return daily.time.mapIndexedNotNull { i, raw ->
            val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return@mapIndexedNotNull null
            ExtendedDay(
                date = date,
                highF = daily.high.getOrNull(i),
                lowF = daily.low.getOrNull(i),
                precipChancePercent = daily.chance.getOrNull(i),
                conditions = daily.code.getOrNull(i)?.let(WmoCodes::describe),
                windMph = daily.wind.getOrNull(i),
                windDirection = Units.compassPoint(daily.windFrom.getOrNull(i)),
            )
        }
    }

    private companion object {
        const val DAILY_FIELDS =
            "temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
                "weather_code,wind_speed_10m_max,wind_direction_10m_dominant"
    }
}

/**
 * WMO weather-interpretation codes, as Open-Meteo reports them, in words close
 * to NWS's own so an extended day reads like the NWS days before it. Pure; an
 * unknown code is null rather than a guess.
 */
object WmoCodes {
    fun describe(code: Int): String? =
        when (code) {
            0 -> "Clear"
            1 -> "Mostly Clear"
            2 -> "Partly Cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61 -> "Light Rain"
            63 -> "Rain"
            65 -> "Heavy Rain"
            66, 67 -> "Freezing Rain"
            71 -> "Light Snow"
            73 -> "Snow"
            75 -> "Heavy Snow"
            77 -> "Snow Grains"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95 -> "Thunderstorms"
            96, 99 -> "Thunderstorms With Hail"
            else -> null
        }
}
