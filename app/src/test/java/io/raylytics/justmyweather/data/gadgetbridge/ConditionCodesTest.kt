package io.raylytics.justmyweather.data.gadgetbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The mapping is the least certain part of the Gadgetbridge export — NWS
 * composes its descriptions freely and a wrong code puts a confidently wrong
 * icon on the watch. These tests pin the ordering decisions specifically,
 * since order is what makes compound descriptions resolve correctly.
 */
class ConditionCodesTest {
    @Test
    fun `plain cloud cover maps to the matching OWM density`() {
        assertEquals(800, openWeatherMapCodeFor("Clear"))
        assertEquals(800, openWeatherMapCodeFor("Sunny"))
        assertEquals(801, openWeatherMapCodeFor("Mostly Sunny"))
        assertEquals(802, openWeatherMapCodeFor("Partly Cloudy"))
        assertEquals(803, openWeatherMapCodeFor("Mostly Cloudy"))
        assertEquals(804, openWeatherMapCodeFor("Overcast"))
    }

    @Test
    fun `mostly cloudy is not swallowed by cloudy`() {
        // Both contain "cloudy". If the bare entry were tested first, every
        // partial cover would collapse to 804 and the watch would show
        // overcast on a partly sunny day.
        assertEquals(803, openWeatherMapCodeFor("Mostly Cloudy"))
        assertEquals(802, openWeatherMapCodeFor("Partly Cloudy"))
        assertEquals(804, openWeatherMapCodeFor("Cloudy"))
    }

    @Test
    fun `thunderstorms outrank the rain they mention`() {
        assertEquals(211, openWeatherMapCodeFor("Thunderstorm"))
        assertEquals(211, openWeatherMapCodeFor("Light Rain and Thunderstorm"))
        assertEquals(211, openWeatherMapCodeFor("Thunderstorm in Vicinity"))
    }

    @Test
    fun `freezing and frozen precipitation beat plain rain`() {
        assertEquals(511, openWeatherMapCodeFor("Freezing Rain"))
        assertEquals(611, openWeatherMapCodeFor("Ice Pellets"))
        assertEquals(600, openWeatherMapCodeFor("Light Snow"))
        assertEquals(602, openWeatherMapCodeFor("Heavy Snow"))
        assertEquals(601, openWeatherMapCodeFor("Snow"))
    }

    @Test
    fun `rain intensities are distinguished`() {
        assertEquals(500, openWeatherMapCodeFor("Light Rain"))
        assertEquals(501, openWeatherMapCodeFor("Rain"))
        assertEquals(502, openWeatherMapCodeFor("Heavy Rain"))
        assertEquals(521, openWeatherMapCodeFor("Rain Showers"))
    }

    @Test
    fun `obscurations map to the atmosphere group`() {
        assertEquals(741, openWeatherMapCodeFor("Fog"))
        // NWS emits this as one token; fog is the more actionable of the two.
        assertEquals(741, openWeatherMapCodeFor("Fog/Mist"))
        assertEquals(701, openWeatherMapCodeFor("Mist"))
        assertEquals(721, openWeatherMapCodeFor("Haze"))
        assertEquals(711, openWeatherMapCodeFor("Smoke"))
    }

    @Test
    fun `matching ignores case and surrounding whitespace`() {
        assertEquals(803, openWeatherMapCodeFor("  mostly cloudy  "))
        assertEquals(800, openWeatherMapCodeFor("CLEAR"))
    }

    @Test
    fun `unmapped and empty text yield null rather than a guess`() {
        // Null is the honest answer: the payload omits the field instead of
        // asserting a condition nobody mapped.
        assertNull(openWeatherMapCodeFor("Blowing Widgets"))
        assertNull(openWeatherMapCodeFor("Unknown"))
        assertNull(openWeatherMapCodeFor(""))
        assertNull(openWeatherMapCodeFor("   "))
        assertNull(openWeatherMapCodeFor(null))
    }
}
