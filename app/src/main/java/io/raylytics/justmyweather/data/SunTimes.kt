package io.raylytics.justmyweather.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/*
 * When the sun next rises and sets, computed rather than fetched.
 *
 * NWS does not publish sun times, and adding a second HTTP source for what is
 * pure astronomy would buy a network dependency, another failure mode and an
 * offline hole for no accuracy gain. Latitude, longitude and the date are
 * enough — so this is pure, instant, offline, and tests on the JVM. It also
 * means the sun times still render when the weather fetch has failed and the
 * glance is showing a remembered reading.
 *
 * The algorithm is NOAA's solar position calculation, the same one behind
 * their public calculator. Spot-checked against the US Naval Observatory from
 * the equator to 64°N: the worst deviation measured was about 90 seconds (New
 * York), most were under half a minute. USNO publishes to the minute and runs
 * a fuller model, so some of that gap is theirs; either way it is far finer
 * than a glance needs, and a minute or two is not worth a network call.
 *
 * A note for whoever revisits the accuracy: an early draft was validated
 * against sunrise-sunset.org, which disagreed by five minutes at 64°N and sent
 * this code chasing an error that turned out to be the reference's. USNO and
 * open-meteo agree with each other and with this. Check against those.
 */

/** The next sunrise and sunset after some moment. Either is null when the sun
 * does not do that thing within the search window — see [SunTimes.next]. */
data class SunEvents(
    val sunrise: Instant?,
    val sunset: Instant?,
)

object SunTimes {
    /**
     * How far ahead to look. Three days covers every latitude where the sun
     * rises and sets daily, plus the shoulder either side of a polar summer
     * or winter. Beyond that the honest answer is "not soon", which is what a
     * null says — better than printing a date months away as if it were a
     * time of day.
     */
    private const val SEARCH_DAYS = 3

    private const val MINUTES_PER_DAY = 1440.0

    /**
     * The sun's centre sits 0.833° below the horizon at the moment we call
     * sunrise: about 0.267° for the solar disc's radius plus roughly 0.567°
     * of atmospheric refraction lifting the image. Using a flat 90° would put
     * every answer a few minutes out.
     */
    private const val ZENITH_DEGREES = 90.833

    /** Passes of the solve. The first can only assume solar noon; the second
     * re-evaluates the sun's position at the time the first landed on. A third
     * moves the answer by under a second, so two is where it stops paying. */
    private const val REFINEMENTS = 2

    /**
     * The next sunrise and sunset strictly after [from] at this coordinate.
     *
     * Each is searched independently, because they are independent questions:
     * at three in the afternoon the next sunset is tonight and the next
     * sunrise is tomorrow, and a caller that assumed they came from the same
     * day would show yesterday's sunrise half the time.
     */
    fun next(latitude: Double, longitude: Double, from: Instant): SunEvents =
        SunEvents(
            sunrise = nextEvent(latitude, longitude, from, rising = true),
            sunset = nextEvent(latitude, longitude, from, rising = false),
        )

    private fun nextEvent(latitude: Double, longitude: Double, from: Instant, rising: Boolean): Instant? {
        // Start a day early: the UTC date rolls at a different moment than the
        // local one, so "today" at this longitude may still be yesterday in UTC
        // and its event may not have happened yet.
        val start = from.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
        for (offset in 0..(SEARCH_DAYS + 1)) {
            val event = eventOn(start.plusDays(offset.toLong()), latitude, longitude, rising)
            if (event != null && event.isAfter(from)) {
                // Cheap guard against returning something absurd if the search
                // window is ever widened without revisiting this.
                if (Duration.between(from, event) <= Duration.ofDays(SEARCH_DAYS.toLong())) return event
                return null
            }
        }
        return null
    }

