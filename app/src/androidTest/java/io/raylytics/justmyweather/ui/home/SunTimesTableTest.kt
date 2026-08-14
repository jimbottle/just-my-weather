package io.raylytics.justmyweather.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.raylytics.justmyweather.data.SunDay
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the sun table draws. The behaviour that used to live here — the day
 * qualifier flipping across local midnight — moved to HomeViewModelTest when
 * the rows gained their own dates: which day is "today" is now decided in the
 * ViewModel and testable on the JVM, so only the rendering needs a device.
 *
 * Instants are built from the device's own zone. The table formats in
 * [ZoneId.systemDefault], so fixed UTC instants would land on a different
 * local date on every differently-offset machine and the row a time appears
 * in would depend on the emulator's settings rather than on the code.
 */
class SunTimesTableTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 8, 14)

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant =
        date.atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun eachDayShowsItsOwnDateAndBothTimes() {
        val days =
            listOf(
                SunDay(today, at(today, 6, 52), at(today, 20, 28)),
                SunDay(today.plusDays(1), at(today.plusDays(1), 6, 53), at(today.plusDays(1), 20, 27)),
            )
        compose.setContent { SunTimesTable(days) }

        // Headings once, not per row.
        compose.onNodeWithText("Sunrise").assertIsDisplayed()
        compose.onNodeWithText("Sunset").assertIsDisplayed()

        // Each row carries its own date, which is what replaced the "tomorrow"
        // suffix — the whole point of the day-row shape.
        compose.onNodeWithText("Aug 14").assertIsDisplayed()
        compose.onNodeWithText("Aug 15").assertIsDisplayed()

        // And its own times, in the right rows.
        compose.onNodeWithText("6:52 AM").assertIsDisplayed()
        compose.onNodeWithText("8:28 PM").assertIsDisplayed()
        compose.onNodeWithText("6:53 AM").assertIsDisplayed()
        compose.onNodeWithText("8:27 PM").assertIsDisplayed()

        // No qualifier anywhere: the date column is doing that job now.
        compose.onAllNodesWithText("tomorrow", substring = true).assertCountEquals(0)
    }

    @Test
    fun aMissingEventRendersAsADashRatherThanVanishing() {
        // Polar night is a real state, not an error. Both halves matter: the
        // row stays so the absence reads as "the sun is not doing that", and
        // the value is a visible dash rather than an empty gap that looks like
        // something failed to load.
        compose.setContent { SunTimesTable(listOf(SunDay(today, sunrise = null, sunset = null))) }

        compose.onNodeWithText("Aug 14").assertIsDisplayed()
        compose.onAllNodesWithText("—").assertCountEquals(2)
    }
}
