package io.raylytics.justmyweather.view

import io.raylytics.justmyweather.region.Regions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ConventionsTest {
    private val us = Conventions.US
    private val uk = Regions.of("GB").conventions
    private val canada = Regions.of("CA").conventions

    @Test
    fun `canonical values read in each region's units`() {
        assertEquals("72°", us.temperature(72.0))
        assertEquals("22°", uk.temperature(72.0))
        assertEquals("0°", uk.temperature(32.0))
        assertEquals("10 mph", uk.windSpeed(10.0), "the UK keeps miles per hour")
        assertEquals("16 km/h", canada.windSpeed(10.0))
        assertEquals("30.12 inHg", us.pressure(30.12))
        assertEquals("1020 hPa", uk.pressure(30.12))
        assertEquals("102.0 kPa", canada.pressure(30.12))
        assertEquals("0.25 in", us.precipitation(0.25))
        assertEquals("6.4 mm", uk.precipitation(0.25))
    }

    @Test
    fun `calm is the same air in every unit`() {
        assertEquals("Calm", uk.wind(0.6, "SW"))
        assertEquals("19 km/h SW", canada.wind(12.0, "SW"))
        assertEquals("—", canada.wind(null, "SW"))
    }

    @Test
    fun `dates and the clock follow the region`() {
        val day = LocalDate.of(2026, 10, 6)
        val at = Instant.parse("2026-10-06T15:05:00Z")
        val utc = ZoneId.of("UTC")
        assertEquals("10/6", day.format(us.shortDate))
        assertEquals("6/10", day.format(uk.shortDate))
        assertEquals("Tue 6/10", day.format(uk.dayAndDate))
        assertEquals("6 Oct", day.format(uk.monthDay))
        assertEquals("3:05 PM", us.clock(at, utc))
        assertEquals("15:05", uk.clock(at, utc))
    }

    @Test
    fun `a threshold step converts by scale, a temperature by offset too`() {
        assertEquals(5.0, TemperatureUnit.CELSIUS.spanFromFahrenheit(9.0), 1e-9)
        assertEquals(35.6, TemperatureUnit.CELSIUS.toFahrenheit(2.0), 1e-9)
        assertEquals(10.0, WindUnit.KMH.toMph(WindUnit.KMH.fromMph(10.0)), 1e-9)
    }
}
