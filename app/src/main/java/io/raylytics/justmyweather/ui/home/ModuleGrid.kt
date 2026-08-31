package io.raylytics.justmyweather.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import io.raylytics.justmyweather.view.ModuleContent
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ModuleSpan
import io.raylytics.justmyweather.view.ModuleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * The modular glance: every visible field as a bordered tile on a 4-column
 * flow grid, plus the launcher-style gestures that rearrange it. All the
 * gesture and animation machinery is deliberately confined to this file — the
 * geometry it acts on (spans, row packing, reorder transforms) is pure Kotlin
 * in view/, and the rest of the app only ever sees a ViewConfig change.
 * docs/modular-v2-evaluation.md holds the criteria this design is judged by.
 */

/** Wiggle amplitude, degrees. Visible without being carnival. */
private const val WIGGLE_DEGREES = 1.6f

/** One half-cycle of the wiggle. */
private const val WIGGLE_PERIOD_MS = 160

/**
 * Which gesture detector is driving a drag.
 *
 * Both are live while arranging — the hold that enters the mode and carries
 * straight on into a drag, and the plain drag that works once the tiles are
 * already wiggling — and a slow press that then moves can look like the start
 * of either. Without an owner they would both add to the drag position and the
 * tile would travel at double speed.
 */
private enum class DragOwner { LONG_PRESS, IMMEDIATE }

/** The dragged tile lifts slightly, the way a launcher icon does. */
private const val DRAG_SCALE = 1.04f

/**
 * How a tile lifts, settles and slides: a firm spring with no bounce. Quick
 * enough that a drop reads as the tile landing, not as an animation being
 * played at you; no overshoot because the whole screen is built to be calm.
 */
private fun <T> settleSpring() =
    spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Where a tile is drawn relative to where layout put it, and how big. One per
 * module, held by the grid rather than the tile: a reorder re-packs the rows
 * and re-targets the (unkeyed) tile composables, so state a tile remembered
 * for itself would follow the slot, not the module.
 *
 * Both values are Animatables read only inside the graphics layer, so every
 * frame of a lift, a settle or a slide invalidates the layer and nothing else.
 */
private class TileMotion {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    val scale = Animatable(1f)
}

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
 *
 * Motion follows the same rule. A tile that changes slot — because a drag
 * moved it, its neighbour, or an accessibility action did — slides from where
 * it was to where it now is, and a dropped tile springs into its slot rather
 * than snapping. Both are per-module Animatables held HERE (see [TileMotion]),
 * driven from the tile's layout callback, and applied in its graphics layer,
 * so the reflow is legible without a single tile recomposing for it.
 */
