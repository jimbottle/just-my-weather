package io.raylytics.justmyweather.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import io.raylytics.justmyweather.data.WeatherSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/**
 * WHEN the observed line reads the clock, which is the half of this feature
 * the JVM suite cannot see. [ObservationAgeTest] proves the words are right
 * for a given pair of instants; nothing there can catch the line handing it
 * the wrong instant, and both defects this line has had were exactly that.
 *
 * The clock is driven, not waited on: a test that slept would take minutes and
 * still only cover the case where the screen is sat and watched, which is the
 * case that already worked.
 */
class ObservedLineTest {
    // An activity-backed rule, not createComposeRule(): the ticker is gated on
    // the host's lifecycle, and without a handle on that there is no way to
    // leave RESUMED — which is precisely the state the worst defect lives in.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Hand the clock back before teardown. Both tests below pause it and leave
     * an endless ticker parked on a delay that will never come due; disposing
     * that composition with the clock still frozen is a way to wedge the rule,
     * and a wedged rule is inherited by whatever test runs next in the process.
     */
    @After
    fun resumeClock() {
        compose.mainClock.autoAdvance = true
    }

    private val t0: Instant = Instant.parse("2026-08-07T12:00:00Z")

    private fun snapshotAt(observedAt: Instant?) =
        WeatherSnapshot(
            locationLabel = "Louisville, KY",
            temperatureF = 78.0,
            conditions = "Mostly Cloudy",
            windMph = null,
            precipitationIn = null,
            pressureInHg = null,
            observedAt = observedAt,
        )

    @Test
    fun aTimestampArrivingLaterIsAgedAgainstTheClockNow() {
        // Frames are stepped by hand: the ticker loops forever, so letting the
        // rule auto-advance would spin rather than settle.
        compose.mainClock.autoAdvance = false
        var fakeNow = t0
        var snapshot by mutableStateOf(snapshotAt(observedAt = null))
        compose.setContent { ObservedLine(snapshot) { fakeNow } }
        compose.mainClock.advanceTimeByFrame()

        // Ten minutes pass with no station timestamp to age — so the ticker
        // never ran and the clock sample from first composition is all we have.
        fakeNow = t0.plusSeconds(600)
        // Then a refresh lands a reading the station took a minute ago.
        snapshot = snapshotAt(observedAt = t0.plusSeconds(540))
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        // Aged against the clock now (1 min), not against the ten-minute-old
        // sample — which would put the reading nine minutes in the "future",
        // past FUTURE_TOLERANCE, and drop the age off the line entirely at the
        // exact moment it was there to confirm the refresh.
        compose.onNodeWithText("1 min ago", substring = true).assertExists()
    }

    @Test
    fun theAgeAdvancesWithoutAnyInteraction() {
        compose.mainClock.autoAdvance = false
        var fakeNow = t0
        compose.setContent { ObservedLine(snapshotAt(observedAt = t0.minusSeconds(60))) { fakeNow } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("1 min ago", substring = true).assertExists()

        // One tick later the label has moved on its own.
        fakeNow = t0.plusSeconds(60)
        compose.mainClock.advanceTimeBy(AGE_TICK.inWholeMilliseconds + 1)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("2 min ago", substring = true).assertExists()
    }

    @Test
    fun theTickerStopsWhileStoppedAndReSamplesOnResume() {
        // The overnight case, and the only one the other two can't see: they
        // run continuously RESUMED, so they pass whether or not the loop is
        // gated on the lifecycle at all.
        //
        // Asserted by COUNTING clock reads rather than by reading the label,
        // because a stopped activity has no window and the semantics tree goes
        // with it — there is nothing to query in the very state under test.
        // The count says the same thing more directly anyway: a read is a tick,
        // so "no reads while stopped" IS "the ticker stopped".
        compose.mainClock.autoAdvance = false
        var fakeNow = t0
        var reads = 0
        val clock = {
            reads++
            fakeNow
        }
        compose.setContent { ObservedLine(snapshotAt(observedAt = t0.minusSeconds(60)), clock) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("1 min ago", substring = true).assertExists()

        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.mainClock.advanceTimeByFrame()
        val readsWhenStopped = reads

        // An hour passes with the screen off. Stepping several ticks stands in
        // for what a device does NOT do while asleep: an ungated ticker has a
        // delay pending, so it would wake, sample, and keep sampling for an app
        // that is not even on screen.
        fakeNow = t0.plusSeconds(3600)
        repeat(4) { compose.mainClock.advanceTimeBy(AGE_TICK.inWholeMilliseconds + 1) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals("ticker kept reading the clock while stopped", readsWhenStopped, reads)

        // Coming back re-samples straight away — not after waiting out another
        // full tick, which is what left a stale age on screen to be read.
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("1 hr ago", substring = true).assertIsDisplayed()
    }
}
