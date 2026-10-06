package io.raylytics.justmyweather.widget

import io.raylytics.justmyweather.data.WeatherLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class WidgetDataTest {
    private val carryWindow: Duration = Duration.ofMinutes(30)

    private fun data(latitude: Double, longitude: Double) =
        WidgetData(WeatherLocation(latitude, longitude, "Here"), snapshot = null, fetchedAt = Instant.EPOCH)

    @Test
    fun `previous data is about the same place within a grid cell's drift`() {
        val fetched = data(38.25, -85.76)
        assertTrue(fetched.isAbout(WeatherLocation(38.25, -85.76, "Here")))
        assertTrue(fetched.isAbout(WeatherLocation(38.29, -85.72, "A drifted fix")))
    }

    @Test
    fun `a list is fresh within the window, on its own clock`() {
        val fetched = Instant.parse("2026-10-05T14:00:00Z")
        val list = Fetched(listOf("x"), fetched)
        val window = Duration.ofMinutes(30)
        assertTrue(list.isFresh(fetched.plusSeconds(600), window))
        assertFalse(list.isFresh(fetched.plusSeconds(3600), window), "an hour old is past a 30 min window")
        assertFalse(list.isFresh(fetched.minusSeconds(60), window), "a clock that went backwards")
    }

    @Test
    fun `a needed list that fetched is stamped now, and one that failed keeps the previous list and its time`() {
        val then = Instant.parse("2026-10-05T14:00:00Z")
        val now = then.plusSeconds(900)
        val previous = Fetched(listOf("old"), then)
        assertEquals(Fetched(listOf("new"), now), settle(true, listOf("new"), previous, now, carryWindow))
        assertEquals(previous, settle(true, null, previous, now, carryWindow))
        assertNull(settle<String>(true, null, null, now, carryWindow))
    }

    @Test
    fun `an unneeded list is carried while fresh and dropped once stale`() {
        val then = Instant.parse("2026-10-05T14:00:00Z")
        val previous = Fetched(listOf("old"), then)
        assertEquals(previous, settle(false, null, previous, then.plusSeconds(900), carryWindow))
        assertNull(settle(false, null, previous, then.plus(carryWindow).plusSeconds(1), carryWindow))
    }

    @Test
    fun `refreshing the hours every tick does not keep unneeded periods alive`() {
        // The 5378 scenario: an hourly widget stays, the daily one went away.
        val start = Instant.parse("2026-10-05T14:00:00Z")
        var hours = Fetched(listOf("h0"), start)
        var periods: Fetched<String>? = Fetched(listOf("p0"), start)
        for (tick in 1..4) {
            val now = start.plus(carryWindow.dividedBy(2).multipliedBy(tick.toLong()))
            hours = settle(true, listOf("h$tick"), hours, now, carryWindow)!!
            periods = settle(false, null, periods, now, carryWindow)
        }
        assertEquals("h4", hours.items.single())
        assertNull(periods, "periods nobody needed were carried past the window on the hours' clock")
    }

    @Test
    fun `previous data is not about another place`() {
        val fetched = data(38.25, -85.76)
        assertFalse(fetched.isAbout(WeatherLocation(47.61, -122.33, "Seattle")))
        assertFalse(fetched.isAbout(WeatherLocation(38.25, -85.85, "Across town, past the gate")))
    }
}