    /**
     * Sunrise or sunset on one UTC date, or null when the sun stays up or
     * stays down — which is a real answer above the Arctic and below the
     * Antarctic circle, not an error.
     */
    private fun eventOn(date: LocalDate, latitude: Double, longitude: Double, rising: Boolean): Instant? {
        val latRad = Math.toRadians(latitude)
        val zenithRad = Math.toRadians(ZENITH_DEGREES)

        // Solve by iteration, starting from solar noon. The sun's declination
        // and the equation of time are evaluated AT the moment being solved
        // for, so the first pass — which can only guess noon — is refined by
        // the next.
        var minutes = 720.0
        repeat(REFINEMENTS) {
            val sun = solarPosition(julianCentury(date, minutes))
            val declRad = Math.toRadians(sun.declinationDegrees)

            // The hour angle: how far the Earth must turn between the event
            // and solar noon. Out of range means the sun never reaches that
            // height today — midnight sun, or polar night.
            val cosHourAngle =
                (cos(zenithRad) / (cos(latRad) * cos(declRad))) - tan(latRad) * tan(declRad)
            if (abs(cosHourAngle) > 1.0) return null

            val hourAngle = Math.toDegrees(acos(cosHourAngle)).let { if (rising) it else -it }

            // Minutes from UTC midnight. The longitude term converts the angle
            // to clock time; eqTime corrects the sun's own irregularity.
            minutes = 720.0 - 4.0 * (longitude + hourAngle) - sun.equationOfTimeMinutes
        }
        return date.atStartOfDay(ZoneOffset.UTC).toInstant()
            .plusSeconds((minutes * 60.0).toLong())
    }

    /** Julian centuries since J2000.0 at [minutes] past UTC midnight on [date].
     * 2440587.5 is the Julian Date of the Unix epoch; 2451545.0 is J2000.0. */
    private fun julianCentury(date: LocalDate, minutes: Double): Double {
        val julianDay = date.toEpochDay() + 2440587.5 + minutes / MINUTES_PER_DAY
        return (julianDay - 2451545.0) / 36525.0
    }

    private data class SolarPosition(
        val declinationDegrees: Double,
        val equationOfTimeMinutes: Double,
    )

    /**
     * Where the sun is, and how far the clock has drifted from it.
     *
     * NOAA's full formulation rather than the short Fourier series often
     * quoted alongside it. Both land within a minute of the US Naval
     * Observatory at the spot-checks in SunTimesTest, including 64°N in
     * December; the full one is kept because it stays good far from the epoch
     * its coefficients were fitted around, and this app's own source (NWS)
     * reaches Alaska at 71°N, where the sun grazes the horizon and a small
     * error in its height becomes a large one in time.
     */
    private fun solarPosition(t: Double): SolarPosition {
        val meanLongitude = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
        val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        // Equation of centre: the correction from a circular orbit to the real
        // elliptical one.
        val centre =
            sin(Math.toRadians(meanAnomaly)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(Math.toRadians(2 * meanAnomaly)) * (0.019993 - 0.000101 * t) +
                sin(Math.toRadians(3 * meanAnomaly)) * 0.000289

        // Apparent longitude folds in nutation and aberration — the small
        // wobble and light-travel corrections that separate where the sun is
        // from where it appears.
        val apparentLongitude =
            meanLongitude + centre - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * t))

        val meanObliquity =
            23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = meanObliquity + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * t))

        val declination =
            Math.toDegrees(
                asin(sin(Math.toRadians(obliquity)) * sin(Math.toRadians(apparentLongitude))),
            )

        val y = tan(Math.toRadians(obliquity / 2.0)).let { it * it }
        val equationOfTime =
            4.0 * Math.toDegrees(
                y * sin(2 * Math.toRadians(meanLongitude)) -
                    2 * eccentricity * sin(Math.toRadians(meanAnomaly)) +
                    4 * eccentricity * y * sin(Math.toRadians(meanAnomaly)) *
                    cos(2 * Math.toRadians(meanLongitude)) -
                    0.5 * y * y * sin(4 * Math.toRadians(meanLongitude)) -
                    1.25 * eccentricity * eccentricity * sin(2 * Math.toRadians(meanAnomaly)),
            )
        return SolarPosition(declination, equationOfTime)
    }
}
