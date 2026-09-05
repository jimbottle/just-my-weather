package io.raylytics.justmyweather.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.packGrid
import io.raylytics.justmyweather.view.rowCount
import kotlin.math.roundToInt

/*
 * The lattice engine: the glance's grid of fixed cells. Every cell is the same
 * size, a tile is a whole number of cells each way, and the grid is as tall
 * as its lowest tile. This is what lets a tile be resized by dragging its
 * corner — the corner snaps to cells, and a tile two rows tall is a real
 * thing rather than "whatever height its row turned out to be".
 *
 * The packing is pure (view/packGrid) and this only turns placements into
 * pixels. Column width is the same arithmetic the flow grid uses — the width
 * less the gaps, divided by four — so a one-column tile here is exactly as
 * wide as a one-column forecast tile below it.
 */

/**
 * Lay [items] out on the lattice at their [size]s, in order.
 *
 * [tile] receives a modifier of its own to extend, as the flow grid's does;
 * the size is imposed by the measure pass rather than the modifier, so a tile
 * cannot opt out of its cells. Children may carry `Modifier.zIndex` — the
 * glance grid raises the tile it is dragging above its neighbours that way.
 */
@Composable
internal fun <T> CellGrid(
    items: List<T>,
    size: (T) -> ModuleSize,
    gap: Dp,
    /** Cell height as a share of cell width — see DensitySpec.cellAspect. */
    cellAspect: Float,
    modifier: Modifier = Modifier,
    tile: @Composable (item: T, index: Int, tileModifier: Modifier) -> Unit,
) {
    val placements = packGrid(items, size = size)
    Layout(
        content = { placements.forEachIndexed { index, placement -> tile(placement.item, index, Modifier) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val gapPx = gap.toPx()
        // The lattice fills the width it is given. An unbounded width has no
        // lattice to fill, so it degrades to the minimum rather than to
        // infinity — a grid can only be asked for that by a caller that has
        // not decided how wide it is, which is a bug worth seeing.
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val cellWidth = (width - gapPx * (ModuleSize.COLUMNS - 1)) / ModuleSize.COLUMNS
        val cellHeight = cellWidth * cellAspect

        // Edges are computed from the origin and rounded once each, so two
        // tiles that share a column boundary agree on it to the pixel and the
        // gap between them is the gap, not the gap plus a rounding error.
        fun left(column: Int) = (column * (cellWidth + gapPx)).roundToInt()

        fun right(column: Int) = ((column + 1) * (cellWidth + gapPx) - gapPx).roundToInt()

        fun top(row: Int) = (row * (cellHeight + gapPx)).roundToInt()

        fun bottom(row: Int) = ((row + 1) * (cellHeight + gapPx) - gapPx).roundToInt()

        val placeables =
            measurables.mapIndexed { index, measurable ->
                val p = placements[index]
                val w = right(p.column + p.size.columns - 1) - left(p.column)
                val h = bottom(p.row + p.size.rows - 1) - top(p.row)
                measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
            }
        val rows = placements.rowCount
        val height = if (rows == 0) 0 else bottom(rows - 1)
        layout(width, constraints.constrainHeight(height)) {
            placeables.forEachIndexed { index, placeable ->
                val p = placements[index]
                placeable.place(left(p.column), top(p.row))
            }
        }
    }
}
