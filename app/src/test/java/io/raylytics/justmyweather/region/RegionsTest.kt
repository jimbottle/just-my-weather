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
        assertEquals(AdConsent.NOT_REQUIRED, Regions.of("US").adConsent)
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
    fun `the banner is withheld wherever a certified consent platform is required`() {
        for (code in listOf("GB", "IE", "DE", "FR", "NO", "IS", "CH")) {
            assertEquals(AdConsent.CERTIFIED_CMP_REQUIRED, Regions.of(code).adConsent, code)
        }
        for (code in listOf("US", "CA", "AU", "NZ", "IN")) {
            assertEquals(AdConsent.NOT_REQUIRED, Regions.of(code).adConsent, code)
        }
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
}
