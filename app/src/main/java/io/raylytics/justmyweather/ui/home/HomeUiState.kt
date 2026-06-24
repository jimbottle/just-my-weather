package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.WeatherSnapshot

/**
 * The complete state of the home screen as one value. A sealed hierarchy keeps
 * the screen honest: every state it can render is enumerated here, so a glance
 * at this file tells you exactly what the user can ever see.
 */
sealed interface HomeUiState {
    /** First paint, before any data. Stays visually quiet — no spinner storm. */
    data object Loading : HomeUiState

    /** The normal case: a snapshot to glance at. */
    data class Ready(
        val snapshot: WeatherSnapshot,
        val refreshing: Boolean = false,
    ) : HomeUiState

    /** Network or NWS failure, with a short plain-language message. */
    data class Error(val message: String) : HomeUiState
}
