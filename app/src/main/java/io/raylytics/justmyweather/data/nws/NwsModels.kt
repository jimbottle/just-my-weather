package io.raylytics.justmyweather.data.nws

import java.time.Instant

/*
 * NWS-shaped domain models: the API's payloads after parsing and
 * unit-conversion. Distinct from the raw wire DTOs in NwsWire (which mirror the
 * JSON shapes) and from the UI's WeatherSnapshot.
 *
 * Ported from almanac-bell's `mobile/src/nws/types.ts`.
 */

/** The grid + zone + station handles NWS hands back for a lat/lon, plus the
 * nearest city/state the same response carries (null outside CONUS). */
data class PointsLookup(
    val gridId: String,
    val gridX: Int,
    val gridY: Int,
    val forecastZoneId: String,
    val observationStationId: String,
    val relativeLocation: RelativeLocation? = null,
)

/** City + state nearest a point, used to pre-fill a human-readable label. */
data class RelativeLocation(
    val city: String,
    val state: String,
)

/** Latest station observation, normalised to American units. */
data class CurrentObservation(
    /** When the station reported this; null if it omitted the timestamp. */
    val observedAt: Instant?,
    val temperatureF: Double?,
    val precipitationIn: Double?,
    val windMph: Double?,
    /** Sea-level pressure in inHg (standard atmosphere = 29.92). */
    val pressureInHg: Double?,
    /** Plain-language summary from the station ("Mostly Cloudy"), if present. */
    val conditions: String?,
)

/** One hour of the gridpoint hourly forecast. */
data class ForecastPoint(
    val startTime: Instant,
    val temperatureF: Double?,
    val windMph: Double?,
)

/** An active NWS hazard alert for a zone (used to coexist with, not duplicate,
 * the sibling hazard-alert app). */
data class ActiveAlert(
    val id: String,
    val event: String,
    val severity: String,
    val headline: String,
)
