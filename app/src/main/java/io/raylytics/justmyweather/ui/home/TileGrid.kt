package io.raylytics.justmyweather.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.packGridRows

/*
 * The flow-grid engine and the tile shell. The forecast grid (ForecastGrid —
 * data-driven) packs its tiles here in rows as tall as their content; the
 * glance (ModuleGrid — arrangeable) packs onto the fixed lattice in
 * CellGrid.kt, because its tiles have a height in cells as well as a width.
 * Both divide the same width into the same four columns and draw the same
 * TileShell, so a tile is a tile wherever it appears: the screen reads as one
 * system rather than two things that happen to use rectangles, and "how wide
 * is a two-column tile" has exactly one answer.
 *
 * The engines deliberately own layout only. Gestures, semantics and animation
 * belong to whoever is drawing — the glance grid is arrangeable and the
 * forecast grid is not, and pushing that difference down here would make this
 * file the union of both instead of the part they share.
 */

/** Corner radius of a tile's border. Rounded just enough to read as a tile,
 * not enough to read as a button. */
internal val TILE_CORNER = 10.dp

/** Air between a tile's border and its content. */
internal val TILE_PADDING = 10.dp

/** Floor for a flow-grid tile's height, so a one-column tile with a short
 * value is still a comfortable touch target. (A lattice tile's height is fixed
 * by its cells and this floor yields to it.) */
internal val TILE_MIN_HEIGHT = 64.dp

/** Both grids stop growing here — on a tablet a one-column tile the width of
 * a phone screen stops being a tile. */
internal val GRID_MAX_WIDTH = 480.dp

/**
 * Pack [items] into rows of [gridColumns] and draw them.
 *
 * The packing itself is pure and lives in `view/packGridRows`; this only turns
 * its rows into Compose. A row's leftover columns become a [Spacer] rather than
 * being absorbed by the tiles: an unfilled quarter is honest grid space, and
 * stretching to hide it is exactly what would stop the grid reading as a grid.
 *
 * [tile] receives a modifier already carrying the item's width and the row's
 * height, so callers add their own concerns to it without having to know the
 * column arithmetic.
 */
@Composable
internal fun <T> TileGrid(
    items: List<T>,
    /** How many columns an item spans. */
    columns: (T) -> Int,
    gap: Dp,
    modifier: Modifier = Modifier,
    /** How many columns the grid has. Four across the page; inside a
     * forecast module, the module's own width in cells — so a one-column
     * item here is exactly one lattice cell wide, wherever the grid sits. */
    gridColumns: Int = ModuleSize.COLUMNS,
    tile: @Composable (item: T, index: Int, tileModifier: Modifier) -> Unit,
) {
    val rows = packGridRows(items, gridColumns, columns)
    var index = 0
    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = modifier) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Equal-height tiles per row, sized by the tallest.
                        .height(IntrinsicSize.Min),
            ) {
                row.forEach { item ->
                    tile(
                        item,
                        index++,
                        Modifier.weight(columns(item).toFloat()).fillMaxHeight(),
                    )
                }
                val leftover = (gridColumns - row.sumOf { columns(it) }).coerceAtLeast(0)
                if (leftover > 0) Spacer(Modifier.weight(leftover.toFloat()))
            }
        }
    }
}

/**
 * The tile itself: a thin border, rounded, with its content centred.
 *
 * The border is always on, in both grids. That was the explicit ask — a tile's
 * grid footprint should be legible outside the editor, not only while
 * arranging — and it is what lets a forecast tile and a glance module read as
 * the same kind of object.
 */
@Composable
internal fun TileShell(
    borderColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = TILE_MIN_HEIGHT)
                .border(1.dp, borderColor, RoundedCornerShape(TILE_CORNER))
                .padding(TILE_PADDING),
        content = content,
    )
}
