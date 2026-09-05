package io.raylytics.justmyweather.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GridPackingTest {
    /** Shorthand: every reading module is `ModuleKey.Reading(field)`, and
     * spelling that out inline costs more width than it earns in clarity. */
    private fun reading(field: WeatherField) = ModuleKey.Reading(field)

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
    fun `the default config packs to hero, conditions, then the forecast, each on its own rows`() {
        val placed = packGrid(ViewConfig.DEFAULT.visible) { it.size }
        assertEquals(
            listOf(reading(WeatherField.TEMPERATURE), reading(WeatherField.CONDITIONS), ModuleKey.Forecast),
            placed.map { it.item.module },
        )
        // 4×2 hero on rows 0–1, the half-width conditions on row 2, the
        // full-width forecast under it from row 3.
        assertEquals(listOf(0 to 0, 0 to 2, 0 to 3), placed.map { it.column to it.row })
    }

    // ---- the lattice ----

    /** Each item is its size; the placements come back as (column, row). */
    private fun place(vararg sizes: ModuleSize): List<Pair<Int, Int>> =
        packGrid(sizes.toList()) { it }.map { it.column to it.row }

    private fun size(columns: Int, rows: Int) = ModuleSize(columns, rows)

    @Test
    fun `lattice items fill a row left to right, then start the next`() {
        val cell = size(1, 1)
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 0 to 1), place(cell, cell, cell, cell, cell))
        assertEquals(listOf(0 to 0, 2 to 0, 0 to 1), place(size(2, 1), size(2, 1), size(2, 1)))
    }

    @Test
    fun `a tall tile is packed beside, and later items sit in the cells it leaves`() {
        // A 2×2 at the origin; two 2×1 tiles stack in the columns beside it.
        assertEquals(listOf(0 to 0, 2 to 0, 2 to 1), place(size(2, 2), size(2, 1), size(2, 1)))
        // The hero: 4×2, then a half below it on row 2.
        assertEquals(listOf(0 to 0, 0 to 2), place(size(4, 2), size(2, 1)))
    }

    @Test
    fun `the cursor never goes backwards, so order is reading order`() {
        // 2×2, then a full-width tile that must drop to row 2, then a 1×1. The
        // 1×1 would FIT at (2, 0) — but that is before the full tile in
        // reading order, and the user put it after. It lands after instead,
        // and the two cells beside the 2×2 stay honestly empty.
        assertEquals(listOf(0 to 0, 0 to 2, 0 to 3), place(size(2, 2), size(4, 1), size(1, 1)))
    }

    @Test
    fun `a later item may still share the previous item's row`() {
        // Forward from the previous item's top-left is not "below it": a 3-wide
        // after a 1-wide takes the rest of that row.
        assertEquals(listOf(0 to 0, 1 to 0), place(size(1, 1), size(3, 1)))
        // And a 1×1 after a 1×2 sits beside it, on the same row.
        assertEquals(listOf(0 to 0, 1 to 0), place(size(1, 2), size(1, 1)))
    }

    @Test
    fun `the lattice is as tall as its lowest tile`() {
        assertEquals(0, packGrid(emptyList<ModuleSize>()) { it }.rowCount)
        assertEquals(2, packGrid(listOf(size(2, 2), size(2, 1))) { it }.rowCount)
        assertEquals(3, packGrid(listOf(size(4, 2), size(1, 1))) { it }.rowCount)
    }

    @Test
    fun `degenerate lattice sizes degrade instead of crashing`() {
        // Wider than the grid is a full row; zero or negative is one cell.
        val placed = packGrid(listOf(size(9, 1), size(0, 0))) { it }
        assertEquals(listOf(size(4, 1), size(1, 1)), placed.map { it.size })
        assertEquals(listOf(0 to 0, 0 to 1), placed.map { it.column to it.row })
    }

    @Test
    fun `degenerate spans degrade instead of crashing`() {
        // Wider than the grid clamps to a full row; zero or negative acts as 1.
        assertEquals(listOf(listOf(9)), pack(9))
        assertEquals(listOf(listOf(0, 1, 1, 1), listOf(1)), pack(0, 1, 1, 1, 1))
        assertEquals(emptyList<List<Int>>(), pack())
    }
}
