package io.raylytics.justmyweather.data.nws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw NWS wire shapes for kotlinx.serialization. These mirror the JSON the API
 * returns; [NwsClient] projects them into the cleaned-up models in [NwsModels].
 * Every field is nullable because NWS omits values freely (a station with no
 * precip sensor simply leaves `precipitationLastHour.value` null), and the Json
 * parser is configured with `ignoreUnknownKeys = true` so the dozens of fields
 * we don't read never break decoding.
 */
@Serializable
data class NwsValue(
    val value: Double? = null,
    val unitCode: String? = null,
)

@Serializable
data class NwsPointsResponse(
    val properties: PointsProperties? = null,
) {
    @Serializable
    data class PointsProperties(
        val gridId: String? = null,
        val gridX: Int? = null,
        val gridY: Int? = null,
        val forecastZone: String? = null,
        val relativeLocation: RelativeLocationFeature? = null,
    )

    @Serializable
    data class RelativeLocationFeature(
        val properties: RelativeLocationProps? = null,
    )

    @Serializable
    data class RelativeLocationProps(
        val city: String? = null,
        val state: String? = null,
    )
}

@Serializable
data class NwsStationsResponse(
    val features: List<StationFeature> = emptyList(),
) {
    @Serializable
    data class StationFeature(
        val properties: StationProps? = null,
    )

    @Serializable
    data class StationProps(
        val stationIdentifier: String? = null,
    )
}

@Serializable
data class NwsObservationResponse(
    val properties: ObservationProps? = null,
) {
    @Serializable
    data class ObservationProps(
        val timestamp: String? = null,
        val temperature: NwsValue? = null,
        val precipitationLastHour: NwsValue? = null,
        val windSpeed: NwsValue? = null,
        val seaLevelPressure: NwsValue? = null,
        val barometricPressure: NwsValue? = null,
        val textDescription: String? = null,
    )
}

@Serializable
data class NwsAlertsResponse(
    val features: List<AlertFeature> = emptyList(),
) {
    @Serializable
    data class AlertFeature(
        val id: String? = null,
        val properties: AlertProps? = null,
    )

    @Serializable
    data class AlertProps(
        val id: String? = null,
        val event: String? = null,
        val severity: String? = null,
        val headline: String? = null,
    )
}

@Serializable
data class NwsForecastResponse(
    val properties: ForecastProps? = null,
) {
    @Serializable
    data class ForecastProps(
        val periods: List<ForecastPeriod> = emptyList(),
    )

    @Serializable
    data class ForecastPeriod(
        val startTime: String? = null,
        val temperature: Double? = null,
        val temperatureUnit: String? = null,
        val windSpeed: String? = null,
        @SerialName("shortForecast") val shortForecast: String? = null,
    )
}
