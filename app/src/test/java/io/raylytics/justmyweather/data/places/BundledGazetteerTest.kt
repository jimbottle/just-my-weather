package io.raylytics.justmyweather.data.places

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The asset the app actually ships, read by the parser the app actually uses.
 * [PlaceCatalog.parse] skips malformed rows rather than failing, which is
 * right on a phone and dangerous at build time: a builder change that broke
 * every foreign row would ship as a silently US-only search. So here every
 * row must survive.
 */
class BundledGazetteerTest {
    // Gradle runs unit tests from the module directory.
    private val lines = File("src/main/assets/places.tsv").readLines()
    private val catalog = PlaceCatalog(PlaceCatalog.parse(lines.asSequence()))

    @Test
    fun `every bundled row parses`() {
        assertEquals(lines.size, catalog.places.size)
    }

    @Test
    fun `the first regions' cities are there, with their zones`() {
        val expected =
            mapOf(
                "Toronto, CA" to "America/Toronto",
                "London, GB" to "Europe/London",
                "Dublin, IE" to "Europe/Dublin",
                "Sydney, AU" to "Australia/Sydney",
                "Auckland, NZ" to "Pacific/Auckland",
            )
        for ((query, zone) in expected) {
            val place = catalog.search(query).first()
            assertEquals(zone, place.timeZone, query)
        }
        assertTrue(catalog.search("louisville, ky").isNotEmpty(), "the US rows are still there")
    }

    @Test
    fun `a place whose name ends in a country code is still found by its name`() {
        // Each ends in a two-letter token that is also a code (ET, NA, NE, LA).
        for (name in listOf("Roi Et", "Bang Na", "Mui Ne", "Sơn La", "Lak Si")) {
            assertEquals(name, catalog.search(name).firstOrNull()?.name, name)
        }
        // A real suffix still narrows: Kentucky's London, not England's.
        assertEquals("London, KY", catalog.search("london ky").first().label)
    }
}
