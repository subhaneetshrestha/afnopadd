package io.github.subhaneetshrestha.afnopadd.tools.shape

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.canvas.ShapeOutline
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeGeometry
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.ShapeStyle
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.tools.Tool
import io.github.subhaneetshrestha.afnopadd.tools.ToolCapability
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolController
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** A drag-to-create shape tool (SPEC-drawing-tools decision 5): one [ShapeElement] per drag, no fill. */
class ShapeTool(
    override val id: String,
    private val kind: ShapeKind,
    override val labelRes: Int,
    override val iconRes: Int,
) : Tool {
    override val capabilities = setOf(ToolCapability.SHAPE)

    override fun createController(context: ToolContext): ToolController = ShapeController(id, kind, context)
}

/** The v1 shape tools, in toolbar order. */
object ShapeTools {
    val all: List<ShapeTool> = listOf(
        ShapeTool(BuiltInTools.LINE, ShapeKind.LINE, R.string.tool_line, R.drawable.ic_tool_line),
        ShapeTool(BuiltInTools.ARROW, ShapeKind.ARROW, R.string.tool_arrow, R.drawable.ic_tool_arrow),
        ShapeTool(BuiltInTools.RECTANGLE, ShapeKind.RECTANGLE, R.string.tool_rectangle, R.drawable.ic_tool_rectangle),
        ShapeTool(BuiltInTools.ELLIPSE, ShapeKind.ELLIPSE, R.string.tool_ellipse, R.drawable.ic_tool_ellipse),
    )
}

/**
 * Turns one drag into one shape. The shape lives in the zoom frame current when the drag started,
 * anchored at its first point, with the width chosen in screen pixels converted to that frame, so
 * it looks the same thickness at the zoom it was drawn at. While dragging, the shape is a preview;
 * lifting commits it in one transaction and a cancelled drag commits nothing. With the constrain
 * modifier, boxes become squares (circles for the ellipse) and lines snap to 45 degree steps.
 */
internal class ShapeController(
    private val toolId: String,
    private val kind: ShapeKind,
    private val context: ToolContext,
) : ToolController {

    /** One drag; its [id] is minted once, so the preview of every move and the committed shape share it. */
    private class Drag(val pointerId: Long, val start: WorldPoint, val startScreen: ScreenPoint, val level: Int, val localPerPx: Double, val id: ElementId)

    private val preview = MutableStateFlow<List<Element>>(emptyList())
    override val previewElements: StateFlow<List<Element>> = preview.asStateFlow()

    private var drag: Drag? = null

    override fun onPointer(event: CanvasPointerEvent) {
        when (event.phase) {
            PointerPhase.DOWN -> {
                val camera = context.camera.camera.value
                val level = FrameLevels.levelFor(camera.zoom)
                drag = Drag(event.pointerId, event.world, event.screen, level, 1.0 / (camera.zoom * FrameLevels.worldUnitsPerLocalUnit(level)), ElementId(context.ulid.next()))
                preview.value = emptyList()
            }
            PointerPhase.MOVE -> drag?.takeIf { it.pointerId == event.pointerId }?.let { preview.value = listOfNotNull(shapeFor(it, event)) }
            PointerPhase.UP -> drag?.takeIf { it.pointerId == event.pointerId }?.let { current ->
                val shape = shapeFor(current, event)
                reset()
                if (shape != null) context.sheet.transact(label = "Shape") { add(shape) }
            }
            PointerPhase.CANCEL -> if (drag?.pointerId == event.pointerId) reset()
        }
    }

    override fun onDeactivated() = reset()

    private fun reset() {
        drag = null
        preview.value = emptyList()
    }

    /** The shape for the drag so far, or null while it is too small to mean anything (a tap). */
    private fun shapeFor(drag: Drag, event: CanvasPointerEvent): ShapeElement? {
        if (hypot(event.screen.x - drag.startScreen.x, event.screen.y - drag.startScreen.y) < MIN_DRAG_PX) return null
        val end = if (event.constrain) constrained(drag.start, event.world) else event.world
        val localEnd = FrameLevels.worldToLocal(end, drag.start, drag.level)
        val style = context.styles.styleFor(toolId)
        val width = (style.widthPx * drag.localPerPx).toFloat()
        val origin = LocalPoint(0f, 0f)
        val local = ShapeOutline.localBounds(kind, origin, localEnd, width)
        val unit = FrameLevels.worldUnitsPerLocalUnit(drag.level)
        val now = context.clock()
        return ShapeElement(
            id = drag.id,
            anchor = drag.start,
            frameLevel = drag.level,
            z = FractionalIndex.between(context.sheet.snapshot.value.ordered.lastOrNull()?.z, null),
            bounds = WorldRect(
                drag.start.x + local.left * unit,
                drag.start.y + local.top * unit,
                drag.start.x + local.right * unit,
                drag.start.y + local.bottom * unit,
            ),
            version = 1,
            versionNonce = context.random.nextInt(),
            createdAt = now,
            updatedAt = now,
            shape = kind,
            geometry = ShapeGeometry(origin, localEnd),
            style = ShapeStyle(strokeWidth = width, colorArgb = style.colorArgb),
        )
    }

    /** Squares and circles for boxes, 45 degree steps for lines; the drag's direction and reach are kept. */
    private fun constrained(start: WorldPoint, end: WorldPoint): WorldPoint {
        val dx = end.x - start.x
        val dy = end.y - start.y
        return when (kind) {
            ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> {
                val side = max(abs(dx), abs(dy))
                WorldPoint(start.x + side * signOf(dx), start.y + side * signOf(dy))
            }
            ShapeKind.LINE, ShapeKind.ARROW -> {
                val length = hypot(dx, dy)
                val angle = (atan2(dy, dx) / STEP).roundToInt() * STEP
                WorldPoint(start.x + length * cos(angle), start.y + length * sin(angle))
            }
        }
    }

    private fun signOf(value: Double) = if (value < 0) -1.0 else 1.0

    private companion object {
        /** A drag shorter than this on screen is a tap, not a shape. */
        const val MIN_DRAG_PX = 4.0
        const val STEP = PI / 4
    }
}
