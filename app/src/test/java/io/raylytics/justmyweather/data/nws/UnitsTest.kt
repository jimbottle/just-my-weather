package io.raylytics.justmyweather.data.nws

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Covers the conversion + parsing primitives ported from almanac-bell. These
 * are the load-bearing arithmetic of the whole data layer, so they're tested
 * directly with no HTTP in sight.
 */
class UnitsTest {
    private val tol = 1e-6

    @Test
    fun `celsius to fahrenheit`() {
        assertEquals(32.0, Units.celsiusToFahrenheit(0.0), tol)
        assertEquals(212.0, Units.celsiusToFahrenheit(100.0), tol)
    }

    @Test
    fun `toFahrenheit honours unit code and nulls`() {
        assertEquals(50.0, Units.toFahrenheit(10.0, "wmoUnit:degC")!!, tol)
        assertEquals(10.0, Units.toFahrenheit(10.0, "wmoUnit:degF")!!, tol)
        assertNull(Units.toFahrenheit(10.0, "wmoUnit:degK"))
        assertNull(Units.toFahrenheit(null, "wmoUnit:degC"))
    }

    @Test
    fun `toInches mm and inch codes, rejects lookalikes`() {
        assertEquals(1.0, Units.toInches(25.4, "wmoUnit:mm")!!, tol)
        assertEquals(2.0, Units.toInches(2.0, "wmoUnit:in")!!, tol)
        // "invalid" contains "in" but must not match.
        assertNull(Units.toInches(2.0, "wmoUnit:invalid"))
    }

    @Test
    fun `toMph across unit systems`() {
        assertEquals(6.21371, Units.toMph(10.0, "wmoUnit:km_h-1")!!, tol)
        assertEquals(22.3694, Units.toMph(10.0, "wmoUnit:m_s-1")!!, tol)
        assertEquals(10.0, Units.toMph(10.0, "wmoUnit:mi_h-1")!!, tol)
        assertNull(Units.toMph(10.0, "wmoUnit:knots"))
    }

    @Test
    fun `toInchesOfMercury distinguishes Pa kPa hPa, rejects lookalikes`() {
        assertEquals(29.92, Units.toInchesOfMercury(101325.0, "wmoUnit:Pa")!!, 0.01)
        assertEquals(29.92, Units.toInchesOfMercury(101.325, "wmoUnit:kPa")!!, 0.01)
        assertEquals(29.92, Units.toInchesOfMercury(1013.25, "wmoUnit:hPa")!!, 0.01)
        // hPa/kPa must not fall through to the bare-Pa branch.
        assertEquals(29.92, Units.toInchesOfMercury(1013.25, "wmoUnit:mbar")!!, 0.01)
    }

    @Test
    fun `parseWindSpeedString takes the max of a range`() {
        assertEquals(10.0, Units.parseWindSpeedString("10 mph")!!, tol)
        assertEquals(10.0, Units.parseWindSpeedString("5 to 10 mph")!!, tol)
        assertNull(Units.parseWindSpeedString("calm"))
        assertNull(Units.parseWindSpeedString(null))
    }

    @Test
    fun `parseRetryAfter handles delta-seconds, empty, and http-date`() {
        assertEquals(120.0, Units.parseRetryAfter("120"), tol)
        assertEquals(1.0, Units.parseRetryAfter(null), tol)
        assertEquals(1.0, Units.parseRetryAfter("   "), tol)
        assertEquals(0.0, Units.parseRetryAfter("-5"), tol)
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val future = "Thu, 01 Jan 2026 00:00:30 GMT"
        assertEquals(30.0, Units.parseRetryAfter(future, now), 0.5)
    }
}
