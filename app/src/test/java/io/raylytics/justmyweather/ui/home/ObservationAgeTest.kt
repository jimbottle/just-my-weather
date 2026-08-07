package io.raylytics.justmyweather.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The words the glance puts on a reading's age, at every boundary. */
class ObservationAgeTest {
    private val observedAt: Instant = Instant.parse("2026-08-07T12:40:00Z")

    private fun after(duration: Duration) = ObservationAge.label(observedAt, observedAt.plus(duration))

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("just now", after(Duration.ZERO))
        assertEquals("just now", after(Duration.ofSeconds(59)))
    }

    @Test
    fun `minutes are counted whole, and floor rather than round`() {
        assertEquals("1 min ago", after(Duration.ofSeconds(60)))
        // 12m59s is still "12 min ago" — a label that rounded up would claim
        // an age the reading has not reached.
        assertEquals("12 min ago", after(Duration.ofMinutes(12).plusSeconds(59)))
        assertEquals("59 min ago", after(Duration.ofMinutes(59)))
    }

    @Test
    fun `an hour in, the unit gets coarser`() {
        assertEquals("1 hr ago", after(Duration.ofMinutes(60)))
        assertEquals("1 hr ago", after(Duration.ofMinutes(119)))
        assertEquals("3 hr ago", after(Duration.ofHours(3)))
        assertEquals("23 hr ago", after(Duration.ofHours(23)))
    }

    @Test
    fun `a day in, coarser still, and singular reads correctly`() {
        assertEquals("1 day ago", after(Duration.ofHours(24)))
        assertEquals("1 day ago", after(Duration.ofHours(47)))
        assertEquals("2 days ago", after(Duration.ofDays(2)))
    }

    @Test
    fun `a small clock disagreement is absorbed, a large one is not claimed`() {
        // The station's timestamp is UTC and the clock is the user's; a gap
        // that runs backwards means the two disagree.
        assertEquals("just now", after(Duration.ofSeconds(-30)))
        assertEquals("just now", after(ObservationAge.FUTURE_TOLERANCE.negated()))
        // Past ordinary skew, any age we printed would be wrong by an unknown
        // amount, so the screen shows the timestamp alone.
        assertNull(after(ObservationAge.FUTURE_TOLERANCE.negated().minusSeconds(1)))
        assertNull(after(Duration.ofHours(-5)))
    }
}
