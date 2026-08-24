package io.raylytics.justmyweather.view

/**
 * How wide a module sits on the glance's grid, out of [COLUMNS] columns.
 *
 * The catalog is deliberately three sizes, not four: a three-quarter width has
 * no content that wants it yet (see docs/modular-v2-evaluation.md, scope cuts).
 * Because 1, 2 and 4 all divide [COLUMNS], rows pack without awkward remainders.
 *
 * [key] is the stable persistence token — never rename it. [label] is the chip
 * text in the customize screen.
 */
enum class ModuleSpan(val key: String, val columns: Int, val label: String) {
    QUARTER("quarter", 1, "Quarter"),
    HALF("half", 2, "Half"),
    FULL("full", 4, "Full"),
    ;

    /** The next size around the ring — what a tap in arrange mode does. Cycling
     * beats a picker there: the tile itself previews the result instantly. */
    fun next(): ModuleSpan = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Width of the glance grid. Four matches the launcher grid the arrange
         * gesture is borrowed from, and every span divides it. */
        const val COLUMNS = 4

        fun byKey(key: String): ModuleSpan? = entries.firstOrNull { it.key == key }
    }
}