@Composable
internal fun ModuleGrid(
    modules: List<ModuleValue>,
    arranging: Boolean,
    spec: DensitySpec,
    onStartArranging: () -> Unit,
    onCycleSpan: (ModuleKey) -> Unit,
    /** Move a module so it lands at this index among the visible ones. */
    onMove: (ModuleKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

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
    var dragged by remember { mutableStateOf<ModuleKey?>(null) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableStateMapOf<ModuleKey, Rect>() }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Where each tile last sat, in the GRID's frame rather than the window's:
    // the page scrolls, and a scroll moves every tile in root coordinates
    // without moving any of them on the grid. Comparing slots here is what
    // lets a layout callback tell "the order changed" from "the page moved".
    val slots = remember { mutableMapOf<ModuleKey, Offset>() }
    val motions = remember { mutableMapOf<ModuleKey, TileMotion>() }

    fun motionOf(field: ModuleKey) = motions.getOrPut(field) { TileMotion() }
    // A module that leaves the grid (hidden on the customize screen) and comes
    // back should appear in place, not fly in from wherever it last was.
    SideEffect {
        val visible = modules.mapTo(HashSet()) { it.module }
        slots.keys.retainAll(visible)
    }
    // The hold that triggers the drag detector's long-press ALSO looks like a
    // tap to the tap detector once the finger lifts (a hold-then-release IS a
    // tap to detectTapGestures unless it consumes-until-up — and consuming is
    // not an option, because that eats the drag's move events; both failure
    // modes were hit on-device). The drag detector flags its long-press here;
    // the tap handler, which runs first on the shared release, skips one tap.
    var suppressTap by remember { mutableStateOf(false) }
    // Which detector is driving the current drag. While arranging BOTH are
    // live — the launcher gesture and the plain one — and a slow press that
    // then moves can look like the start of either. Without an owner they
    // would both add to dragPosition and the tile would travel at double
    // speed; with one, the second detector sees a drag already in progress
    // and keeps out of it.
    var dragOwner by remember { mutableStateOf<DragOwner?>(null) }
    // The move already requested but not yet reflected in the modules list —
    // the save round-trips through DataStore, and re-requesting the same move
    // on every drag event in that window would thrash.
    var pendingTarget by remember { mutableStateOf<Int?>(null) }
    // Counts drags, so a drop's deferred clean-up can tell whether the drag it
    // belongs to is still the current one.
    var dragSerial by remember { mutableStateOf(0) }

    // Gesture positions arrive grid-local; tiles report window-root bounds.
    fun toRoot(local: Offset): Offset = gridCoords?.localToRoot(local) ?: local

    fun tileAt(rootPos: Offset): ModuleKey? =
        currentModules.firstOrNull { bounds[it.module]?.contains(rootPos) == true }?.module

    /** Claim the drag for [owner], or return false if someone else has it. */
    fun beginDrag(owner: DragOwner, field: ModuleKey, rootPos: Offset): Boolean {
        if (dragOwner != null) return false
        dragSerial++
        dragOwner = owner
        dragged = field
        dragPosition = rootPos
        grabOffset = rootPos - (bounds[field]?.topLeft ?: rootPos)
        pendingTarget = null
        // The lift. Any slide still in flight is abandoned: the drag positions
        // the tile absolutely from here on.
        val motion = motionOf(field)
        scope.launch {
            motion.offset.snapTo(Offset.Zero)
            motion.scale.animateTo(DRAG_SCALE, settleSpring())
        }
        return true
    }

    fun endDrag(owner: DragOwner) {
        if (dragOwner != owner) return
        val field = dragged
        dragOwner = null
        pendingTarget = null
        suppressTap = false
        if (field == null) return
        // The drop. The tile is wherever the finger left it and layout has its
        // slot; spring the difference to zero and the scale back to rest,
        // together, so it lands rather than appears. `dragged` is cleared
        // INSIDE the coroutine, after the offset is snapped: until then the
        // tile is still drawn at its drag position, and clearing it a frame
        // early would flash the tile in its slot before it flew back out to
        // land there. The serial guards the hand-off — if another drag has
        // begun by the time this runs, the tile is that drag's to place.
        val serial = dragSerial
        scope.launch {
            if (dragSerial != serial) return@launch
            val motion = motionOf(field)
            bounds[field]?.topLeft?.let { rest -> motion.offset.snapTo(dragPosition - grabOffset - rest) }
            dragged = null
            coroutineScope {
                launch { motion.scale.animateTo(1f, settleSpring()) }
                launch { motion.offset.animateTo(Offset.Zero, settleSpring()) }
            }
        }
    }

    // Nearest-center wins, not rect containment: once a tile moves under the
    // pointer, ITS center is the nearest, so the arrangement is stable by
    // construction — rect hit-testing oscillates when tiles of different spans
    // land where the pointer already is.
    fun settleDrag() {
        val field = dragged ?: return
        val current = currentModules.indexOfFirst { it.module == field }
        if (current == -1) return
        val target =
            currentModules.indices.minByOrNull { i ->
                val center = bounds[currentModules[i].module]?.center ?: return@minByOrNull Float.MAX_VALUE
                (center - dragPosition).getDistanceSquared()
            } ?: return
        if (target != current && target != pendingTarget) {
            pendingTarget = target
            // A tick per slot, the launcher's way of letting the thumb feel
            // the grid it cannot see under itself.
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            move(field, target)
        }
    }

    TileGrid(
        items = modules,
        span = { it.span },
        gap = spec.moduleGap,
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
                            beginDrag(DragOwner.LONG_PRESS, field, rootPos)
                        },
                        onDrag = { change, amount ->
                            if (dragOwner != DragOwner.LONG_PRESS) return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragPosition += amount
                            settleDrag()
                        },
                        onDragEnd = { endDrag(DragOwner.LONG_PRESS) },
                        onDragCancel = { endDrag(DragOwner.LONG_PRESS) },
                    )
                }
                // Once the tiles are wiggling, a plain drag moves one — no
                // second long-press. That is what a launcher does, and holding
                // again for every tile you want to nudge is the kind of
                // friction people read as the gesture not having worked.
                //
                // Keyed on `arranging` so the detector does not EXIST outside
                // arrange mode: an always-on drag detector over the grid would
                // swallow the glance's vertical scroll. Keying it is safe here
                // in a way it is not for the block above — that one is running
                // the very gesture that flips `arranging`, and restarting it
                // mid-hold is what once killed the drag and froze a tile
                // mid-air. This one is only ever created BEFORE its gesture
                // starts.
                .pointerInput(arranging) {
                    if (!arranging) return@pointerInput
                    // The `finally` is load-bearing. Keying this detector on
                    // `arranging` means Compose CANCELS it the moment arrange
                    // mode ends — and that can happen with a finger still
                    // down, through a channel this detector never sees: system
                    // Back is wired to leave arrange mode, and a second finger
                    // can reach "Done arranging". A cancelled coroutine
                    // unwinds through its suspension point; it does NOT run
                    // onDragEnd or onDragCancel. Without cleanup here the
                    // drag state would simply be abandoned mid-flight: the
                    // tile stranded at its drag offset and scale with nothing
                    // left to clear it, and dragOwner still claimed, so
                    // beginDrag would refuse every later drag for the life of
                    // the process. That is the same "frozen tile" failure the
                    // long-press detector is kept on pointerInput(Unit) to
                    // avoid, arriving by a different road.
                    try {
                        detectDragGestures(
                            onDragStart = { local ->
                                val rootPos = toRoot(local)
                                val field = tileAt(rootPos) ?: return@detectDragGestures
                                if (beginDrag(DragOwner.IMMEDIATE, field, rootPos)) suppressTap = true
                            },
                            onDrag = { change, amount ->
                                if (dragOwner != DragOwner.IMMEDIATE) return@detectDragGestures
                                change.consume()
                                dragPosition += amount
                                settleDrag()
                            },
                            onDragEnd = { endDrag(DragOwner.IMMEDIATE) },
                            onDragCancel = { endDrag(DragOwner.IMMEDIATE) },
                        )
                    } finally {
                        // A no-op when this detector did not own the drag.
                        endDrag(DragOwner.IMMEDIATE)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { local ->
                            if (suppressTap) return@detectTapGestures
                            if (!isArranging) return@detectTapGestures
                            tileAt(toRoot(local))?.let {
                                // The same tick a slot change gives: the tile
                                // visibly changes size, but the finger is
                                // covering the part that changed.
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                cycleSpan(it)
                            }
                        },
                    )
                },
        // The dragged tile's row draws over its neighbours, or the tile slides
        // UNDER the next row on a long drag.
        rowModifier = { row -> if (row.any { it.module == dragged }) Modifier.zIndex(1f) else Modifier },
    ) { module, index, tileModifier ->
        val field = module.module
        val isDragged = dragged == field
        val wiggle = wiggleAngle(active = arranging && !isDragged, phase = index)
        val motion = motionOf(field)
        ModuleTile(
            index = index,
            lastIndex = modules.lastIndex,
            onMove = onMove,
            onCycleSpan = onCycleSpan,
            module = module,
            arranging = arranging,
            spec = spec,
            modifier =
                tileModifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .onGloballyPositioned { coords ->
                        bounds[field] = coords.boundsInRoot()
                        // A changed slot while not being dragged means the
                        // order (or a neighbour's height) changed under this
                        // tile: slide from the old slot to the new one. The
                        // dragged tile is exempt — the finger, not layout,
                        // says where it is.
                        val grid = gridCoords ?: return@onGloballyPositioned
                        val slot = grid.localPositionOf(coords, Offset.Zero)
                        val previous = slots.put(field, slot)
                        if (previous != null && previous != slot && dragged != field) {
                            scope.slide(motion, from = previous - slot)
                        }
                    }
                    .graphicsLayer {
                        // Every property is written on every pass, both
                        // branches: the layer keeps its last values between
                        // invocations, so a branch that "doesn't touch"
                        // translation would freeze the drag's offset onto the
                        // tile.
                        if (isDragged) {
                            // Anchor the grab point under the finger, wherever
                            // layout put the tile this frame.
                            val base = bounds[field]?.topLeft ?: (dragPosition - grabOffset)
                            val shift = dragPosition - grabOffset - base
                            translationX = shift.x
                            translationY = shift.y
                            scaleX = motion.scale.value
                            scaleY = motion.scale.value
                            rotationZ = 0f
                        } else {
                            translationX = motion.offset.value.x
                            translationY = motion.offset.value.y
                            scaleX = motion.scale.value
                            scaleY = motion.scale.value
                            rotationZ = wiggle.value
                        }
                    }
                    .testTag("module_${field.key}"),
        )
    }
}

