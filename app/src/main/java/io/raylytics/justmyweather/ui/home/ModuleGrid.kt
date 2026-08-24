package io.raylytics.justmyweather.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.raylytics.justmyweather.view.ModuleSpan
import io.raylytics.justmyweather.view.ModuleValue
import io.raylytics.justmyweather.view.WeatherField
import io.raylytics.justmyweather.view.packGridRows

/*
 * The modular glance: every visible field as a bordered tile on a 4-column
 * flow grid, plus the launcher-style gestures that rearrange it. All the
 * gesture and animation machinery is deliberately confined to this file — the
 * geometry it acts on (spans, row packing, reorder transforms) is pure Kotlin
 * in view/, and the rest of the app only ever sees a ViewConfig change.
 * docs/modular-v2-evaluation.md holds the criteria this design is judged by.
 */

/** Corner radius of a tile's border. Rounded just enough to read as a tile,
 * not enough to read as a button. */
private val TILE_CORNER = 10.dp

/** Air between a tile's border and its content. */
private val TILE_PADDING = 10.dp

/** Floor for a tile's height, so a quarter tile with a short value is still a
 * comfortable touch target for the long-press. */
private val TILE_MIN_HEIGHT = 64.dp

/** The grid stops growing here — on a tablet a quarter tile the width of a
 * phone screen stops being a tile. */
private val GRID_MAX_WIDTH = 480.dp

/** Wiggle amplitude, degrees. Visible without being carnival. */
private const val WIGGLE_DEGREES = 1.6f

/** One half-cycle of the wiggle. */
private const val WIGGLE_PERIOD_MS = 160

/** The dragged tile lifts slightly, the way a launcher icon does. */
private const val DRAG_SCALE = 1.04f

/**
 * The grid itself. Tiles flow in the user's order at their configured spans;
 * a trailing gap in a row is honest grid space and stays empty. Outside
 * arrange mode this composable is inert — the borders are the only sign the
 * grid exists.
 *
 * Arrange mode is the launcher's grammar: long-press any tile to enter (tiles
 * wiggle, borders take the accent), and the same hold flows straight into a
 * drag. While arranging, tap a tile to cycle its width and long-press-drag to
 * reorder. Every edit lands as a
 * [ViewConfig][io.raylytics.justmyweather.view.ViewConfig] transform through
 * [onMove]/[onCycleSpan], so a drag persists like any other customization —
 * there is no separate "editing copy" to commit or lose.
 *
 * All pointer detection lives on the GRID, not the tiles, with tiles found by
 * hit-testing the bounds they report. That is a correctness requirement, not a
 * style choice: a reorder re-packs the rows, which re-targets the (unkeyed)
 * tile composables, and a gesture coroutine keyed to a tile dies mid-drag when
 * its tile changes row — the drop event then never arrives, and the dragged
 * tile freezes mid-air (verified on-device via the drag lifecycle logs). The
 * grid node never moves, so a gesture that starts on it always sees its end.
 */
