package io.raylytics.justmyweather.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/**
 * One day's sunrise and sunset, as they fall on that LOCAL date.
 *
 * A day rather than "the next of each", because those two are not the same
 * day for most of the waking hours: from sunrise until sunset, tonight's
 * sunset belongs to today while the next sunrise belongs to tomorrow. Keyed to
 * a date, each time is unambiguous without a qualifier hung off it.
 *
 * Either may be null. Above the Arctic circle the sun can decline to rise or
 * set for months, and saying nothing is better than inventing a time.
 */
data class SunDay(
    val date: LocalDate,
    val sunrise: Instant?,
    val sunset: Instant?,
)

object SunTimes {
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
     * [count] days of sun times starting at [start], in [zone].
     *
     * The caller gets whole days and picks the one it wants, rather than being
     * handed a "next" that silently spans two dates.
     */
    fun daysFrom(
        latitude: Double,
        longitude: Double,
        start: LocalDate,
        zone: ZoneId,
        count: Int,
    ): List<SunDay> = (0 until count).map { forDate(latitude, longitude, start.plusDays(it.toLong()), zone) }

    /** Sunrise and sunset falling on the local date [date] in [zone]. */
    fun forDate(latitude: Double, longitude: Double, date: LocalDate, zone: ZoneId): SunDay =
        SunDay(
            date = date,
            sunrise = eventOnLocalDate(latitude, longitude, date, zone, rising = true),
            sunset = eventOnLocalDate(latitude, longitude, date, zone, rising = false),
        )

    /**
     * The event that lands on local date [date]. [eventOn] works in UTC dates,
     * and a local date straddles two of them at every offset but zero — so try
     * the neighbours and keep whichever actually falls on the day asked for.
     */
    private fun eventOnLocalDate(
        latitude: Double,
        longitude: Double,
        date: LocalDate,
        zone: ZoneId,
        rising: Boolean,
    ): Instant? {
        for (offset in -1..1) {
            val event = eventOn(date.plusDays(offset.toLong()), latitude, longitude, rising) ?: continue
            if (event.atZone(zone).toLocalDate() == date) return event
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
