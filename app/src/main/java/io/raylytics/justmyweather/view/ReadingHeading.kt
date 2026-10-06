package io.raylytics.justmyweather.view

/**
 * What a reading's line opens with: "Observed 12:40 PM" for a station's
 * measurement, "Forecast for 1:00 PM" for a place NWS does not cover, where
 * the reading is MET Norway's forecast for the current hour. One definition,
 * so the glance, the widget and the detail sheet can never call a forecast
 * observed. Pure.
 */
object ReadingHeading {
    /** Named beside a forecast reading: MET's data is CC BY 4.0, and this is
     * its attribution where the reading is shown. */
    const val FORECAST_SOURCE = "MET Norway"

    /** [time] is the reading's clock time, already formatted; null when the
     * reading carries none. */
    fun of(fromForecast: Boolean, time: String?): String =
        when {
            fromForecast && time != null -> "Forecast for $time"
            fromForecast -> "Forecast"
            time != null -> "Observed $time"
            else -> "Observed"
        }
}
