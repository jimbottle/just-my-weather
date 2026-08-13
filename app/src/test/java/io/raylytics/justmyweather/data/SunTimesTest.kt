package io.raylytics.justmyweather.data

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Sun times against the US Naval Observatory.
 *
 * The expectations below were taken from USNO's own service
 * (aa.usno.navy.mil/api/rstt/oneday, tz=0) on 2026-08-13, and cross-checked
 * against open-meteo. They are NOT self-generated from this implementation,
 * which would only prove it agrees with itself.
 *
 * Worth recording why the source matters: an early draft was validated against
 * sunrise-sunset.org, which disagreed by five minutes at 64°N and led to a
 * rewrite chasing an error that was the reference's, not ours. USNO and
 * open-meteo agree with each other and with this code.
 *
 * The tolerance below absorbs USNO's minute-rounding plus the difference
 * between their fuller model and this one. The worst deviation measured across
 * these cases was about 90 seconds (New York); two minutes leaves margin
 * without letting a real regression through — five minutes, the size of the
 * error a coarse solar model produces at high latitude, would still fail.
 */
class SunTimesTest {
    private val tolerance: Duration = Duration.ofMinutes(2)

    private fun assertNear(expected: String, actual: Instant?, what: String) {
        assertNotNull(actual, "$what: expected about $expected, got null")
        val off = abs(Duration.between(Instant.parse(expected), actual).seconds)
        assertTrue(off <= tolerance.seconds, "$what: expected about $expected, got $actual (${off}s off)")
    }

    @Test
    fun `mid-latitude northern, matching USNO`() {
        // Louisville, the developer's own sky. USNO: rise 10:56, set 00:40 (+1d).
        val e = SunTimes.next(38.2527, -85.7585, Instant.parse("2026-08-13T09:00:00Z"))
        assertNear("2026-08-13T10:56:00Z", e.sunrise, "Louisville sunrise")
        assertNear("2026-08-14T00:40:00Z", e.sunset, "Louisville sunset")
    }

    @Test
    fun `the app's own default location`() {
        // WeatherLocation.DEFAULT is New York, so this is what a fresh install
        // with no location permission shows. USNO: rise 10:03, set 23:58.
        val e = SunTimes.next(40.7128, -74.0060, Instant.parse("2026-08-13T09:00:00Z"))
        assertNear("2026-08-13T10:03:00Z", e.sunrise, "New York sunrise")
        assertNear("2026-08-13T23:58:00Z", e.sunset, "New York sunset")
    }

    @Test
    fun `high latitude in deep winter, where a coarse model goes wrong`() {
        // Reykjavik, 64°N, winter solstice: a four-hour day, and the case that
        // separates a good solar model from a passable one. USNO: 11:22, 15:29.
        val e = SunTimes.next(64.1466, -21.9426, Instant.parse("2026-12-21T09:00:00Z"))
        assertNear("2026-12-21T11:22:00Z", e.sunrise, "Reykjavik sunrise")
        assertNear("2026-12-21T15:29:00Z", e.sunset, "Reykjavik sunset")
    }

    @Test
    fun `southern hemisphere, where the seasons invert`() {
        // Sydney. USNO: rise 20:36 (prev UTC day), set 07:24.
        val e = SunTimes.next(-33.8688, 151.2093, Instant.parse("2026-08-12T19:00:00Z"))
        assertNear("2026-08-12T20:36:00Z", e.sunrise, "Sydney sunrise")
        assertNear("2026-08-13T07:24:00Z", e.sunset, "Sydney sunset")
    }

    @Test
    fun `the equator at an equinox`() {
        // Quito. USNO: 11:18, 23:24.
        val e = SunTimes.next(-0.1807, -78.4678, Instant.parse("2026-03-20T09:00:00Z"))
        assertNear("2026-03-20T11:18:00Z", e.sunrise, "Quito sunrise")
        assertNear("2026-03-20T23:24:00Z", e.sunset, "Quito sunset")
    }

    @Test
    fun `polar night and midnight sun have no answer, and say so`() {
        // Longyearbyen at 78°N. The sun does not rise in December nor set in
        // June, and null is the honest answer — not a fabricated time, and not
        // a crash. This is why the return type is nullable at all.
        val december = SunTimes.next(78.2232, 15.6267, Instant.parse("2026-12-21T09:00:00Z"))
        assertNull(december.sunrise, "no sunrise during polar night")
        assertNull(december.sunset, "no sunset during polar night")

        val june = SunTimes.next(78.2232, 15.6267, Instant.parse("2026-06-21T09:00:00Z"))
        assertNull(june.sunrise, "no sunrise during midnight sun")
        assertNull(june.sunset, "no sunset during midnight sun")
    }

    @Test
    fun `next means next — the two events come from different days when they must`() {
        // Mid-afternoon in Louisville: tonight's sunset is hours away, but the
        // next sunrise is tomorrow's. A caller that took both from "today"
        // would show a sunrise that already happened.
        val afternoon = Instant.parse("2026-08-13T19:00:00Z") // 3pm local
        val e = SunTimes.next(38.2527, -85.7585, afternoon)
        assertTrue(e.sunset!!.isAfter(afternoon), "sunset must be in the future")
        assertTrue(e.sunrise!!.isAfter(e.sunset), "at 3pm the next sunrise follows the next sunset")
        assertNear("2026-08-14T10:57:00Z", e.sunrise, "tomorrow's sunrise")
    }

    @Test
    fun `both events are always strictly in the future`() {
        // Walk a day in twenty-minute steps at a mid-latitude and assert the
        // invariant the UI depends on. The boundaries either side of an event
        // are where an off-by-one day would show.
        var from = Instant.parse("2026-08-13T00:00:00Z")
        val end = from.plus(Duration.ofDays(1))
        while (from.isBefore(end)) {
            val e = SunTimes.next(38.2527, -85.7585, from)
            assertTrue(e.sunrise!!.isAfter(from), "sunrise not in the future at $from")
            assertTrue(e.sunset!!.isAfter(from), "sunset not in the future at $from")
            assertTrue(
                Duration.between(from, e.sunrise).toHours() < 25,
                "sunrise implausibly far off at $from: ${e.sunrise}",
            )
            from = from.plus(Duration.ofMinutes(20))
        }
    }
}
