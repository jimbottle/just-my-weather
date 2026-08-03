package io.raylytics.justmyweather.alerts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertSettingsTest {
    @Test
    fun `disabled quiet hours are never quiet`() {
        val off = AlertSettings(quietHoursEnabled = false, quietStartHour = 22, quietEndHour = 7)
        assertFalse(off.isQuietAt(2))
        assertFalse(off.isQuietAt(23))
    }

    @Test
    fun `a window wrapping midnight is quiet across the boundary and open in the day`() {
        val night = AlertSettings(quietHoursEnabled = true, quietStartHour = 22, quietEndHour = 7)
        assertTrue(night.isQuietAt(22)) // start is inclusive
        assertTrue(night.isQuietAt(23))
        assertTrue(night.isQuietAt(0))
        assertTrue(night.isQuietAt(6))
        assertFalse(night.isQuietAt(7)) // end is exclusive
        assertFalse(night.isQuietAt(12))
        assertFalse(night.isQuietAt(21))
    }

    @Test
    fun `a same-day window does not wrap`() {
        val midday = AlertSettings(quietHoursEnabled = true, quietStartHour = 9, quietEndHour = 17)
        assertFalse(midday.isQuietAt(8))
        assertTrue(midday.isQuietAt(9))
        assertTrue(midday.isQuietAt(16))
        assertFalse(midday.isQuietAt(17))
        assertFalse(midday.isQuietAt(23))
    }

    @Test
    fun `codec round-trips and degrades to default on bad data`() {
        val settings = AlertSettings(quietHoursEnabled = true, quietStartHour = 21, quietEndHour = 6, pollMinutes = 180)
        assertEquals(settings, AlertSettingsCodec.decode(AlertSettingsCodec.encode(settings)))
        assertEquals(AlertSettings.DEFAULT, AlertSettingsCodec.decode(null))
        assertEquals(AlertSettings.DEFAULT, AlertSettingsCodec.decode("{ not json"))
        // A partial blob keeps the defaulted fields.
        assertEquals(AlertSettings.DEFAULT, AlertSettingsCodec.decode("{}"))
    }

    @Test
    fun `an out-of-range cadence falls back to the default`() {
        // A cadence not in POLL_CHOICES (e.g. an old 5-minute value) is clamped.
        val decoded = AlertSettingsCodec.decode("""{"pollMinutes":5}""")
        assertEquals(AlertSettings.DEFAULT.pollMinutes, decoded.pollMinutes)
        // A valid choice is kept.
        assertEquals(360, AlertSettingsCodec.decode("""{"pollMinutes":360}""").pollMinutes)
    }

    @Test
    fun `poll label reads as minutes under an hour and hours at or above`() {
        assertEquals("30m", AlertSettings.pollLabel(30))
        assertEquals("1h", AlertSettings.pollLabel(60))
        assertEquals("3h", AlertSettings.pollLabel(180))
    }

    @Test
    fun `safety notifications default off and survive a round trip`() {
        // Off by default is a product promise, not a preference: these are
        // official hazard pushes the user has to ask for.
        assertFalse(AlertSettings.DEFAULT.safetyNotifications)
        val on = AlertSettings.DEFAULT.copy(safetyNotifications = true)
        assertTrue(AlertSettingsCodec.decode(AlertSettingsCodec.encode(on)).safetyNotifications)
    }

    @Test
    fun `a settings blob written before safety alerts existed still decodes`() {
        // Older installs have no such key; it must default rather than throw
        // or reset every other setting.
        val legacy = """{"quietHoursEnabled":true,"quietStartHour":23,"quietEndHour":6,"pollMinutes":180}"""
        val decoded = AlertSettingsCodec.decode(legacy)
        assertFalse(decoded.safetyNotifications)
        assertTrue(decoded.quietHoursEnabled)
        assertEquals(23, decoded.quietStartHour)
        assertEquals(180, decoded.pollMinutes)
    }
}
