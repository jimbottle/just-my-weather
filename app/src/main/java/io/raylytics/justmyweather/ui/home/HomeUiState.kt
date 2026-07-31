package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.ViewConfig
import io.raylytics.justmyweather.view.ViewMode

/**
 * The complete state of the home screen as one value. A sealed hierarchy keeps
 * the screen honest: every state it can render is enumerated here, so a glance
 * at this file tells you exactly what the user can ever see.
 */
sealed interface HomeUiState {
    /** First paint, before any data. Stays visually quiet — no spinner storm. */
    data object Loading : HomeUiState

    /** The normal case: a snapshot to glance at, projected through the user's
     * [config] so the screen draws exactly the fields they chose, in order.
     * [mode] picks the time framing; the forecast for a framing is null until
     * its first fetch lands (the screen shows a quiet placeholder). */
    data class Ready(
        val snapshot: WeatherSnapshot,
        val config: ViewConfig,
        val refreshing: Boolean = false,
        val mode: ViewMode = ViewMode.NOW,
        val hourly: List<ForecastPoint>? = null,
        val daily: List<DailyPeriod>? = null,
        /** Short message when the selected framing's fetch failed; the Now
         * glance is unaffected. */
        val forecastError: String? = null,
    ) : HomeUiState

    /** Network or NWS failure, with a short plain-language message. */
    data class Error(val message: String) : HomeUiState
}
