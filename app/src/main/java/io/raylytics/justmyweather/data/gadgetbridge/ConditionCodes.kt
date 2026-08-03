package io.raylytics.justmyweather.data.gadgetbridge

/*
 * NWS plain-language conditions → OpenWeatherMap condition codes.
 *
 * Gadgetbridge's WeatherSpec.currentConditionCode is an OpenWeatherMap code,
 * not anything NWS produces: watches and watch faces switch icons on it. NWS
 * gives us `textDescription` ("Mostly Cloudy", "Light Rain"), so something has
 * to translate, and that translation is the least certain part of this feature
 * — hence its own file and its own tests.
 *
 * Codes verified against openweathermap.org/weather-conditions (2026-08-02):
 * 800 clear, 801/802/803/804 increasing cloud cover, 5xx rain, 3xx drizzle,
 * 2xx thunderstorm, 6xx snow, 7xx atmosphere (mist/fog/haze/smoke).
 *
 * Two deliberate choices:
 *
 * 1. Matching is on lowercased substrings, most specific first, because NWS
 *    composes descriptions ("Light Rain Fog/Mist", "Thunderstorm in Vicinity")
 *    rather than drawing from a closed vocabulary. A first-match-wins ordered
 *    list is legible and debuggable; a regex soup would not be.
 * 2. Unmapped text returns null rather than a guess. The caller omits the
 *    field, which is honest, instead of asserting "clear sky" over a condition
 *    nobody mapped — the failure mode being a wrong icon on the wrist.
 */

/**
 * Ordered most-specific-first: "freezing rain" must be tested before "rain",
 * and "thunderstorm" before either, or a compound description resolves to the
 * blander code. Order is the correctness property here, not an accident.
 */
private val CONDITION_CODES: List<Pair<String, Int>> =
    listOf(
        // Severe first — these outrank any precipitation word they contain.
        "tornado" to 781,
        "squall" to 771,
        "funnel cloud" to 781,
        // Thunderstorms outrank the rain/drizzle they usually mention.
        "thunderstorm" to 211,
        "thunder" to 211,
        // Freezing/frozen precipitation before plain rain.
        "freezing rain" to 511,
        "freezing drizzle" to 511,
        "ice pellets" to 611,
        "sleet" to 611,
        "hail" to 611,
        "blizzard" to 602,
        "heavy snow" to 602,
        "light snow" to 600,
        "snow" to 601,
        "wintry mix" to 616,
        "rain and snow" to 616,
        // Showers before steady rain: NWS says "Rain Showers" for convective.
        "heavy rain" to 502,
        "light rain" to 500,
        "rain shower" to 521,
        "showers" to 521,
        "rain" to 501,
        "drizzle" to 301,
        // Obscurations. "fog/mist" is a single NWS token, so fog wins.
        "fog" to 741,
        "mist" to 701,
        "haze" to 721,
        "smoke" to 711,
        "volcanic ash" to 762,
        "dust" to 761,
        "sand" to 751,
        // Cloud cover, densest first so "mostly cloudy" can't match "cloudy"
        // and lose the distinction between 803 and 804.
        "overcast" to 804,
        "mostly cloudy" to 803,
        "partly cloudy" to 802,
        "partly sunny" to 802,
        "mostly sunny" to 801,
        "mostly clear" to 801,
        "few clouds" to 801,
        "scattered clouds" to 802,
        "broken clouds" to 803,
        "cloudy" to 804,
        "clear" to 800,
        "sunny" to 800,
        "fair" to 800,
    )

/**
 * The OpenWeatherMap condition code for an NWS description, or null when
 * nothing matches. Null is a real answer: the payload omits the field rather
 * than claiming a condition the mapping doesn't cover.
 */
fun openWeatherMapCodeFor(conditions: String?): Int? {
    val text = conditions?.trim()?.lowercase().orEmpty()
    if (text.isEmpty()) return null
    return CONDITION_CODES.firstOrNull { (needle, _) -> text.contains(needle) }?.second
}
