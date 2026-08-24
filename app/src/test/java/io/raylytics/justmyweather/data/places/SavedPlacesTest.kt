package io.raylytics.justmyweather.data.places

import io.raylytics.justmyweather.data.WeatherLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SavedPlacesTest {
    private val louisville = WeatherLocation(38.22, -85.74, "Louisville, KY")
    private val boulder = WeatherLocation(40.02, -105.27, "Boulder, CO")

    @Test
    fun `saving a place selects it, and saving it again does not duplicate`() {
        val once = SavedPlaces.EMPTY.add(louisville)
        assertEquals(listOf(louisville), once.places)
        assertEquals(louisville, once.current)

        val twice = once.add(boulder).add(louisville)
        assertEquals(2, twice.places.size, "no duplicate")
        assertEquals(louisville, twice.current, "re-saving selects it")
    }

    @Test
    fun `removing the place you are looking at hands you back to the device`() {
        val saved = SavedPlaces.EMPTY.add(louisville).add(boulder)
        assertEquals(boulder, saved.current)
        val after = saved.remove(boulder.label)
        assertNull(after.selected, "not an arbitrary neighbour")
        assertNull(after.current)
        assertEquals(listOf(louisville), after.places)
    }

    @Test
    fun `removing a place you are not looking at leaves the selection alone`() {
        val saved = SavedPlaces.EMPTY.add(boulder).add(louisville)
        val after = saved.remove(boulder.label)
        assertEquals(louisville, after.current)
    }

    @Test
    fun `no selection means follow the device`() {
        assertNull(SavedPlaces.EMPTY.current)
        val saved = SavedPlaces.EMPTY.add(louisville).select(null)
        assertNull(saved.current, "the place is kept, just not shown")
        assertTrue(saved.places.isNotEmpty())
    }

    @Test
    fun `a selection naming a place that is not there resolves to the device`() {
        // The safe direction to fail: a dangling label must not crash or point
        // somewhere arbitrary.
        val saved = SavedPlaces(places = listOf(louisville), selected = "Atlantis, XX")
        assertNull(saved.current)
    }

    @Test
    fun `the codec round-trips places and the selection`() {
        val saved = SavedPlaces.EMPTY.add(louisville).add(boulder).select(louisville.label)
        val restored = SavedPlacesCodec.decode(SavedPlacesCodec.encode(saved))
        assertEquals(saved, restored)
    }

    @Test
    fun `absent or corrupt storage decodes to an empty list, never a crash`() {
        assertEquals(SavedPlaces.EMPTY, SavedPlacesCodec.decode(null))
        assertEquals(SavedPlaces.EMPTY, SavedPlacesCodec.decode(""))
        assertEquals(SavedPlaces.EMPTY, SavedPlacesCodec.decode("{ not json"))
    }

    @Test
    fun `a non-finite coordinate is dropped rather than stored as a place`() {
        // NaN reaches PointCacheKey.of, where rounding it throws, on the launch
        // path — the same trap DataStoreLastLocationStore documents.
        val raw = """{"places":[{"label":"Broken","lat":null,"lon":-85.0}],"selected":"Broken"}"""
        assertEquals(SavedPlaces.EMPTY, SavedPlacesCodec.decode(raw))

        val mixed =
            """{"places":[{"label":"Good","lat":38.22,"lon":-85.74}],"selected":"Gone"}"""
        val decoded = SavedPlacesCodec.decode(mixed)
        assertEquals(1, decoded.places.size)
        assertNull(decoded.selected, "a selection with nothing behind it is dropped")
    }
}