/**
 * Slide a tile whose slot moved: it appears where it WAS and springs to where
 * it IS. Added to any motion already under way rather than replacing it, so a
 * tile whose slot moves twice in quick succession — a drop followed by the
 * pending reorder landing — keeps one continuous path.
 */
private fun CoroutineScope.slide(motion: TileMotion, from: Offset) =
    launch {
        motion.offset.snapTo(motion.offset.value + from)
        motion.offset.animateTo(Offset.Zero, settleSpring())
    }

/**
 * One tile: the always-on border (the user asked for the grid footprint to be
 * legible outside the editor — this thin line is that), a quiet label, and the
 * value at the size its width allows. Width IS prominence: a full tile shows
 * its value at hero size and drops the label (a full-width value speaks for
 * itself, as the old hero did), narrower tiles caption themselves and their
 * value steps down with them. The value is FITTED rather than styled per span
 * (see FittedText): the span sets the ceiling, and a value too long for it —
 * a conditions phrase, a pressure with its unit — shrinks to fit its tile
 * instead of breaking words or spilling past the border.
 *
 * The tile is also where arranging becomes reachable without gestures. Its
 * accessibility actions — Move up, Move down, Resize — are the SAME
 * [onMove]/[onCycleSpan] calls the drag and tap make, and they are offered at
 * all times rather than only while arranging: long-press-and-drag is not a
 * gesture a TalkBack or switch-access user can perform at all, so gating them
 * behind a mode they cannot enter would be gating them behind nothing.
 * Merging the tile into one node is what makes them reachable — an unmerged
 * node carrying no text of its own never takes accessibility focus, and
 * unfocusable actions are not actions.
 */
