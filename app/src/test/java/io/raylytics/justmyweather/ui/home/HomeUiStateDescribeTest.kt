package io.raylytics.justmyweather.ui.home

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
    }
}
