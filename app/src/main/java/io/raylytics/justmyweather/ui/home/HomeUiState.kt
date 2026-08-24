package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.DailyPeriod
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ViewConfig
import java.time.ZoneId

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
     * [forecastMode] picks the forecast grid's framing; that grid's data is
     * null until its first fetch lands (it shows a quiet placeholder), and the
     * grid is absent altogether when the config has it switched off. */
    data class Ready(
        val snapshot: WeatherSnapshot,
        val config: ViewConfig,
        val refreshing: Boolean = false,
        val forecastMode: ForecastMode = ForecastMode.DEFAULT,
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
         * Sun times per day, today first — empty when the user hasn't switched
         * them on. A day rather than "the next of each" because those two fall
         * on different dates for most of the waking hours, and a row carrying
         * its own date needs no qualifier. Either time within a day can still
         * be null: above the Arctic circle the sun may not rise for months.
         */
        val sunDays: List<SunDay> = emptyList(),
        /**
         * The zone every time on screen is formatted in: the PLACE's, from the
         * NWS point lookup, falling back to the device's when unknown. A saved
         * place can be hours away, and "Sunset 10:48 PM" for somewhere the sun
         * sets at 7:48 is wrong rather than merely surprising.
         */
        val zone: ZoneId = ZoneId.systemDefault(),
    ) : HomeUiState

    /** Network or NWS failure, with a short plain-language message. */
    data class Error(val message: String) : HomeUiState
}
