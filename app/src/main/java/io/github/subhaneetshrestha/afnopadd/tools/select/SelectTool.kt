package io.github.subhaneetshrestha.afnopadd.tools.select

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.movedBy
import io.github.subhaneetshrestha.afnopadd.document.withCommon
import io.github.subhaneetshrestha.afnopadd.tools.Tool
import io.github.subhaneetshrestha.afnopadd.tools.ToolCapability
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolController
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.subhaneetshrestha.afnopadd.canvas.SelectionHandles
import io.github.subhaneetshrestha.afnopadd.tools.commands.ZoomCommands
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Selects, moves, scales and rotates elements (SPEC-drawing-tools decision 7). The selection itself
 * lives in [ToolContext.selection], shared with the selection commands and the canvas overlay.
 */
class SelectTool : Tool {
    override val id = BuiltInTools.SELECT
    override val labelRes = R.string.tool_select
    override val iconRes = R.drawable.ic_tool_select
    override val capabilities = setOf(ToolCapability.SELECT)

    override fun createController(context: ToolContext): SelectController = SelectController(context)
}

/**
 * One gesture at a time. A press that stays within [SLOP_PX] is a tap: it selects the topmost
 * element under it (or clears the selection on empty space), and held for [LONG_PRESS_MILLIS] it
 * adds that element to the selection or removes it. A drag that starts on an element moves the
 * selection (selecting that element first if it was not selected); the originals are hidden and
 * moved copies previewed until the pointer lifts, which commits one "Move" transaction. A drag
 * that starts on empty space draws a marquee and selects what lies entirely inside it.
 *
 * A press on a [SelectionHandles] handle transforms the whole selection about the centre of its
 * bounds instead: the scale handle scales uniformly by how far the pointer is from the centre
 * compared with where it started, and the rotate handle turns by the change in the pointer's angle
 * around the centre, snapping to multiples of [SNAP_STEP_DEG] within [SNAP_WITHIN_DEG]. Both are
 * previewed like a move and committed as one transaction.
 */
class SelectController internal constructor(private val context: ToolContext) : ToolController {

    private enum class Mode { PRESSED, MOVING, MARQUEE, SCALING, ROTATING }

    private class Gesture(
        val pointerId: Long,
        val downScreen: ScreenPoint,
        val downWorld: WorldPoint,
        val downTimeMillis: Long,
        val hit: ElementId?,
        var mode: Mode = Mode.PRESSED,
        /** Set when the press is on a transform handle: the mode it becomes once it drags. A tap there is still a tap. */
        val handleMode: Mode? = null,
        /** The selection's centre, for scaling and rotating. */
        val pivot: WorldPoint? = null,
    )

    private val selection get() = context.selection
    private var gesture: Gesture? = null

    private val hidden = MutableStateFlow<Set<ElementId>>(emptySet())
    override val hiddenElements: StateFlow<Set<ElementId>> = hidden.asStateFlow()

    private val preview = MutableStateFlow<List<Element>>(emptyList())
    override val previewElements: StateFlow<List<Element>> = preview.asStateFlow()

    private val _marquee = MutableStateFlow<WorldRect?>(null)
    override val marquee: StateFlow<WorldRect?> = _marquee.asStateFlow()

    override fun onPointer(event: CanvasPointerEvent) {
        if (event.phase == PointerPhase.DOWN) {
            resetPreview()
            val hit = context.hitTester.elementAt(event.world, TOLERANCE_PX, context.camera.camera.value)
            gesture = handleGesture(event, hit) ?: Gesture(
                pointerId = event.pointerId,
                downScreen = event.screen,
                downWorld = event.world,
                downTimeMillis = event.timeMillis,
                hit = hit,
            )
            return
        }
        val current = gesture?.takeIf { it.pointerId == event.pointerId } ?: return
        when (event.phase) {
            PointerPhase.MOVE -> {
                startDraggingIfMoved(current, event)
                showProgress(current, event)
            }
            PointerPhase.UP -> {
                startDraggingIfMoved(current, event)
                when (current.mode) {
                    Mode.PRESSED -> tap(current, event)
                    Mode.MOVING -> commitMove(current, event)
                    Mode.MARQUEE -> selection.set(context.hitTester.elementsIn(rectBetween(current.downWorld, event.world)).toSet())
                    Mode.SCALING, Mode.ROTATING -> commitTransform(current, event)
                }
                gesture = null
                resetPreview()
            }
            PointerPhase.CANCEL -> {
                gesture = null
                resetPreview()
            }
            PointerPhase.DOWN -> Unit
        }
    }

    override fun onDeactivated() {
        gesture = null
        resetPreview()
        selection.clear()
    }

    private fun startDraggingIfMoved(gesture: Gesture, event: CanvasPointerEvent) {
        if (gesture.mode != Mode.PRESSED) return
        if (hypot(event.screen.x - gesture.downScreen.x, event.screen.y - gesture.downScreen.y) <= SLOP_PX) return
        val hit = gesture.hit
        gesture.mode = if (gesture.handleMode != null) {
            gesture.handleMode
        } else if (hit != null) {
            if (hit !in selection.selected.value) selection.set(setOf(hit))
            Mode.MOVING
        } else {
            Mode.MARQUEE
        }
    }

