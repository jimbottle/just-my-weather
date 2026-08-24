package io.raylytics.justmyweather.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.data.SunDay
import io.raylytics.justmyweather.data.WeatherSnapshot
import io.raylytics.justmyweather.data.nws.ActiveAlert
import io.raylytics.justmyweather.data.nws.ForecastPoint
import io.raylytics.justmyweather.view.ForecastMode
import io.raylytics.justmyweather.view.ViewConfig
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The controls stay on screen however tall the glance gets.
 *
 * Written for a real report: on a day with a safety banner, the sun table and
 * an hourly strip all showing, Refresh / Customize / Alerts were pushed below
 * the fold. The column scrolls, but carries no scrollbar and no clipped edge,
 * so there was nothing to suggest they were still down there — they read as
 * missing.
 *
 * A short viewport stands in for that tall day. Any assertion that merely
 * finds the nodes would pass either way, since they are composed in both
 * layouts; assertIsDisplayed is what distinguishes "present" from "on screen".
 */
class HomeScreenControlsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 8, 15)

    private fun at(hour: Int, minute: Int): Instant =
        today.atTime(hour, minute).atZone(zone).toInstant()

    /** A day with everything on: a standing alert, sun times, an hourly strip. */
    private val crowded =
        HomeUiState.Ready(
            snapshot =
                WeatherSnapshot(
                    locationLabel = "Louisville, KY",
                    temperatureF = 93.0,
                    conditions = "Mostly Clear",
                    windMph = 7.0,
                    precipitationIn = null,
                    pressureInHg = 30.0,
                    observedAt = at(18, 10),
                ),
            config = ViewConfig.DEFAULT,
            forecastMode = ForecastMode.HOURLY,
            hourly =
                (0..11).map {
                    ForecastPoint(
                        startTime = at(18, 0).plusSeconds(it * 3600L),
                        temperatureF = 90.0 - it,
                        windMph = 5.0,
                    )
                },
            safetyAlerts =
                listOf(
                    ActiveAlert(
                        id = "urn:test",
                        event = "Heat Advisory",
                        headline = "Heat Advisory issued August 15 at 2:43PM EDT until August 16 at 9:00PM EDT",
                        severity = "Severe",
                    ),
                ),
            sunDays =
                listOf(
                    SunDay(today, at(6, 57), at(20, 36)),
                    SunDay(today.plusDays(1), at(6, 58), at(20, 35)),
                ),
        )

    private fun show(height: Dp) {
        compose.setContent {
            Box(Modifier.fillMaxWidth().height(height)) {
                HomeScreen(
                    state = crowded,
                    onRefresh = {},
                    onSetMode = {},
                    onCustomize = {},
                    onAlerts = {},
                    onCycleSpan = {},
                    onMoveModule = { _, _ -> },
                )
            }
        }
    }

    @Test
    fun theControlsStayOnScreenWhenTheGlanceOverflows() {
        // Deliberately shorter than the content needs: this is the reported
        // situation, not a hypothetical one.
        show(400.dp)
        compose.onNodeWithText("Refresh").assertIsDisplayed()
        compose.onNodeWithText("Customize").assertIsDisplayed()
        compose.onNodeWithText("Alerts").assertIsDisplayed()
    }

    @Test
    fun theyAreStillThereWhenEverythingFits() {
        // The other half of the contract: pinning must not cost them their
        // place on a roomy screen.
        show(900.dp)
        compose.onNodeWithText("Refresh").assertIsDisplayed()
        compose.onNodeWithText("Customize").assertIsDisplayed()
        compose.onNodeWithText("Alerts").assertIsDisplayed()
    }
}
