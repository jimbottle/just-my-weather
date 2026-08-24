package io.raylytics.justmyweather.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridPackingTest {
    private fun pack(vararg spans: Int): List<List<Int>> = packGridRows(spans.toList()) { it }

    @Test
    fun `items flow into a row until it is full, then wrap`() {
        assertEquals(listOf(listOf(1, 1, 1, 1), listOf(1)), pack(1, 1, 1, 1, 1))
        assertEquals(listOf(listOf(2, 2), listOf(2)), pack(2, 2, 2))
        assertEquals(listOf(listOf(4), listOf(4)), pack(4, 4))
    }

    @Test
    fun `a row wraps early rather than reordering to fill its gap`() {
        // The 2 after 1+2 would fit a later slot, but the user's order wins:
        // 1,2 leaves one column, the next 2 can't have it, new row.
        assertEquals(listOf(listOf(1, 2), listOf(2, 1)), pack(1, 2, 2, 1))
        // The default glance: a full hero, then a half — the half's trailing
        // gap is honest grid space, not something to backfill.
        assertEquals(listOf(listOf(4), listOf(2)), pack(4, 2))
    }

    @Test
    fun `the default config packs to hero row then conditions row`() {
        val rows = packGridRows(ViewConfig.DEFAULT.visible) { it.span.columns }
        assertEquals(
            listOf(listOf(WeatherField.TEMPERATURE), listOf(WeatherField.CONDITIONS)),
            rows.map { row -> row.map { it.field } },
        )
    }

    @Test
    fun `degenerate spans degrade instead of crashing`() {
        // Wider than the grid clamps to a full row; zero or negative acts as 1.
        assertEquals(listOf(listOf(9)), pack(9))
        assertEquals(listOf(listOf(0, 1, 1, 1), listOf(1)), pack(0, 1, 1, 1, 1))
        assertEquals(emptyList<List<Int>>(), pack())
    }
}
