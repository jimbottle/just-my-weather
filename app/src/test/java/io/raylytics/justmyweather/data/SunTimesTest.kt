package io.raylytics.justmyweather.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
        // Louisville, the developer's own sky. USNO: rise 10:56Z, set 00:40Z —
        // both of which are 13 August where he lives.
        val day =
            SunTimes.forDate(38.2527, -85.7585, LocalDate.of(2026, 8, 13), ZoneId.of("America/Kentucky/Louisville"))
        assertNear("2026-08-13T10:56:00Z", day.sunrise, "Louisville sunrise")
        assertNear("2026-08-14T00:40:00Z", day.sunset, "Louisville sunset")
    }

    @Test
    fun `the app's own default location`() {
        // WeatherLocation.DEFAULT is New York. USNO: rise 10:03Z, set 23:58Z.
        val day = SunTimes.forDate(40.7128, -74.0060, LocalDate.of(2026, 8, 13), ZoneId.of("America/New_York"))
        assertNear("2026-08-13T10:03:00Z", day.sunrise, "New York sunrise")
        assertNear("2026-08-13T23:58:00Z", day.sunset, "New York sunset")
    }

    @Test
    fun `high latitude in deep winter, where a coarse model goes wrong`() {
        // Reykjavik, 64°N, winter solstice: a four-hour day, and the case that
        // separates a good solar model from a passable one. USNO: 11:22, 15:29.
        val day = SunTimes.forDate(64.1466, -21.9426, LocalDate.of(2026, 12, 21), ZoneId.of("Atlantic/Reykjavik"))
        assertNear("2026-12-21T11:22:00Z", day.sunrise, "Reykjavik sunrise")
        assertNear("2026-12-21T15:29:00Z", day.sunset, "Reykjavik sunset")
    }

    @Test
    fun `southern hemisphere, where the local date runs ahead of UTC`() {
        // Sydney: the sunrise USNO reports at 20:36Z on the 12th is the MORNING
        // of the 13th there. Asking by local date has to find it across that
        // UTC boundary — the thing "the next of each" never had to do.
        val day = SunTimes.forDate(-33.8688, 151.2093, LocalDate.of(2026, 8, 13), ZoneId.of("Australia/Sydney"))
        assertNear("2026-08-12T20:36:00Z", day.sunrise, "Sydney sunrise")
        assertNear("2026-08-13T07:24:00Z", day.sunset, "Sydney sunset")
    }

    @Test
    fun `the equator at an equinox`() {
        // Quito. USNO: 11:18, 23:24.
        val day = SunTimes.forDate(-0.1807, -78.4678, LocalDate.of(2026, 3, 20), ZoneId.of("America/Guayaquil"))
        assertNear("2026-03-20T11:18:00Z", day.sunrise, "Quito sunrise")
        assertNear("2026-03-20T23:24:00Z", day.sunset, "Quito sunset")
    }

    @Test
    fun `polar night and midnight sun have no answer, and say so`() {
        // Longyearbyen at 78°N. The sun does not rise in December nor set in
        // June, and null is the honest answer — not a fabricated time, and not
        // a crash. This is why the times are nullable at all.
        val zone = ZoneId.of("Arctic/Longyearbyen")
        val december = SunTimes.forDate(78.2232, 15.6267, LocalDate.of(2026, 12, 21), zone)
        assertNull(december.sunrise, "no sunrise during polar night")
        assertNull(december.sunset, "no sunset during polar night")

        val june = SunTimes.forDate(78.2232, 15.6267, LocalDate.of(2026, 6, 21), zone)
        assertNull(june.sunrise, "no sunrise during midnight sun")
        assertNull(june.sunset, "no sunset during midnight sun")
    }

    @Test
    fun `a day's times always fall on that day, in every zone`() {
        // The invariant the day rows rest on: a row headed with a date must not
        // carry another date's times. Walked across zones either side of UTC
        // and across a year, because the UTC date a local event belongs to
        // shifts with both.
        val places =
            listOf(
                Triple(38.2527, -85.7585, ZoneId.of("America/Kentucky/Louisville")),
                Triple(-33.8688, 151.2093, ZoneId.of("Australia/Sydney")),
                Triple(35.6762, 139.6503, ZoneId.of("Asia/Tokyo")),
                // Kiritimati is UTC+14, the far edge of the date line.
                Triple(1.9, -157.4, ZoneId.of("Pacific/Kiritimati")),
                Triple(64.1466, -21.9426, ZoneId.of("Atlantic/Reykjavik")),
            )
        for ((lat, lon, zone) in places) {
            for (month in 1..12) {
                val date = LocalDate.of(2026, month, 15)
                val day = SunTimes.forDate(lat, lon, date, zone)
                day.sunrise?.let {
                    assertEquals(date, it.atZone(zone).toLocalDate(), "sunrise off its date at $zone in $month")
                }
                day.sunset?.let {
                    assertEquals(date, it.atZone(zone).toLocalDate(), "sunset off its date at $zone in $month")
                }
            }
        }
    }

    @Test
    fun `daysFrom returns consecutive days starting where asked`() {
        val start = LocalDate.of(2026, 8, 13)
        val days = SunTimes.daysFrom(38.2527, -85.7585, start, ZoneId.of("America/Kentucky/Louisville"), 3)
        assertEquals(listOf(start, start.plusDays(1), start.plusDays(2)), days.map { it.date })
        // And the sun keeps setting a little earlier through August.
        assertTrue(days[1].sunset!!.isBefore(days[0].sunset!!.plusSeconds(86400)), "sunset drifts earlier")
    }
}
