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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.raylytics.justmyweather.view.ModuleContent
import io.raylytics.justmyweather.view.ModuleKey
import io.raylytics.justmyweather.view.ModuleSize
import io.raylytics.justmyweather.view.ModuleValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * The modular glance: every visible field as a bordered tile on a 4-column
 * lattice, plus the launcher-style gestures that rearrange and resize it. All
 * the gesture and animation machinery is deliberately confined to this file —
 * the geometry it acts on (sizes, cell packing, reorder transforms) is pure
 * Kotlin in view/, and the rest of the app only ever sees a ViewConfig change.
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

/**
 * What a drag is doing to its tile. Decided where the finger landed: on the
 * corner handle it resizes, anywhere else on the tile it moves. One drag is
 * one or the other for its whole life.
 */
private enum class DragKind { MOVE, RESIZE }

/** The dragged tile lifts slightly, the way a launcher icon does. */
private const val DRAG_SCALE = 1.04f

/** The resize handle: a dot on the tile's bottom-right corner. */
private val HANDLE_RADIUS = 6.dp

/** How far the handle's dot sits inside the corner, so it rides the border's
 * curve rather than floating off the tile. */
private val HANDLE_INSET = 3.dp

/**
 * The region around a tile's bottom-right corner that starts a resize instead
 * of a move: this far INSIDE the corner along each edge, and [HANDLE_REACH]
 * outside it. Far bigger than the dot it surrounds: the dot is what to aim
 * at, this is what a thumb actually lands on.
 */
private val HANDLE_HIT = 40.dp

/**
 * How far past the corner the handle still catches. A drag's start is not the
 * finger's landing point but where it crossed the touch slop, which for a
 * growing drag is down-and-right of the dot — outside the tile — by the slop
 * plus whatever a fast finger covered in the frame that crossed it. Verified
 * on-device: with only half a gap of reach, a grow from the dot missed and
 * was taken for a move. The cost is a thin strip of the neighbour's corner
 * that resizes this tile instead of moving that one.
 */
