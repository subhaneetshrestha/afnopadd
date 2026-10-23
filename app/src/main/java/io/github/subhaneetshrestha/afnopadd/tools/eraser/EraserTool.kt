package io.github.subhaneetshrestha.afnopadd.tools.eraser

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.tools.Tool
import io.github.subhaneetshrestha.afnopadd.tools.ToolCapability
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolController
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Removes whole strokes and shapes that the eraser path touches (drawing-tools spec decision 6).
 * Images are never erased; they are deleted through selection. Partial stroke erasing waits for Ink 1.1.
 */
class EraserTool : Tool {
    override val id = BuiltInTools.ERASER
    override val labelRes = R.string.tool_eraser
    override val iconRes = R.drawable.ic_tool_eraser
    override val capabilities = setOf(ToolCapability.ERASE)

    override fun createController(context: ToolContext): ToolController = EraserController(context)

    companion object {
        /** The eraser's reach around the pointer, in screen pixels, so it feels the same at every zoom. */
        const val RADIUS_PX = 12.0
    }
}

/**
 * Collects everything the pointer touches during one gesture and hides it at once as a preview;
 * lifting the pointer deletes it all in one undoable transaction, and a cancelled gesture (for
 * example a second finger starting a pinch) restores it untouched.
 */
internal class EraserController(private val context: ToolContext) : ToolController {

    private val touched = MutableStateFlow<Set<ElementId>>(emptySet())
    override val hiddenElements: StateFlow<Set<ElementId>> = touched.asStateFlow()

    private var pointerId: Long? = null
    private var last: WorldPoint? = null

    override fun onPointer(event: CanvasPointerEvent) {
        when (event.phase) {
            PointerPhase.DOWN -> {
                pointerId = event.pointerId
                last = event.world
                touched.value = emptySet()
                eraseAlong(event.world, event.world)
            }
            PointerPhase.MOVE -> if (event.pointerId == pointerId) {
                eraseAlong(last ?: event.world, event.world)
                last = event.world
            }
            PointerPhase.UP -> if (event.pointerId == pointerId) {
                eraseAlong(last ?: event.world, event.world)
                commit()
            }
            PointerPhase.CANCEL -> if (event.pointerId == pointerId) reset()
        }
    }

    override fun onDeactivated() = reset()

    private fun eraseAlong(from: WorldPoint, to: WorldPoint) {
        val radius = EraserTool.RADIUS_PX / context.camera.camera.value.zoom
        val elements = context.sheet.snapshot.value.elements
        val hits = context.hitTester.elementsAlong(from, to, radius).filter { id ->
            val element = elements[id]
            element is StrokeElement || element is ShapeElement
        }
        if (hits.isNotEmpty()) touched.value += hits
    }

    private fun commit() {
        val ids = touched.value
        reset()
        if (ids.isEmpty()) return
        context.sheet.transact(label = "Erase") {
            // Skip anything that vanished meanwhile (an undo cannot run mid-gesture, but stay safe).
            val live = context.sheet.snapshot.value.elements
            ids.filter { it in live }.forEach { delete(it) }
        }
    }

    private fun reset() {
        pointerId = null
        last = null
        touched.value = emptySet()
    }
}