@Composable
private fun ModuleTile(
    module: ModuleValue,
    arranging: Boolean,
    spec: DensitySpec,
    /** This tile's place among the visible modules, and the last such index —
     * together they decide which move actions exist at the ends. */
    index: Int,
    lastIndex: Int,
    onMove: (ModuleKey, Int) -> Unit,
    onCycleSpan: (ModuleKey) -> Unit,
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
    // A full-width tile drops its label — the content is big enough to speak
    // for itself, as the old hero did, and the sun table brings its own column
    // headings.
    val showLabel = module.span != ModuleSpan.FULL
    TileShell(
        borderColor = borderColor,
        modifier =
            modifier
                .semantics(mergeDescendants = true) {
                    // The width is state, not a label: it changes under the
                    // user and is what Resize acts on, so it belongs where a
                    // screen reader re-reads it rather than in the name.
                    stateDescription = "${module.span.label} width"
                    customActions =
                        buildList {
                            if (index > 0) {
                                add(
                                    CustomAccessibilityAction("Move up") {
                                        onMove(module.module, index - 1)
                                        true
                                    },
                                )
                            }
                            if (index < lastIndex) {
                                add(
                                    CustomAccessibilityAction("Move down") {
                                        onMove(module.module, index + 1)
                                        true
                                    },
                                )
                            }
                            // Named for where it lands, not for what it does:
                            // "Resize" alone leaves the user to guess which of
                            // three widths a tap will pick.
                            add(
                                CustomAccessibilityAction("Resize to ${module.span.next().label.lowercase()}") {
                                    onCycleSpan(module.module)
                                    true
                                },
                            )
                        }
                },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showLabel) {
                Text(
                    text = module.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (val content = module.content) {
                is ModuleContent.Reading ->
                    // The hero's face and weight at every width, so a reading
                    // is one thing scaled rather than three styles that happen
                    // to share a tile.
                    FittedText(
                        text = content.text,
                        style = spec.heroStyle,
                        ceiling = spec.valueCeiling(module.span),
                        floor = VALUE_FLOOR,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                // Sun times draw themselves: they are a table at full width and
                // today's pair when narrower. See SunModule.kt for why that is
                // adaptation rather than two designs.
                is ModuleContent.Sun -> SunModuleContent(days = content.days, span = module.span, zone = content.zone)
            }
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