@Composable
internal fun ModuleGrid(
    modules: List<ModuleValue>,
    arranging: Boolean,
    spec: DensitySpec,
    onStartArranging: () -> Unit,
    onCycleSpan: (WeatherField) -> Unit,
    /** Move a field so it lands at this index among the visible modules. */
    onMove: (WeatherField, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    // The gesture coroutines below live in pointerInput(Unit) — never
    // restarted, so they cannot lose a gesture — which means everything they
    // touch must be readable through state, never captured parameter values.
    val currentModules by rememberUpdatedState(modules)
    val isArranging by rememberUpdatedState(arranging)
    val startArranging by rememberUpdatedState(onStartArranging)
    val cycleSpan by rememberUpdatedState(onCycleSpan)
    val move by rememberUpdatedState(onMove)

    // Drag state. Positions are all in window-root coordinates — one shared
    // frame for the pointer and every tile's bounds, so nothing needs to know
    // which row anything is in. `bounds` is refreshed by layout after every
    // reorder, which is what keeps the dragged tile anchored under the finger
    // when its slot (and therefore its layout position) changes mid-drag.
    var dragged by remember { mutableStateOf<WeatherField?>(null) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableStateMapOf<WeatherField, Rect>() }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // The hold that triggers the drag detector's long-press ALSO looks like a
    // tap to the tap detector once the finger lifts (a hold-then-release IS a
    // tap to detectTapGestures unless it consumes-until-up — and consuming is
    // not an option, because that eats the drag's move events; both failure
    // modes were hit on-device). The drag detector flags its long-press here;
    // the tap handler, which runs first on the shared release, skips one tap.
    var suppressTap by remember { mutableStateOf(false) }
    // The move already requested but not yet reflected in the modules list —
    // the save round-trips through DataStore, and re-requesting the same move
    // on every drag event in that window would thrash.
    var pendingTarget by remember { mutableStateOf<Int?>(null) }

    // Gesture positions arrive grid-local; tiles report window-root bounds.
    fun toRoot(local: Offset): Offset = gridCoords?.localToRoot(local) ?: local

    fun tileAt(rootPos: Offset): WeatherField? =
        currentModules.firstOrNull { bounds[it.field]?.contains(rootPos) == true }?.field

    fun beginDrag(field: WeatherField, rootPos: Offset) {
        dragged = field
        dragPosition = rootPos
        grabOffset = rootPos - (bounds[field]?.topLeft ?: rootPos)
        pendingTarget = null
    }

    fun endDrag() {
        dragged = null
        pendingTarget = null
    }

    // Nearest-center wins, not rect containment: once a tile moves under the
    // pointer, ITS center is the nearest, so the arrangement is stable by
    // construction — rect hit-testing oscillates when tiles of different spans
    // land where the pointer already is.
    fun settleDrag() {
        val field = dragged ?: return
        val current = currentModules.indexOfFirst { it.field == field }
        if (current == -1) return
        val target =
            currentModules.indices.minByOrNull { i ->
                val center = bounds[currentModules[i].field]?.center ?: return@minByOrNull Float.MAX_VALUE
                (center - dragPosition).getDistanceSquared()
            } ?: return
        if (target != current && target != pendingTarget) {
            pendingTarget = target
            move(field, target)
        }
    }

    val rows = packGridRows(modules) { it.span.columns }
    var moduleIndex = 0
    Column(
        verticalArrangement = Arrangement.spacedBy(spec.moduleGap),
        modifier =
            modifier
                .widthIn(max = GRID_MAX_WIDTH)
                .onGloballyPositioned { gridCoords = it }
                .pointerInput(Unit) {
                    // One detector for both modes: outside arrange mode the
                    // long-press starts the wiggle AND flows straight into the
                    // drag without lifting — the launcher gesture in full.
                    detectDragGesturesAfterLongPress(
                        onDragStart = { local ->
                            suppressTap = true
                            val rootPos = toRoot(local)
                            val field = tileAt(rootPos) ?: return@detectDragGesturesAfterLongPress
                            if (!isArranging) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                startArranging()
                            }
                            beginDrag(field, rootPos)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragPosition += amount
                            settleDrag()
                        },
                        onDragEnd = {
                            endDrag()
                            suppressTap = false
                        },
                        onDragCancel = {
                            endDrag()
                            suppressTap = false
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { local ->
                            if (suppressTap) return@detectTapGestures
                            if (isArranging) tileAt(toRoot(local))?.let { cycleSpan(it) }
                        },
                    )
                },
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spec.moduleGap),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Equal-height tiles per row, sized by the tallest.
                        .height(IntrinsicSize.Min)
                        // The dragged tile's row draws over its neighbours, or
                        // the tile slides UNDER the next row on a long drag.
                        .zIndex(if (row.any { it.field == dragged }) 1f else 0f),
            ) {
                row.forEach { module ->
                    val index = moduleIndex++
                    val field = module.field
                    val isDragged = dragged == field
                    val wiggle = wiggleAngle(active = arranging && !isDragged, phase = index)
                    ModuleTile(
                        module = module,
                        arranging = arranging,
                        heroStyle = spec.heroStyle,
                        modifier =
                            Modifier
                                .weight(module.span.columns.toFloat())
                                .fillMaxHeight()
                                .zIndex(if (isDragged) 1f else 0f)
                                .onGloballyPositioned { bounds[field] = it.boundsInRoot() }
                                .graphicsLayer {
                                    // Every property is written on every pass,
                                    // both branches: the layer keeps its last
                                    // values between invocations, so a branch
                                    // that "doesn't touch" translation would
                                    // freeze the drag's offset onto the tile.
                                    if (isDragged) {
                                        // Anchor the grab point under the finger,
                                        // wherever layout put the tile this frame.
                                        val base = bounds[field]?.topLeft ?: (dragPosition - grabOffset)
                                        val shift = dragPosition - grabOffset - base
                                        translationX = shift.x
                                        translationY = shift.y
                                        scaleX = DRAG_SCALE
                                        scaleY = DRAG_SCALE
                                        rotationZ = 0f
                                    } else {
                                        translationX = 0f
                                        translationY = 0f
                                        scaleX = 1f
                                        scaleY = 1f
                                        rotationZ = wiggle.value
                                    }
                                }
                                .testTag("module_${field.key}"),
                    )
                }
                val leftover = (ModuleSpan.COLUMNS - row.sumOf { it.span.columns }).coerceAtLeast(0)
                if (leftover > 0) Spacer(Modifier.weight(leftover.toFloat()))
            }
        }
    }
}

/**
 * One tile: the always-on border (the user asked for the grid footprint to be
 * legible outside the editor — this thin line is that), a quiet label, and the
 * value at a size that follows the tile's width. Width IS prominence: a full
 * tile shows its value near hero size and drops the label (a full-width value
 * speaks for itself, as the old hero did), narrower tiles caption themselves.
 */
@Composable
private fun ModuleTile(
    module: ModuleValue,
    arranging: Boolean,
    heroStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val borderColor =
        if (arranging) {
            // The accent marks "these are editable now" — the one moment the
            // border is allowed to speak up.
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val valueStyle =
        when (module.span) {
            ModuleSpan.FULL -> heroStyle
            ModuleSpan.HALF -> MaterialTheme.typography.headlineMedium
            ModuleSpan.QUARTER -> MaterialTheme.typography.titleLarge
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .heightIn(min = TILE_MIN_HEIGHT)
                .border(1.dp, borderColor, RoundedCornerShape(TILE_CORNER))
                .padding(TILE_PADDING),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (module.span != ModuleSpan.FULL) {
                Text(
                    text = module.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = module.value,
                style = valueStyle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The wiggle, per tile. Returns [State] rather than a value so only the
 * graphics layer reads it — the animation then invalidates the layer each
 * frame, never recomposition. [phase] staggers neighbours (the prime keeps the
 * offsets from lining up), because tiles wiggling in lockstep read as one
 * shivering screen instead of many loose tiles.
 */
@Composable
private fun wiggleAngle(active: Boolean, phase: Int): State<Float> {
    if (!active) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "wiggle")
    return transition.animateFloat(
        initialValue = -WIGGLE_DEGREES,
        targetValue = WIGGLE_DEGREES,
        animationSpec =
            infiniteRepeatable(
                animation = tween(WIGGLE_PERIOD_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset((phase * 37) % WIGGLE_PERIOD_MS),
            ),
        label = "wiggleAngle",
    )
}
