package io.raylytics.justmyweather.view

/**
 * How much of the glance grid a module occupies: [columns] of [COLUMNS]
 * across and [rows] cells down. The grid is a fixed lattice — every cell is
 * the same size — so a tile's footprint is a whole number of cells in each
 * direction, and its prominence IS that footprint: a 4×2 temperature is the
 * hero, the same reading at 1×1 is a small number in a corner.
 *
 * Any size in the lattice is legal. The old three-width catalog (quarter,
 * half, full) went when resizing became a corner drag: a handle that snaps to
 * cells has no reason to skip three-quarters, and stepping through a ring of
 * fixed sizes on a tap is a slot machine once there are more than three.
 *
 * This is a plain value. The floor a particular module may shrink to is the
 * module's own ([ModuleKey.minSize] — a number fits a single cell, a phrase or
 * a table does not), so clamping happens where the module is known:
 * `ViewConfig.resize` and the codec. Steps here are unclamped for the same
 * reason; callers ask [fits] before offering one.
 */
data class ModuleSize(val columns: Int, val rows: Int) {
    fun wider(): ModuleSize = copy(columns = columns + 1)

    fun narrower(): ModuleSize = copy(columns = columns - 1)

    fun taller(): ModuleSize = copy(rows = rows + 1)

    fun shorter(): ModuleSize = copy(rows = rows - 1)

    /** Whether this size lies within the lattice and at or above [min]. */
    fun fits(min: ModuleSize): Boolean =
        columns in min.columns..COLUMNS && rows in min.rows..MAX_ROWS

    /** The nearest legal size: no smaller than [min], no larger than the
     * lattice. Config is user data, so an out-of-range size degrades to a
     * legal one rather than being rejected. */
    fun clamp(min: ModuleSize): ModuleSize =
        ModuleSize(
            columns = columns.coerceIn(min.columns.coerceIn(1, COLUMNS), COLUMNS),
            rows = rows.coerceIn(min.rows.coerceIn(1, MAX_ROWS), MAX_ROWS),
        )

    /** How a screen reader hears it: the two counts, in words a user can act
     * on with the Wider/Taller actions. */
    val label: String get() = "$columns wide, $rows tall"

    companion object {
        /** Width of the glance grid. Four matches the launcher grid the arrange
         * gesture is borrowed from. */
        const val COLUMNS = 4

        /** Tallest a tile may be. Four cells is what the forecast wants for
         * two rows of hours under its header; beyond that a tile stops being
         * a tile and starts being the screen. */
        const val MAX_ROWS = 4

        /** A single cell — the floor for a short reading. */
        val CELL = ModuleSize(1, 1)
    }
}
