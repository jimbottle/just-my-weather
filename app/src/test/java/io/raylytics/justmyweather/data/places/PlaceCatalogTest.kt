package io.raylytics.justmyweather.data.places

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The search rules, settled here rather than by tapping at a phone. Every
 * ranking claim in [PlaceCatalog]'s docs has a case below — a ranking nobody
 * asserts is a ranking that drifts.
 */
class PlaceCatalogTest {
    private val catalog =
        PlaceCatalog(
            PlaceCatalog.parse(
                sequenceOf(
                    "Louisville\tKY\t38.22\t-85.74",
                    "Louisville\tCO\t39.97\t-105.14",
                    "St. Louis\tMO\t38.64\t-90.24",
                    "York\tPA\t39.96\t-76.73",
                    "Yorkana\tPA\t39.96\t-76.60",
                    "New York\tNY\t40.66\t-73.94",
                    "Bayamón\tPR\t18.36\t-66.17",
                    "Mountain View\tCA\t37.40\t-122.08",
                ),
            ),
        )

    @Test
    fun `a name that starts with the query beats one that merely contains it`() {
        val names = catalog.search("louis").map { it.label }
        assertEquals("Louisville, CO", names.first())
        assertTrue(names.contains("St. Louis, MO"))
        // Both Louisvilles come before the one that only contains the string.
        assertTrue(names.indexOf("Louisville, KY") < names.indexOf("St. Louis, MO"))
    }

    @Test
    fun `a word start beats mid-word, and an exact prefix beats both`() {
        val names = catalog.search("york").map { it.label }
        // "York" starts with it; "New York" has it at a word start; "Yorkana"
        // starts with it too but is longer, so length breaks that tie.
        assertEquals(listOf("York, PA", "Yorkana, PA", "New York, NY"), names)
    }

    @Test
    fun `shorter names win ties, so the obvious answer is not buried`() {
        assertEquals("York, PA", catalog.search("york").first().label)
    }

    @Test
    fun `a state can be given after a comma or after a space`() {
        assertEquals(listOf("Louisville, KY"), catalog.search("louisville, ky").map { it.label })
        assertEquals(listOf("Louisville, KY"), catalog.search("louisville ky").map { it.label })
        assertEquals(listOf("Louisville, CO"), catalog.search("louisville, co").map { it.label })
    }

    @Test
    fun `a place whose name ends in two letters is not mistaken for a state`() {
        // "New York" must stay a search for New York, not "New" in state "YO".
        assertEquals(listOf("New York, NY"), catalog.search("new york").map { it.label })
        // And a bare state code searches for places named that, rather than
        // silently listing everything in the state.
        assertTrue(catalog.search("ky").isEmpty())
    }

    @Test
    fun `accents are ignored, because the keyboard may not offer them`() {
        assertEquals(listOf("Bayamón, PR"), catalog.search("bayamon").map { it.label })
        assertEquals(listOf("Bayamón, PR"), catalog.search("Bayamón").map { it.label })
    }

    @Test
    fun `an empty or whitespace query matches nothing rather than everything`() {
        assertTrue(catalog.search("").isEmpty())
        assertTrue(catalog.search("   ").isEmpty())
    }

    @Test
    fun `the limit is honoured`() {
        assertEquals(1, catalog.search("louis", limit = 1).size)
    }

    @Test
    fun `parse skips malformed lines instead of failing the whole catalog`() {
        val places =
            PlaceCatalog.parse(
                sequenceOf(
                    "Good\tKY\t38.22\t-85.74",
                    "TooFewColumns\tKY",
                    "BadLat\tKY\tnorth\t-85.74",
                    // No name at all.
                    "\tKY\t38.0\t-85.0",
                    "AlsoGood\tTN\t35.82\t-84.05",
                ),
            )
        assertEquals(listOf("Good", "AlsoGood"), places.map { it.name })
    }

    @Test
    fun `a place converts to the location the rest of the app speaks`() {
        val place = catalog.search("mountain view").first()
        val location = place.toLocation()
        assertEquals("Mountain View, CA", location.label)
        assertEquals(37.40, location.latitude, 0.001)
        assertEquals(-122.08, location.longitude, 0.001)
    }
}
