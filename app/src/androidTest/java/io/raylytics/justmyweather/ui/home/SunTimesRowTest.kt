package io.raylytics.justmyweather.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.raylytics.justmyweather.data.SunEvents
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The day qualifier across a local midnight — the one staleness a ticking
 * ViewModel cannot fix.
 *
 * Recomputing the events does nothing here: at 23:59 and again at 00:05 the
 * next sunrise is the SAME instant, so [SunEvents] is structurally equal, the
 * state carrying it is conflated by its StateFlow, and this row is never
 * recomposed. Only "now" has moved, so only a clock held as state inside the
 * row can catch it. Without that, the row goes on saying "tomorrow" about a
 * sunrise happening in six hours.
 */
class SunTimesRowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Hand the clock back: these tests pause it and leave an endless ticker
     * parked, and a wedged rule is inherited by whatever runs next. */
    @After
    fun resumeClock() {
        compose.mainClock.autoAdvance = true
    }

    // Fixed events either side of a local midnight in New York: sunset this
    // evening, sunrise the following morning. Deliberately never changed.
    private val sunset: Instant = Instant.parse("2026-08-13T23:56:00Z") // 7:56 PM EDT the 13th
    private val sunrise: Instant = Instant.parse("2026-08-14T10:05:00Z") // 6:05 AM EDT the 14th
    private val events = SunEvents(sunrise = sunrise, sunset = sunset)

    @Test
    fun theDayQualifierFollowsTheClockEvenWhenTheEventsDoNot() {
        // The row formats in the device's zone; this case is only meaningful
        // where the two instants straddle local midnight.
        assumeTrue(
            "needs a zone where these instants straddle midnight",
            ZoneId.systemDefault().rules.getOffset(sunrise).totalSeconds <= -4 * 3600,
        )
        compose.mainClock.autoAdvance = false
        var fakeNow = Instant.parse("2026-08-14T03:59:00Z") // 11:59 PM EDT on the 13th
        compose.setContent { SunTimesRow(events) { fakeNow } }
        compose.mainClock.advanceTimeByFrame()

        // Before midnight, that sunrise genuinely belongs to tomorrow.
        compose.onNodeWithText("tomorrow", substring = true).assertIsDisplayed()

        // Six minutes later it is the small hours of the 14th. The events are
        // untouched — same objects, same instants — so nothing upstream emits.
        fakeNow = Instant.parse("2026-08-14T04:05:00Z") // 12:05 AM EDT on the 14th
        compose.mainClock.advanceTimeBy(SUN_TICK.inWholeMilliseconds + 1)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("tomorrow", substring = true).assertDoesNotExist()
    }

    @Test
    fun aMissingEventRendersAsADashRatherThanVanishing() {
        // Polar night is a real state, not an error. The row must stay put so
        // the absence reads as "the sun is not doing that", rather than the
        // element quietly disappearing for a season.
        compose.mainClock.autoAdvance = false
        val now = Instant.parse("2026-12-21T12:00:00Z")
        compose.setContent { SunTimesRow(SunEvents(sunrise = null, sunset = null)) { now } }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Sunrise").assertIsDisplayed()
        compose.onNodeWithText("Sunset").assertIsDisplayed()
    }
}
