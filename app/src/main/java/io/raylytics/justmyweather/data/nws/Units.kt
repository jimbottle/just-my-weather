package io.raylytics.justmyweather.data.nws

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Pure unit-conversion + header-parsing helpers for the NWS client.
 *
 * Ported from almanac-bell's `mobile/src/nws/units.ts` (itself a port of the
 * Python `_to_fahrenheit` / `_to_inches` / `_to_mph` helpers). Same conversion
 * factors, same return-null-on-unknown-unit posture. Kept free of any I/O or
 * Android types so it can be unit-tested on the JVM without a fake HTTP layer.
 */
object Units {
    private const val PA_PER_INHG = 3386.389

    /**
     * Shared C→F primitive. The hourly-forecast endpoint sends short unit
     * codes ('C' / 'F') rather than `wmoUnit:degC`, so the forecast parser
     * calls this directly. Keeping the arithmetic in one place keeps both
     * call sites in sync.
     */
    fun celsiusToFahrenheit(value: Double): Double = value * 9.0 / 5.0 + 32.0

    fun toFahrenheit(value: Double?, unitCode: String): Double? {
        if (value == null) return null
        if (unitCode.contains("degC")) return celsiusToFahrenheit(value)
        if (unitCode.contains("degF")) return value
        return null
    }

    fun toInches(value: Double?, unitCode: String): Double? {
        if (value == null) return null
        if (unitCode.contains("mm")) return value / 25.4
        // Match `wmoUnit:in` (or a future `:in_h-1`) rather than a bare "in"
        // substring, which would false-match codes like `:invalid`.
        if (IN_UNIT.containsMatchIn(unitCode)) return value
        return null
    }

    fun toMph(value: Double?, unitCode: String): Double? {
        if (value == null) return null
        if (unitCode.contains("km_h-1") || unitCode.contains("km/h")) return value * 0.621371
        if (unitCode.contains("m_s-1") || unitCode.contains("m/s")) return value * 2.23694
        if (unitCode.contains("mi_h-1") || unitCode.contains("mph")) return value
        return null
    }

    /**
     * Convert NWS pressure to inches of mercury. Observations report Pascals
     * (`wmoUnit:Pa`); the gridpoint forecast uses kilopascals. Returns null on
     * an unrecognised unit rather than guessing.
     */
    fun toInchesOfMercury(value: Double?, unitCode: String): Double? {
        if (value == null) return null
        if (unitCode.contains("kPa")) return value * 1000 / PA_PER_INHG
        if (unitCode.contains("hPa") || unitCode.contains("mbar")) return value * 100 / PA_PER_INHG
        // Match `:Pa` strictly so `:hPa` / `:kPa` don't false-match here.
        if (PA_UNIT.containsMatchIn(unitCode)) return value / PA_PER_INHG
        if (unitCode.contains("inHg") || unitCode.contains("in_Hg")) return value
        return null
    }

    /**
     * Parse the NWS hourly-forecast wind format ("10 mph", "5 to 10 mph").
     * Returns the higher number on a range (matches "notify when wind reaches
     * N"). Null on anything unparseable rather than throwing.
     */
    fun parseWindSpeedString(value: String?): Double? {
        if (value.isNullOrEmpty()) return null
        val nums = NUMBER.findAll(value).map { it.value.toDouble() }.filter { it.isFinite() }.toList()
        return nums.maxOrNull()
    }

    /**
     * Parse a Retry-After header into a non-negative delay in seconds. RFC 7231
     * permits delta-seconds (what NWS sends) or an HTTP-date. Anything
     * unparseable falls back to 1 second so the retry loop keeps progressing.
     *
     * [now] is injectable for deterministic tests.
     */
    fun parseRetryAfter(header: String?, now: Instant = Instant.now()): Double {
        val trimmed = header?.trim()
        if (trimmed.isNullOrEmpty()) return 1.0
        val asNumber = trimmed.toDoubleOrNull()
        if (asNumber != null && asNumber.isFinite()) return maxOf(asNumber, 0.0)
        val instant =
            try {
                Instant.from(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.parse(trimmed))
            } catch (_: DateTimeParseException) {
                return 1.0
            }
        val delta = (instant.toEpochMilli() - now.toEpochMilli()) / 1000.0
        return maxOf(delta, 0.0)
    }

    private val IN_UNIT = Regex("(?:^|:)in(?:_|$)")
    private val PA_UNIT = Regex("(?:^|:)Pa(?:_|$)")
    private val NUMBER = Regex("\\d+(?:\\.\\d+)?")
}
