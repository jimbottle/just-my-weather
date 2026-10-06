package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.data.nws.Units
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * How numbers and times READ: the one place a value in the app's canonical
 * units becomes text. The data layer keeps everything in American units
 * (°F, mph, inHg, inches) end to end — the alert thresholds and every cache
 * are stored that way — and nothing converts until it is shown, here. A
 * region (region/Regions.kt) picks a Conventions; the user may override the
 * units one by one; every screen, sheet, widget and notification formats
 * through the result. Pure, so each convention is settled on the JVM.
 *
 * To add a unit: one enum entry below, with its conversion from canonical,
 * its label and its precision. Nothing else changes.
 */

enum class TemperatureUnit(val key: String, val label: String) {
    FAHRENHEIT("f", "°F"),
    CELSIUS("c", "°C"),
    ;

    fun fromFahrenheit(f: Double): Double = if (this == FAHRENHEIT) f else (f - 32.0) * 5.0 / 9.0

    fun toFahrenheit(value: Double): Double = if (this == FAHRENHEIT) value else Units.celsiusToFahrenheit(value)
}

enum class WindUnit(val key: String, val label: String, private val perMph: Double) {
    MPH("mph", "mph", 1.0),
    KMH("kmh", "km/h", 1.609344),
    METRES_PER_SECOND("ms", "m/s", 0.44704),
    KNOTS("kn", "kn", 0.868976),
    ;

    fun fromMph(mph: Double): Double = mph * perMph

    fun toMph(value: Double): Double = value / perMph
}

enum class PressureUnit(val key: String, val label: String, private val perInHg: Double, val decimals: Int) {
    INCHES_OF_MERCURY("inhg", "inHg", 1.0, 2),
    HECTOPASCALS("hpa", "hPa", 33.8639, 0),
    KILOPASCALS("kpa", "kPa", 3.38639, 1),
    MILLIMETRES_OF_MERCURY("mmhg", "mmHg", 25.4, 0),
    ;

    fun fromInHg(inHg: Double): Double = inHg * perInHg

    fun toInHg(value: Double): Double = value / perInHg
}

enum class PrecipitationUnit(val key: String, val label: String, private val perInch: Double, val decimals: Int) {
    INCHES("in", "in", 1.0, 2),
    MILLIMETRES("mm", "mm", 25.4, 1),
    ;

    fun fromInches(inches: Double): Double = inches * perInch

    fun toInches(value: Double): Double = value / perInch
}

/** The four dimensions the app shows, each in one unit. */
data class UnitPrefs(
    val temperature: TemperatureUnit,
    val wind: WindUnit,
    val pressure: PressureUnit,
    val precipitation: PrecipitationUnit,
) {
    companion object {
        /** The United States, and what the app showed before it had regions. */
        val US = UnitPrefs(
            TemperatureUnit.FAHRENHEIT,
            WindUnit.MPH,
            PressureUnit.INCHES_OF_MERCURY,
            PrecipitationUnit.INCHES,
        )

        /** Most of the world. */
        val METRIC = UnitPrefs(
            TemperatureUnit.CELSIUS,
            WindUnit.KMH,
            PressureUnit.HECTOPASCALS,
            PrecipitationUnit.MILLIMETRES,
        )

        /** The United Kingdom's mix: Celsius and millimetres, but road-sign
         * miles per hour for wind — what the Met Office itself shows. */
        val UK = METRIC.copy(wind = WindUnit.MPH)
    }
}

/** Which way a numeric date runs: 10/6 (US) or 6/10 (most of the world).
 * Year-first countries write the short month/day as month first, so they
 * read [MONTH_FIRST] here. */
enum class DateOrder { MONTH_FIRST, DAY_FIRST }

/**
 * A region's way of reading weather: its units, its date order, and its
 * clock. Every formatting method takes values in the app's canonical units
 * and returns display text.
 */
