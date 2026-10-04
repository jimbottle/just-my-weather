package io.raylytics.justmyweather.ui.home

import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.view.ViewConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The bug report's "Glance:" line. Spelled out, never the class name — R8
 * renamed that to "n0" in the first release build. */
class HomeUiStateDescribeTest {
    @Test
    fun `each state has a fixed human name`() {
        assertEquals("Loading", HomeUiState.Loading.describe())
        val error = HomeUiState.Error("Couldn't reach the weather service.")
        assertEquals("Error: Couldn't reach the weather service.", error.describe())
        val snapshot = WeatherSnapshot("Louisville, KY", 68.0, "Light Rain", 9.0, null, 30.08, observedAt = null)
        val ready = HomeUiState.Ready(snapshot = snapshot, config = ViewConfig.DEFAULT)
        assertEquals("Ready", ready.describe())
        assertEquals("Ready (refreshing)", ready.copy(refreshing = true).describe())
    }
}
