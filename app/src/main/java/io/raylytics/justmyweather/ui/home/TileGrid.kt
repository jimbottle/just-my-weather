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
import io.raylytics.justmyweather.view.ModuleSpan
import io.raylytics.justmyweather.view.packGridRows

/*
 * The grid engine, shared by the two grids this screen has: the glance
 * (ModuleGrid — arrangeable) and the forecast (ForecastGrid — data-driven).
 * One engine is the point: a tile is a tile wherever it appears, so the screen
 * reads as one system rather than two things that happen to use rectangles,
 * and "how wide is a half tile" has exactly one answer.
 *
 * The engine deliberately owns layout only. Gestures, semantics and animation
 * belong to whoever is drawing — the glance grid is arrangeable and the
 * forecast grid is not, and pushing that difference down here would make this
 * file the union of both instead of the part they share.
 */

/** Corner radius of a tile's border. Rounded just enough to read as a tile,
 * not enough to read as a button. */
internal val TILE_CORNER = 10.dp

/** Air between a tile's border and its content. */
internal val TILE_PADDING = 10.dp

/** Floor for a tile's height, so a quarter tile with a short value is still a
 * comfortable touch target for the glance grid's long-press. */
internal val TILE_MIN_HEIGHT = 64.dp

/** Both grids stop growing here — on a tablet a quarter tile the width of a
 * phone screen stops being a tile. */
internal val GRID_MAX_WIDTH = 480.dp

/**
 * Pack [items] into rows of [ModuleSpan.COLUMNS] and draw them.
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
    span: (T) -> ModuleSpan,
    gap: Dp,
    modifier: Modifier = Modifier,
    /** Applied to the Row holding [row]'s tiles — the glance grid raises the
     * row it is dragging above its neighbours this way. */
    rowModifier: (row: List<T>) -> Modifier = { Modifier },
    tile: @Composable (item: T, index: Int, tileModifier: Modifier) -> Unit,
) {
    val rows = packGridRows(items) { span(it).columns }
    var index = 0
    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = modifier) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Equal-height tiles per row, sized by the tallest.
                        .height(IntrinsicSize.Min)
                        .then(rowModifier(row)),
            ) {
                row.forEach { item ->
                    tile(
                        item,
                        index++,
                        Modifier.weight(span(item).columns.toFloat()).fillMaxHeight(),
                    )
                }
                val leftover = (ModuleSpan.COLUMNS - row.sumOf { span(it).columns }).coerceAtLeast(0)
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
