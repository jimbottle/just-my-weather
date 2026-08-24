package io.raylytics.justmyweather.view

/*
 * The flow-grid's one piece of geometry: order + span in, rows out. Pure so the
 * layout the user sees is decided (and tested) on the JVM; the composable that
 * draws it only walks the result.
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
    columns: Int = ModuleSpan.COLUMNS,
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
