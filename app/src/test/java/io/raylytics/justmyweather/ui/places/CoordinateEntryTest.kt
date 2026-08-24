package io.raylytics.justmyweather.ui.places

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * The coordinate escape hatch's rules, asserted directly rather than inferred
 * from a greyed-out button.
 *
 * The locale cases are the point of this file. A device set to German formats
 * 38.22 as "38,22", so a locale-sensitive label would read "38,22, -85,74" —
 * a comma serving as both the decimal point and the separator between two
 * numbers. Worse, [io.raylytics.justmyweather.data.places.SavedPlaces] uses the
 * label as a place's identity, so the same coordinates would become a
 * different place after a language change.
 */
class CoordinateEntryTest {
    private val original: Locale = Locale.getDefault()

    @AfterEach
    fun restore() = Locale.setDefault(original)

    @Test
    fun `the coordinate label uses dots whatever the device locale`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("38.22, -85.74", coordinateLabel(38.22, -85.74))
        Locale.setDefault(Locale.US)
        assertEquals("38.22, -85.74", coordinateLabel(38.22, -85.74))
    }

    @Test
    fun `an unnamed place is labelled by its coordinates, locale-independently`() {
        Locale.setDefault(Locale.GERMANY)
        val place = parseCoordinates(name = "  ", latitude = "38.22", longitude = "-85.74")
        // Same label a US device would produce: the identity of a saved place
        // must not depend on the language the phone is set to.
        assertEquals("38.22, -85.74", place?.label)
    }

    @Test
    fun `a given name wins over the coordinates`() {
        val place = parseCoordinates(name = " Cabin ", latitude = "45.5", longitude = "-110.0")
        assertEquals("Cabin", place?.label)
        assertEquals(45.5, place?.latitude)
        assertEquals(-110.0, place?.longitude)
    }

    @Test
    fun `anything that is not two numbers is not a place`() {
        assertNull(parseCoordinates("x", "", "-85.74"))
        assertNull(parseCoordinates("x", "north", "-85.74"))
        assertNull(parseCoordinates("x", "38.22", ""))
    }

    @Test
    fun `an out-of-range coordinate is refused rather than clamped`() {
        // Silently moving somebody's cabin to the nearest legal latitude is
        // worse than saying no.
        assertNull(parseCoordinates("x", "91.0", "-85.74"))
        assertNull(parseCoordinates("x", "-91.0", "-85.74"))
        assertNull(parseCoordinates("x", "38.22", "181.0"))
        assertNull(parseCoordinates("x", "38.22", "-181.0"))
        // The edges themselves are legal.
        assertEquals(90.0, parseCoordinates("x", "90", "180")?.latitude)
    }

    @Test
    fun `a non-finite coordinate is not a place`() {
        // NaN reaches PointCacheKey.of, where rounding it throws.
        assertNull(parseCoordinates("x", "NaN", "-85.74"))
        assertNull(parseCoordinates("x", "38.22", "Infinity"))
    }
}
