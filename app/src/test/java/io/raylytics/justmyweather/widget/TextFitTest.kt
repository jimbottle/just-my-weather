package io.raylytics.justmyweather.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextFitTest {
    /** [TextFit.sp] with the glance's hero ceiling and value floor. */
    private fun fit(text: String, widthDp: Float, heightDp: Float, maxLines: Int = 1): Float =
        TextFit.sp(text, widthDp = widthDp, heightDp = heightDp, ceilingSp = 120f, floorSp = 14f, maxLines = maxLines)

    @Test
    fun `a short number in a big box draws at the ceiling`() {
        assertEquals(120f, TextFit.sp("72°", widthDp = 300f, heightDp = 200f, ceilingSp = 120f, floorSp = 14f))
    }

    @Test
    fun `a short box bounds the size by height`() {
        val size = TextFit.sp("72°", widthDp = 300f, heightDp = 40f, ceilingSp = 120f, floorSp = 14f)
        assertTrue(size * TextFit.LINE_HEIGHT <= 40f, "$size sp overflows 40dp")
        assertTrue(size >= 30f, "$size sp is needlessly small for 40dp")
    }

    @Test
    fun `a narrow box bounds the size by width`() {
        val size = TextFit.sp("30.12 inHg", widthDp = 100f, heightDp = 200f, ceilingSp = 120f, floorSp = 14f)
        assertTrue("30.12 inHg".length * size * TextFit.GLYPH_WIDTH <= 100f, "$size sp overflows 100dp")
        assertTrue(size < 40f)
    }

    @Test
    fun `nothing fits - the floor, and the launcher ellipsises`() {
        val cramped = fit("Chance Showers And Thunderstorms", widthDp = 30f, heightDp = 20f)
        assertEquals(14f, cramped)
        assertEquals(14f, TextFit.sp("", widthDp = 300f, heightDp = 200f, ceilingSp = 120f, floorSp = 14f))
    }

    @Test
    fun `a phrase may wrap onto the lines it is allowed, and grows for it`() {
        val oneLine = fit("Partly Cloudy", widthDp = 120f, heightDp = 100f, maxLines = 1)
        val twoLines = fit("Partly Cloudy", widthDp = 120f, heightDp = 100f, maxLines = 2)
        assertTrue(twoLines > oneLine, "two lines ($twoLines) should allow a larger size than one ($oneLine)")
    }

    @Test
    fun `a word is never broken across lines`() {
        // "Thunderstorms" alone must fit a line at the chosen size, even
        // though the two words together would fit a line at a larger one.
        val size = fit("Chance Thunderstorms", widthDp = 160f, heightDp = 200f, maxLines = 3)
        assertTrue(size > 14f, "$size sp: a 160dp box has room above the floor")
        assertTrue("Thunderstorms".length * size * TextFit.GLYPH_WIDTH <= 160f, "$size sp breaks the long word")
    }
}
