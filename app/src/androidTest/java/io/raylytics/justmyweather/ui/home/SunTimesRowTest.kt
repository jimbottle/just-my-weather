package io.raylytics.justmyweather.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.raylytics.justmyweather.data.SunEvents
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
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
 *
 * Every instant below is BUILT FROM the device's own zone rather than
 * hardcoded in UTC. The row formats in [ZoneId.systemDefault], so fixed UTC
 * instants would straddle local midnight at exactly one offset: the test would
 * fail against correct code in US Central and westward, and skip itself
 * entirely on a default GMT emulator image — a result decided by the device's
 * settings rather than by the code. Derived this way the crossing is real in
 * every zone, and no assumption or skip is needed.
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

    private val zone: ZoneId = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.of(2026, 8, 13)

    private fun localInstant(date: LocalDate, hour: Int, minute: Int): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    // Both events fall on the morning and evening AFTER the crossing, so they
    // are in the future from either side of it — and, crucially, they never
    // change. Only the clock moves.
    private val events =
        SunEvents(
            sunrise = localInstant(day.plusDays(1), 6, 5),
            sunset = localInstant(day.plusDays(1), 20, 30),
        )

    @Test
    fun theDayQualifierFollowsTheClockEvenWhenTheEventsDoNot() {
        compose.mainClock.autoAdvance = false
        var fakeNow = localInstant(day, 23, 59)
        compose.setContent { SunTimesRow(events) { fakeNow } }
        compose.mainClock.advanceTimeByFrame()

        // A minute before midnight both events belong to tomorrow — asserting
        // the count, not "at least one", so the row is pinned rather than
        // merely probed.
        compose.onAllNodesWithText("tomorrow", substring = true).assertCountEquals(2)

        // Six minutes later it is the small hours of the next day. The events
        // are untouched — same object, same instants — so nothing upstream
        // emits and nothing recomposes unless the row's own clock moved.
        fakeNow = localInstant(day.plusDays(1), 0, 5)
        compose.mainClock.advanceTimeBy(SUN_TICK.inWholeMilliseconds + 1)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        compose.onAllNodesWithText("tomorrow", substring = true).assertCountEquals(0)
    }

    @Test
    fun aMissingEventRendersAsADashRatherThanVanishing() {
        // Polar night is a real state, not an error. Both halves matter: the
        // labels stay so the absence reads as "the sun is not doing that", and
        // the value is a visible dash rather than an empty gap that looks like
        // a value failed to load.
        compose.mainClock.autoAdvance = false
        val now = localInstant(day, 12, 0)
        compose.setContent { SunTimesRow(SunEvents(sunrise = null, sunset = null)) { now } }
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithText("Sunrise").assertIsDisplayed()
        compose.onNodeWithText("Sunset").assertIsDisplayed()
        compose.onAllNodesWithText("—").assertCountEquals(2)
    }
}