data class Conventions(
    val units: UnitPrefs,
    val dateOrder: DateOrder,
    /** 24-hour clock ("16:00") rather than 12-hour ("4:00 PM"). */
    val clock24: Boolean,
) {
    // ── Numbers ────────────────────────────────────────────────────────────

    /** "72°" — the unit letter is left off, as on every weather app. */
    fun temperature(fahrenheit: Double): String = "${units.temperature.fromFahrenheit(fahrenheit).roundToInt()}°"

    /** [temperature], or an em-dash for a value the source did not send. */
    fun degrees(fahrenheit: Double?): String = fahrenheit?.let(::temperature) ?: "—"

    /** "12 mph" / "19 km/h". */
    fun windSpeed(mph: Double): String = "${units.wind.fromMph(mph).roundToInt()} ${units.wind.label}"

    /** "12 mph SW", "Calm", or an em-dash — one wording for observation and
     * forecast alike. Calm is judged on the canonical value, so it means the
     * same air in every unit. */
    fun wind(mph: Double?, direction: String?): String =
        when {
            mph == null -> "—"
            mph < 1.0 -> "Calm"
            direction != null -> "${windSpeed(mph)} $direction"
            else -> windSpeed(mph)
        }

    /** "30.12 inHg" / "1020 hPa". */
    fun pressure(inHg: Double): String =
        "${fixed(units.pressure.fromInHg(inHg), units.pressure.decimals)} ${units.pressure.label}"

    /** "0.25 in" / "6.4 mm". */
    fun precipitation(inches: Double): String =
        "${fixed(units.precipitation.fromInches(inches), units.precipitation.decimals)} ${units.precipitation.label}"

    // ── Times and dates ──────────────────────────────────────────────────

    /** "4:05 PM" / "16:05". */
    val clock: DateTimeFormatter get() = pattern(if (clock24) "HH:mm" else "h:mm a")

    /** "4 PM" / "16:00" — an hour on its own, as a detail sheet titles it. */
    val hour: DateTimeFormatter get() = pattern(if (clock24) "HH:00" else "h a")

    /** "4PM" / "16:00" — the compact form a forecast tile has room for. */
    val tileHour: DateTimeFormatter get() = pattern(if (clock24) "HH:00" else "ha")

    /** "10/6" / "6/10". */
    val shortDate: DateTimeFormatter get() = pattern(if (dateOrder == DateOrder.DAY_FIRST) "d/M" else "M/d")

    /** "Oct 6" / "6 Oct". */
    val monthDay: DateTimeFormatter get() = pattern(if (dateOrder == DateOrder.DAY_FIRST) "d MMM" else "MMM d")

    /** "Tuesday, October 6" / "Tuesday 6 October". */
    val longDate: DateTimeFormatter
        get() = pattern(if (dateOrder == DateOrder.DAY_FIRST) "EEEE d MMMM" else "EEEE, MMMM d")

    /** "Tue 10/6" / "Tue 6/10": a day named past the source's own names,
     * where a bare weekday would repeat one already on screen. */
    val dayAndDate: DateTimeFormatter
        get() = pattern(if (dateOrder == DateOrder.DAY_FIRST) "EEE d/M" else "EEE M/d")

    fun clock(at: Instant, zone: ZoneId): String = at.atZone(zone).format(clock)

    companion object {
        /** What the app showed before it had regions: the United States. */
        val US = Conventions(UnitPrefs.US, DateOrder.MONTH_FIRST, clock24 = false)

        /** Names (Oct, Tuesday, PM) are English because the UI is: a month
         * name in another language inside an English sentence helps nobody. */
        private fun pattern(p: String): DateTimeFormatter = DateTimeFormatter.ofPattern(p, Locale.ENGLISH)

        /** Locale.US on purpose: a device in German would otherwise write
         * "1020,5" beside an English unit label. */
        private fun fixed(value: Double, decimals: Int): String =
            if (decimals == 0) value.roundToInt().toString() else String.format(Locale.US, "%.${decimals}f", value)
    }
}