    private fun showProgress(gesture: Gesture, event: CanvasPointerEvent) {
        when (gesture.mode) {
            Mode.PRESSED -> Unit
            Mode.MOVING -> {
                val dx = event.world.x - gesture.downWorld.x
                val dy = event.world.y - gesture.downWorld.y
                val elements = context.sheet.snapshot.value.elements
                hidden.value = selection.selected.value
                preview.value = selection.selected.value.mapNotNull { elements[it]?.movedBy(dx, dy) }
            }
            Mode.MARQUEE -> _marquee.value = rectBetween(gesture.downWorld, event.world)
            Mode.SCALING, Mode.ROTATING -> {
                val elements = context.sheet.snapshot.value.elements
                hidden.value = selection.selected.value
                preview.value = selection.selected.value.mapNotNull { id -> elements[id]?.let { transformed(gesture, event, it) } }
            }
        }
    }

    /** A gesture on a transform handle of the current selection, or null if [event] is not on one. */
    private fun handleGesture(event: CanvasPointerEvent, hit: ElementId?): Gesture? {
        val elements = context.sheet.snapshot.value.elements
        val bounds = ZoomCommands.boundsOf(selection.selected.value.mapNotNull { elements[it]?.bounds }) ?: return null
        val handle = SelectionHandles.handleAt(event.world, bounds, context.camera.camera.value.zoom, context.density) ?: return null
        return Gesture(
            pointerId = event.pointerId,
            downScreen = event.screen,
            downWorld = event.world,
            downTimeMillis = event.timeMillis,
            hit = hit,
            handleMode = if (handle == SelectionHandles.Handle.SCALE) Mode.SCALING else Mode.ROTATING,
            pivot = WorldPoint((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2),
        )
    }

    /** The scale factor of a scale gesture so far: the pointer's distance from the centre now over at the start. */
    private fun scaleFactor(gesture: Gesture, event: CanvasPointerEvent): Double {
        val pivot = gesture.pivot ?: return 1.0
        val start = hypot(gesture.downWorld.x - pivot.x, gesture.downWorld.y - pivot.y)
        if (start == 0.0) return 1.0
        return (hypot(event.world.x - pivot.x, event.world.y - pivot.y) / start).coerceIn(MIN_SCALE_STEP, MAX_SCALE_STEP)
    }

    /** The clockwise turn of a rotate gesture so far, in degrees, snapped near multiples of [SNAP_STEP_DEG]. */
    private fun rotationDegrees(gesture: Gesture, event: CanvasPointerEvent): Double {
        val pivot = gesture.pivot ?: return 0.0
        val turn = Math.toDegrees(
            atan2(event.world.y - pivot.y, event.world.x - pivot.x) - atan2(gesture.downWorld.y - pivot.y, gesture.downWorld.x - pivot.x),
        )
        val snapped = Math.round(turn / SNAP_STEP_DEG) * SNAP_STEP_DEG
        return if (abs(turn - snapped) <= SNAP_WITHIN_DEG) snapped else turn
    }

    private fun transformed(gesture: Gesture, event: CanvasPointerEvent, element: Element): Element {
        val pivot = gesture.pivot ?: return element
        return when (gesture.mode) {
            Mode.SCALING -> Transforms.scaled(element, pivot, scaleFactor(gesture, event))
            Mode.ROTATING -> Transforms.rotated(element, pivot, rotationDegrees(gesture, event))
            else -> element
        }
    }

    private fun commitTransform(gesture: Gesture, event: CanvasPointerEvent) {
        val unchanged = when (gesture.mode) {
            Mode.SCALING -> scaleFactor(gesture, event) == 1.0
            else -> rotationDegrees(gesture, event) == 0.0
        }
        if (unchanged) return
        val live = context.sheet.snapshot.value.elements
        val ids = selection.selected.value.filter { it in live }
        if (ids.isEmpty()) return
        val now = context.clock()
        context.sheet.transact(label = if (gesture.mode == Mode.SCALING) "Scale" else "Rotate") {
            for (id in ids) {
                update(id) { element ->
                    transformed(gesture, event, element).withCommon(version = element.version + 1, versionNonce = context.random.nextInt(), updatedAt = now)
                }
            }
        }
    }

    private fun tap(gesture: Gesture, event: CanvasPointerEvent) {
        val hit = gesture.hit
        if (event.timeMillis - gesture.downTimeMillis >= LONG_PRESS_MILLIS) {
            if (hit != null) selection.toggle(hit)
        } else {
            selection.set(if (hit != null) setOf(hit) else emptySet())
        }
    }

    private fun commitMove(gesture: Gesture, event: CanvasPointerEvent) {
        val dx = event.world.x - gesture.downWorld.x
        val dy = event.world.y - gesture.downWorld.y
        if (dx == 0.0 && dy == 0.0) return
        val live = context.sheet.snapshot.value.elements
        val ids = selection.selected.value.filter { it in live }
        if (ids.isEmpty()) return
        val now = context.clock()
        context.sheet.transact(label = "Move") {
            for (id in ids) {
                update(id) { element ->
                    element.movedBy(dx, dy).withCommon(version = element.version + 1, versionNonce = context.random.nextInt(), updatedAt = now)
                }
            }
        }
    }

    private fun resetPreview() {
        hidden.value = emptySet()
        preview.value = emptyList()
        _marquee.value = null
    }

    private fun rectBetween(a: WorldPoint, b: WorldPoint) = WorldRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))

    companion object {
        /** How close, in screen pixels, a tap must come to an element's ink to pick it. */
        const val TOLERANCE_PX = 12f
        /** Movement in screen pixels that turns a press into a drag. */
        const val SLOP_PX = 8.0
        const val LONG_PRESS_MILLIS = 500L
        const val SNAP_STEP_DEG = 15.0
        const val SNAP_WITHIN_DEG = 3.0
        /** Bounds on one scale gesture, so a pointer dragged onto the centre cannot collapse the selection. */
        const val MIN_SCALE_STEP = 0.05
        const val MAX_SCALE_STEP = 20.0
    }
}