private val HANDLE_REACH = 24.dp

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
 * wiggle, borders take the accent, a handle appears on every corner), and the
 * same hold flows straight into a drag. While arranging, drag a tile to move
 * it and drag its bottom-right corner to resize it — the corner snaps to
 * cells as the finger draws the rectangle, and stops at the module's own
 * minimum. Every edit lands as a
 * [ViewConfig][io.raylytics.justmyweather.view.ViewConfig] transform through
 * [onMove]/[onResize], so a drag persists like any other customization —
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
    /** Give a module this footprint. The config clamps it to the module's
     * minimum, so the grid asks for whatever the finger drew. */
    onResize: (ModuleKey, ModuleSize) -> Unit,
    /** Move a module so it lands at this index among the visible ones. */
    onMove: (ModuleKey, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val gapPx = with(density) { spec.moduleGap.toPx() }
    val handleHitPx = with(density) { HANDLE_HIT.toPx() }
    val handleReachPx = with(density) { HANDLE_REACH.toPx() }

    // The gesture coroutines below live in pointerInput(Unit) — never
    // restarted, so they cannot lose a gesture — which means everything they
    // touch must be readable through state, never captured parameter values.
    val currentModules by rememberUpdatedState(modules)
    val isArranging by rememberUpdatedState(arranging)
    val startArranging by rememberUpdatedState(onStartArranging)
    val resize by rememberUpdatedState(onResize)
    val move by rememberUpdatedState(onMove)

    // Drag state. Positions are all in window-root coordinates — one shared
    // frame for the pointer and every tile's bounds, so nothing needs to know
    // which row anything is in. `bounds` is refreshed by layout after every
    // reorder, which is what keeps the dragged tile anchored under the finger
    // when its slot (and therefore its layout position) changes mid-drag.
    var dragged by remember { mutableStateOf<ModuleKey?>(null) }
    var dragKind by remember { mutableStateOf<DragKind?>(null) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    // A resize measures the rectangle the finger has drawn from the tile's
    // top-left AS IT WAS when the drag began. Growing a tile can move it (it
    // no longer fits where it sat and re-packs onto the next row); measuring
    // from where it is now would then change the answer under a still finger.
    var resizeOrigin by remember { mutableStateOf(Offset.Zero) }
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
    // The resize's twin of pendingTarget.
    var pendingSize by remember { mutableStateOf<ModuleSize?>(null) }
    // Counts drags, so a drop's deferred clean-up can tell whether the drag it
    // belongs to is still the current one.
    var dragSerial by remember { mutableStateOf(0) }

    // Gesture positions arrive grid-local; tiles report window-root bounds.
    fun toRoot(local: Offset): Offset = gridCoords?.localToRoot(local) ?: local

    fun tileAt(rootPos: Offset): ModuleKey? =
        currentModules.firstOrNull { bounds[it.module]?.contains(rootPos) == true }?.module

    /** The tile whose corner handle is under [rootPos], if any: inside the
     * corner by [HANDLE_HIT], past it by [HANDLE_REACH]. */
    fun handleAt(rootPos: Offset): ModuleKey? =
        currentModules
            .firstOrNull {
                val rect = bounds[it.module] ?: return@firstOrNull false
                rootPos.x in (rect.right - handleHitPx)..(rect.right + handleReachPx) &&
                    rootPos.y in (rect.bottom - handleHitPx)..(rect.bottom + handleReachPx)
            }?.module

    /** What a drag starting at [rootPos] would do, and to which tile — or null
     * off every tile. The handle is only live while arranging: outside the
     * mode there is no handle to see, so a hold on a corner is a hold on the
     * tile. */
    fun dragAt(rootPos: Offset): Pair<ModuleKey, DragKind>? {
        if (isArranging) handleAt(rootPos)?.let { return it to DragKind.RESIZE }
        return tileAt(rootPos)?.let { it to DragKind.MOVE }
    }

    /** Claim the drag for [owner], or return false if someone else has it. */
    fun beginDrag(owner: DragOwner, field: ModuleKey, kind: DragKind, rootPos: Offset): Boolean {
        if (dragOwner != null) return false
        dragSerial++
        dragOwner = owner
        dragged = field
        dragKind = kind
        dragPosition = rootPos
        pendingTarget = null
        pendingSize = null
        val rest = bounds[field]?.topLeft ?: rootPos
        when (kind) {
            DragKind.RESIZE -> resizeOrigin = rest
            DragKind.MOVE -> {
                grabOffset = rootPos - rest
                // The lift. Any slide still in flight is abandoned: the drag
                // positions the tile absolutely from here on.
                val motion = motionOf(field)
                scope.launch {
                    motion.offset.snapTo(Offset.Zero)
                    motion.scale.animateTo(DRAG_SCALE, settleSpring())
                }
            }
        }
        return true
    }

    fun endDrag(owner: DragOwner) {
        if (dragOwner != owner) return
        val field = dragged
        val kind = dragKind
        dragOwner = null
        pendingTarget = null
        pendingSize = null
        if (field == null) return
        if (kind == DragKind.RESIZE) {
            // Nothing to land: a resize never lifted the tile, and each cell
            // it crossed was committed as it crossed it.
            dragged = null
            dragKind = null
            return
        }
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
            dragKind = null
            coroutineScope {
                launch { motion.scale.animateTo(1f, settleSpring()) }
                launch { motion.offset.animateTo(Offset.Zero, settleSpring()) }
            }
        }
    }

    // Nearest-center wins, not rect containment: once a tile moves under the
    // pointer, ITS center is the nearest, so the arrangement is stable by
    // construction — rect hit-testing oscillates when tiles of different sizes
    // land where the pointer already is.
    fun settleMove() {
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

    // The finger is drawing the tile's far corner; the size is however many
    // cells that rectangle rounds to, so the tile grows half a cell before it
    // snaps and shrinks the same way. The cell pitch is read off the tile
    // itself — its width is `columns` cells and `columns - 1` gaps — rather
    // than passed in, so this needs to know nothing about the lattice's
    // arithmetic. The config clamps to the module's minimum; the tick fires
    // only when a size is actually requested, so dragging past the floor is
    // silent, which is how the floor is felt.
    fun settleResize() {
        val field = dragged ?: return
        val module = currentModules.firstOrNull { it.module == field } ?: return
        val rect = bounds[field] ?: return
        val current = module.size
        val pitchX = (rect.width + gapPx) / current.columns
        val pitchY = (rect.height + gapPx) / current.rows
        val drawn = dragPosition - resizeOrigin
        val target =
            ModuleSize(
                columns = (drawn.x / pitchX).roundToInt(),
                rows = (drawn.y / pitchY).roundToInt(),
            ).clamp(field.minSize)
        if (target != current && target != pendingSize) {
            pendingSize = target
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            resize(field, target)
        }
    }

    fun settleDrag() {
        when (dragKind) {
            DragKind.MOVE -> settleMove()
            DragKind.RESIZE -> settleResize()
            null -> Unit
        }
    }

    CellGrid(
        items = modules,
        size = { it.size },
        gap = spec.moduleGap,
        cellAspect = spec.cellAspect,
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
                            val rootPos = toRoot(local)
                            // Decided BEFORE the mode flips: a hold that opens
                            // the mode is a hold on a tile, not on a handle
                            // that was not there to be held.
                            val (field, kind) = dragAt(rootPos) ?: return@detectDragGesturesAfterLongPress
                            if (!isArranging) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                startArranging()
                            }
                            beginDrag(DragOwner.LONG_PRESS, field, kind, rootPos)
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
                // Once the tiles are wiggling, a plain drag moves one — or,
                // from its corner, resizes it — with no second long-press.
                // That is what a launcher does, and holding again for every
                // tile you want to nudge is the kind of friction people read
                // as the gesture not having worked.
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
                                val (field, kind) = dragAt(rootPos) ?: return@detectDragGestures
                                beginDrag(DragOwner.IMMEDIATE, field, kind, rootPos)
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
                // Taps do nothing to the grid, and are consumed so that they do
                // nothing to anything else either: the tap-on-empty-ground
                // detector above this grid leaves arrange mode, and a tap on
                // a wiggling tile must not — the launcher contract is that
                // the way out is Done, Back, or the ground, never the thing
                // you were editing.
                .pointerInput(Unit) { detectTapGestures {} },
    ) { module, index, tileModifier ->
        val field = module.module
        // Only a MOVE lifts the tile and pins it under the finger; a resize
        // leaves it in the lattice and changes its cells.
        val isLifted = dragged == field && dragKind == DragKind.MOVE
        val wiggle = wiggleAngle(active = arranging && !isLifted, phase = index)
        val motion = motionOf(field)
        ModuleTile(
            index = index,
            lastIndex = modules.lastIndex,
            onMove = onMove,
            onResize = onResize,
            module = module,
            arranging = arranging,
            spec = spec,
            modifier =
                tileModifier
                    // The dragged tile draws over its neighbours, or it slides
                    // UNDER the next row on a long drag.
                    .zIndex(if (dragged == field) 1f else 0f)
                    .onGloballyPositioned { coords ->
                        bounds[field] = coords.boundsInRoot()
                        // A changed slot while not being moved means the
                        // order (or a neighbour's size) changed under this
                        // tile: slide from the old slot to the new one. The
                        // lifted tile is exempt — the finger, not layout, says
                        // where it is. A tile being RESIZED is not: growing
                        // can re-pack it onto the next row, and it should be
                        // seen going there.
                        val grid = gridCoords ?: return@onGloballyPositioned
                        val slot = grid.localPositionOf(coords, Offset.Zero)
                        val previous = slots.put(field, slot)
                        if (previous != null && previous != slot && !isLifted) {
                            scope.slide(motion, from = previous - slot)
                        }
                    }
                    .graphicsLayer {
                        // Every property is written on every pass, both
                        // branches: the layer keeps its last values between
                        // invocations, so a branch that "doesn't touch"
                        // translation would freeze the drag's offset onto the
                        // tile.
                        if (isLifted) {
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
 * legible outside the editor — this thin line is that), a quiet label, the
 * value at the size its cells allow, and — while arranging — the handle on
 * its corner. Size IS prominence: the value is FITTED to the tile (see
 * FittedText) up to the hero size, so a 4×2 temperature is the hero, the same
 * reading at 1×1 is a small number, and a conditions phrase or a pressure
 * with its unit shrinks to fit rather than breaking words or spilling past the
 * border. A full-width tile drops its label — a value that wide speaks for
 * itself, as the old hero did.
 *
 * The tile is also where arranging becomes reachable without gestures. Its
 * accessibility actions — Move up/down, Wider/Narrower, Taller/Shorter — are
 * the SAME [onMove]/[onResize] calls the drags make, and they are offered at
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
    onResize: (ModuleKey, ModuleSize) -> Unit,
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
    val showLabel = module.size.columns != ModuleSize.COLUMNS
    val size = module.size
    val min = module.module.minSize
    val handleColor = MaterialTheme.colorScheme.primary
    val handleRing = MaterialTheme.colorScheme.background
    TileShell(
        borderColor = borderColor,
        modifier =
            modifier
                .drawWithContent {
                    drawContent()
                    // The handle: a dot on the corner, ringed in the ground
                    // colour so it reads as sitting ON the border rather than
                    // as a thickening of it. Drawn, not composed, so it adds
                    // no node to the tile's semantics and no layout to its
                    // cells.
                    if (arranging) {
                        val inset = HANDLE_INSET.toPx()
                        val center = Offset(this.size.width - inset, this.size.height - inset)
                        drawCircle(handleRing, radius = HANDLE_RADIUS.toPx() + 2.dp.toPx(), center = center)
                        drawCircle(handleColor, radius = HANDLE_RADIUS.toPx(), center = center)
                    }
                }
                .semantics(mergeDescendants = true) {
                    // The size is state, not a label: it changes under the
                    // user and is what the resize actions act on, so it
                    // belongs where a screen reader re-reads it rather than
                    // in the name.
                    stateDescription = size.label
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
                            // One cell at a time in each direction, and only
                            // the steps that exist: a tile at the grid's edge
                            // is not offered "Wider", one at its module's
                            // minimum is not offered "Narrower". An action
                            // that would do nothing is absent, not ignored.
                            if (size.columns < ModuleSize.COLUMNS) {
                                add(
                                    CustomAccessibilityAction("Wider") {
                                        onResize(module.module, size.wider())
                                        true
                                    },
                                )
                            }
                            if (size.columns > min.columns) {
                                add(
                                    CustomAccessibilityAction("Narrower") {
                                        onResize(module.module, size.narrower())
                                        true
                                    },
                                )
                            }
                            if (size.rows < ModuleSize.MAX_ROWS) {
                                add(
                                    CustomAccessibilityAction("Taller") {
                                        onResize(module.module, size.taller())
                                        true
                                    },
                                )
                            }
                            if (size.rows > min.rows) {
                                add(
                                    CustomAccessibilityAction("Shorter") {
                                        onResize(module.module, size.shorter())
                                        true
                                    },
                                )
                            }
                        }
                },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showLabel) {
                // Fitted like the value, on one line: "Temperature" does not
                // fit a single cell at the label size, and "Temperat…" is
                // worse than the same word a shade smaller.
                val labelStyle = MaterialTheme.typography.labelSmall
                FittedText(
                    text = module.label,
                    style = labelStyle,
                    ceiling = labelStyle.fontSize,
                    floor = LABEL_FLOOR,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            when (val content = module.content) {
                is ModuleContent.Reading ->
                    // The hero's face and weight at every size, so a reading
                    // is one thing scaled rather than several styles that
                    // happen to share a tile. The ceiling is the hero itself;
                    // the tile's cells decide how close the value gets.
                    FittedText(
                        text = content.text,
                        style = spec.heroStyle,
                        ceiling = spec.heroStyle.fontSize,
                        floor = VALUE_FLOOR,
                        color = MaterialTheme.colorScheme.onBackground,
                        // Two lines per row of cells: a phrase in a taller
                        // tile may use the height rather than shrink.
                        maxLines = 2 * size.rows,
                    )
                // Sun times draw themselves: a table at full size, today's
                // pair when smaller. See SunModule.kt for why that is
                // adaptation rather than two designs.
                is ModuleContent.Sun -> SunModuleContent(days = content.days, size = module.size, zone = content.zone)
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
