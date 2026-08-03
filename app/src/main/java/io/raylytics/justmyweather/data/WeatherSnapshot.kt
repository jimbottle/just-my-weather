package io.raylytics.justmyweather.data

import java.time.Instant

/**
 * Everything the UI might show for one place at one moment, already in display
 * units. The home view reads only the fields the user has chosen to surface;
 * the rest are carried so the customization layer can promote them without a
 * re-fetch. This is the single domain object the UI layer depends on — the NWS
 * wire shapes never leak past the repository.
 */
data class WeatherSnapshot(
    val locationLabel: String,
    /** Whole-degree temperature is what people read at a glance. */
    val temperatureF: Double?,
    /** Plain-language conditions from the station ("Mostly Cloudy"). */
    val conditions: String?,
    val windMph: Double?,
    val precipitationIn: Double?,
    val pressureInHg: Double?,
    /** When the reading was taken; null when the station omitted it. */
    val observedAt: Instant?,
    /**
     * Carried for the Gadgetbridge export rather than the default glance —
     * WeatherSpec has fields for both, and sending 0 for an unknown humidity
     * would render as a confident "0%" on the watch. Defaulted so every other
     * construction site is unaffected.
     */
    val relativeHumidityPercent: Double? = null,
    /** Wind bearing in degrees clockwise from true north, 0–360. */
    val windDirectionDegrees: Double? = null,
)
