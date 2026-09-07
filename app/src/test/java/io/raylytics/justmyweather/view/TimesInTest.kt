package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.WeatherSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId

class TimesInTest {
    private val newYork = ZoneId.of("America/New_York")
    private val seattle = ZoneId.of("America/Los_Angeles")

    private fun reading(zone: String?) =
        WeatherSnapshot("x", null, null, null, null, null, observedAt = null, timeZone = zone)

    @Test
    fun `a reading's timestamp reads in its own zone in place mode, never the screen's current place`() {
        // Mid place-switch: the screen's place is already Seattle, the reading
        // on screen is still New York's. Its time must stay New York's.
        assertEquals(newYork, TimesIn.PLACE.observedZone(reading("America/New_York"), place = seattle))
        // A reading with no zone of its own falls back to the place.
        assertEquals(seattle, TimesIn.PLACE.observedZone(reading(null), place = seattle))
        // And a garbage zone id degrades to the fallback rather than failing.
        assertEquals(seattle, TimesIn.PLACE.observedZone(reading("Mars/Olympus"), place = seattle))
    }

    @Test
    fun `in device mode the reading reads in the phone's clock like everything else`() {
        assertEquals(
            newYork,
            TimesIn.DEVICE.observedZone(reading("America/Los_Angeles"), place = seattle, device = newYork),
        )
    }
}
