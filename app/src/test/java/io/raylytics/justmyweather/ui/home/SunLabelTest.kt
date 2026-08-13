package io.raylytics.justmyweather.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
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
}
