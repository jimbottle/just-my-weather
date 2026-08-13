package io.raylytics.justmyweather.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The day qualifier on a sun time. These are the *next* sunrise and sunset, so
 * they routinely fall on different days, and a bare clock time is ambiguous
 * exactly when it matters.
 */
class SunLabelTest {
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val format: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    private fun label(event: String, now: String) =
        SunLabel.format(Instant.parse(event), Instant.parse(now), zone, format)

    @Test
    fun `an event later today is just a time`() {
        // 9am local, sunset at 8:31pm the same evening.
        assertEquals("8:31 PM", label(event = "2026-08-13T20:31:00-04:00", now = "2026-08-13T09:00:00-04:00"))
    }

    @Test
    fun `an event on the next local day says tomorrow`() {
        // 3pm local: tonight's sunset has not happened, but the next SUNRISE
        // is tomorrow's. Without the qualifier this reads as a time that has
        // already gone past.
        assertEquals("6:52 AM tomorrow", label(event = "2026-08-14T06:52:00-04:00", now = "2026-08-13T15:00:00-04:00"))
    }

    @Test
    fun `the boundary is the local calendar day, not a 24-hour span`() {
        // 11pm, event 8 hours away — that is tomorrow.
        assertEquals("6:52 AM tomorrow", label(event = "2026-08-14T06:52:00-04:00", now = "2026-08-13T23:00:00-04:00"))
        // 1am, event 8 hours away — that is today, and calling it "tomorrow"
        // would be wrong even though the gap is identical.
        assertEquals("6:52 AM", label(event = "2026-08-14T06:52:00-04:00", now = "2026-08-14T01:00:00-04:00"))
    }

    @Test
    fun `an event further out names its day`() {
        // Only reachable near the poles, where the next sunrise can be days
        // away. "6:52 AM" alone would be a lie by omission.
        assertEquals("6:52 AM Sat", label(event = "2026-08-15T06:52:00-04:00", now = "2026-08-13T09:00:00-04:00"))
    }

    @Test
    fun `the zone is the user's, not UTC`() {
        // Both instants fall on 2026-08-13 in UTC, so a comparison done in UTC
        // would say "today". Locally they straddle midnight — 11pm on the 12th
        // and 12am on the 13th — so the honest answer is tomorrow.
        assertEquals("12:00 AM tomorrow", label(event = "2026-08-13T04:00:00Z", now = "2026-08-13T03:00:00Z"))
    }

    @Test
    fun `the day name follows the formatter's locale, not the machine's`() {
        // The clock time and the day name must not disagree about language,
        // and a test asserting "Sat" must not depend on which locale the
        // machine or CI image happens to run in.
        val french = DateTimeFormatter.ofPattern("H:mm", Locale.FRANCE)
        val label = SunLabel.format(
            Instant.parse("2026-08-15T06:52:00-04:00"),
            Instant.parse("2026-08-13T09:00:00-04:00"),
            zone,
            french,
        )
        assertTrue(label.startsWith("6:52"), "French pattern gives 24-hour time: $label")
        assertFalse(label.endsWith("Sat"), "the day name should be French, not English: $label")
    }

    @Test
    fun `an event already past does not name a weekday`() {
        // Defensive: freshly computed events are always in the future, so this
        // only happens if a caller holds a stale value. Printing "Sat" for a
        // Saturday that has been and gone would read as the coming one — the
        // bare time at least makes no claim about the day.
        assertEquals("6:52 AM", label(event = "2026-08-11T06:52:00-04:00", now = "2026-08-13T09:00:00-04:00"))
    }
}
