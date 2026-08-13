package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.SunEvents
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ActiveAlert
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
        val mode: ViewMode = ViewMode.DEFAULT,
        val hourly: List<ForecastPoint>? = null,
        val daily: List<DailyPeriod>? = null,
        /** Short message when the selected framing's fetch failed; the Now
         * glance is unaffected. */
        val forecastError: String? = null,
        /**
         * Active safety alerts for this location, worst first, already
         * filtered by SafetyAlerts. Empty on the overwhelming majority of
         * days — the banner renders only when this isn't.
         */
        val safetyAlerts: List<ActiveAlert> = emptyList(),
        /**
         * Set when a refresh failed while this reading was already on screen.
         * The glance above it is the last reading we know to be true — which
         * is why the failure is a line beside it rather than the [Error] state
         * that replaces the whole screen. The "Observed" timestamp is what
         * tells the user how old that reading now is.
         */
        val refreshError: String? = null,
        /**
         * The next sunrise and sunset, or null when the user hasn't switched
         * them on. Either event inside can still be null on its own: above the
         * Arctic circle the sun may not rise for months, and saying nothing is
         * better than inventing a time.
         */
        val sunEvents: SunEvents? = null,
    ) : HomeUiState

    /** Network or NWS failure, with a short plain-language message. */
    data class Error(val message: String) : HomeUiState
}
