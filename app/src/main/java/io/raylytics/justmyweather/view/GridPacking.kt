package io.raylytics.justmyweather.view

/*
 * The grids' geometry: order + size in, positions out. Pure so the layout the
 * user sees is decided (and tested) on the JVM; the composables that draw it
 * only walk the result.
 *
 * Two packers because the screen has two kinds of grid. [packGridRows] is the
 * flow grid the forecast uses: tiles have a width and take whatever height
 * their row needs. [packGrid] is the glance's lattice: tiles have a width AND
 * a height in cells, so a hero can be two rows tall beside a column of
 * single-cell readings.
 */

/**
 * Pack items into grid rows of [columns], preserving order.
 *
 * Greedy flow, the same rule text uses to fill lines: an item joins the current
 * row if its span still fits, otherwise it starts the next row. No item is ever
 * reordered to fill a gap — the user's order is the user's order, and a
 * trailing gap in a row is honest grid space, shown as such.
 *
 * A span wider than [columns] is treated as a full row rather than rejected:
 * config is user data, and geometry must degrade, not crash.
 */
fun <T> packGridRows(
    items: List<T>,
    columns: Int = ModuleSize.COLUMNS,
    span: (T) -> Int,
): List<List<T>> {
    val rows = mutableListOf<MutableList<T>>()
    var used = columns // "full" so the first item always opens a row
    items.forEach { item ->
        val width = span(item).coerceIn(1, columns)
        if (used + width > columns) {
            rows.add(mutableListOf())
            used = 0
        }
        rows.last().add(item)
        used += width
    }
    return rows
}

/** Where one item landed on the lattice: its top-left cell and its footprint. */
data class GridPlacement<T>(
    val item: T,
    val column: Int,
    val row: Int,
    val size: ModuleSize,
)

/**
 * Pack items onto a lattice of [columns] columns and as many rows as they need,
 * preserving order.
 *
 * Each item takes the first free rectangle of its size scanning left to right,
 * top to bottom — but never from before the previous item's top-left cell. The
 * cursor only moves forward, which is what keeps the user's order the reading
 * order: a small tile placed after a tall one may sit beside it, never above
 * it. A hole that rule leaves (a 3-wide tile after a 2-wide one starts a new
 * row and leaves two cells empty) is honest grid space, exactly as a row's
 * trailing gap is in the flow grid; the user closes it by reordering, and the
 * grid never does it for them.
 *
 * Degenerate sizes degrade rather than fail: wider than the grid is a full
 * row, zero or negative is one cell. Config is user data.
 */
fun <T> packGrid(
    items: List<T>,
    columns: Int = ModuleSize.COLUMNS,
    size: (T) -> ModuleSize,
): List<GridPlacement<T>> {
    val occupied = HashSet<Pair<Int, Int>>() // (row, column)

    fun free(row: Int, column: Int, width: Int, height: Int): Boolean {
        for (r in row until row + height) {
            for (c in column until column + width) if ((r to c) in occupied) return false
        }
        return true
    }
    val placements = ArrayList<GridPlacement<T>>(items.size)
    var cursorRow = 0
    var cursorColumn = 0
    items.forEach { item ->
        val width = size(item).columns.coerceIn(1, columns)
        val height = size(item).rows.coerceAtLeast(1)
        var row = cursorRow
        var column = cursorColumn
        while (true) {
            if (column + width > columns) {
                row++
                column = 0
                continue
            }
            if (free(row, column, width, height)) break
            column++
        }
        for (r in row until row + height) for (c in column until column + width) occupied += r to c
        placements += GridPlacement(item, column, row, ModuleSize(width, height))
        cursorRow = row
        cursorColumn = column
    }
    return placements
}

/** How many rows a packing occupies — the lattice's height. */
val List<GridPlacement<*>>.rowCount: Int
    get() = maxOfOrNull { it.row + it.size.rows } ?: 0
