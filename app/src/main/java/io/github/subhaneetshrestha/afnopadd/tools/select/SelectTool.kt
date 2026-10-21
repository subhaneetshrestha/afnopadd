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
import kotlin.math.hypot

/**
 * Selects and moves elements (SPEC-drawing-tools decision 7, first half; scale and rotate handles
 * are Task 21). The selection itself lives in [ToolContext.selection], shared with the selection
 * commands and the canvas overlay.
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
 */
class SelectController internal constructor(private val context: ToolContext) : ToolController {

    private enum class Mode { PRESSED, MOVING, MARQUEE }

    private class Gesture(
        val pointerId: Long,
        val downScreen: ScreenPoint,
        val downWorld: WorldPoint,
        val downTimeMillis: Long,
        val hit: ElementId?,
        var mode: Mode = Mode.PRESSED,
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
            gesture = Gesture(
                pointerId = event.pointerId,
                downScreen = event.screen,
                downWorld = event.world,
                downTimeMillis = event.timeMillis,
                hit = context.hitTester.elementAt(event.world, TOLERANCE_PX, context.camera.camera.value),
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
        gesture.mode = if (hit != null) {
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
    }
}
