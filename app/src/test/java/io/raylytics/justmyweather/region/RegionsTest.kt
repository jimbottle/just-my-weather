package io.raylytics.justmyweather.region

import io.raylytics.justmyweather.view.Conventions
import io.raylytics.justmyweather.view.DateOrder
import io.raylytics.justmyweather.view.PressureUnit
import io.raylytics.justmyweather.view.UnitPrefs
import io.raylytics.justmyweather.view.WindUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What switching region switches: each first-wave region's conventions,
 * pinned, so a JDK or CLDR update that moved one is caught here. */
class RegionsTest {
    @Test
    fun `the United States reads exactly as the app always did`() {
        assertEquals(Conventions.US, Regions.of("US").conventions)
        assertEquals(PlayStatus.LIVE, Regions.of("US").play)
    }

    @Test
    fun `wave one regions read the local way`() {
        val gb = Regions.of("GB").conventions
        assertEquals(UnitPrefs.UK, gb.units, "Celsius, mph, hPa, mm")
        assertEquals(DateOrder.DAY_FIRST, gb.dateOrder)
        assertTrue(gb.clock24)

        val ca = Regions.of("CA").conventions
        assertEquals(UnitPrefs.METRIC.copy(pressure = PressureUnit.KILOPASCALS), ca.units)
        assertFalse(ca.clock24)

        val au = Regions.of("AU").conventions
        assertEquals(UnitPrefs.METRIC, au.units)
        assertEquals(DateOrder.DAY_FIRST, au.dateOrder)
        assertFalse(au.clock24)

        assertEquals(UnitPrefs.METRIC, Regions.of("NZ").conventions.units)
        assertEquals(UnitPrefs.METRIC, Regions.of("IE").conventions.units)
        assertTrue(Regions.of("IE").conventions.clock24)
    }

    @Test
    fun `any country gets derived conventions, prepared or not`() {
        val norway = Regions.of("no")
        assertEquals("NO", norway.code, "codes are normalised to Play's upper case")
        assertEquals(WindUnit.METRES_PER_SECOND, norway.conventions.units.wind)
        assertTrue(norway.conventions.clock24)
        assertNull(norway.play, "not prepared: adapts, but is not a region we ship to")
        assertNull(norway.defaultPlace)
        assertEquals("Japan", Regions.of("JP").name)
    }

    @Test
    fun `every prepared region is a real code with a first-run place`() {
        assertTrue(Regions.prepared.isNotEmpty())
        for (region in Regions.prepared) {
            assertTrue(Regions.isKnown(region.code), region.code)
            assertTrue(region.defaultPlace != null, region.code)
        }
        assertEquals(Regions.prepared.size, Regions.prepared.map { it.code }.toSet().size, "no duplicates")
    }

    @Test
    fun `wave two regions read metric, each with its own date order and clock`() {
        val expected =
            mapOf(
                "IN" to (DateOrder.DAY_FIRST to false),
                "PK" to (DateOrder.DAY_FIRST to false),
                "NG" to (DateOrder.DAY_FIRST to true),
                "PH" to (DateOrder.MONTH_FIRST to false),
                "ZA" to (DateOrder.DAY_FIRST to true),
                "KE" to (DateOrder.DAY_FIRST to true),
                "GH" to (DateOrder.DAY_FIRST to false),
                "MY" to (DateOrder.DAY_FIRST to false),
                "SG" to (DateOrder.DAY_FIRST to false),
            )
        for ((code, dates) in expected) {
            val region = Regions.of(code)
            assertEquals(UnitPrefs.METRIC, region.conventions.units, code)
            assertEquals(dates.first, region.conventions.dateOrder, code)
            assertEquals(dates.second, region.conventions.clock24, code)
        }
    }

    @Test
    fun `an unprepared year-first country still derives the order it writes day and month in`() {
        // Japan writes 10/6; derivation reads the medium pattern, not a
        // year-first short one that would hide the order.
        assertEquals(DateOrder.MONTH_FIRST, Regions.of("JP").conventions.dateOrder)
        assertEquals(DateOrder.DAY_FIRST, Regions.conventionsFor("ZA").dateOrder)
    }
}
