package io.github.subhaneetshrestha.afnopadd.tools.pen

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.FinishedStroke
import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeFrame
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.tools.Tool
import io.github.subhaneetshrestha.afnopadd.tools.ToolCapability
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolController

/** The pen: a pressure-sensitive Ink stroke in the current colour and width. */
class PenTool : Tool {
    override val id = ID
    override val labelRes = R.string.tool_pen
    override val iconRes = R.drawable.ic_tool_pen
    override val capabilities = setOf(ToolCapability.STROKE)

    override fun createController(context: ToolContext): ToolController = PenController(context)

    companion object {
        const val ID = "tool:pen"
        const val FAMILY = "ink:pressure-pen"
        /** Ink's recommended quantisation for frames whose unit spans one to ten screen pixels. */
        const val EPSILON = 0.025f
    }
}

internal class PenController(private val context: ToolContext) : ToolController {

    /** Pointer samples are rendered by Ink's wet layer; the pen only cares about the finished stroke. */
    override fun onPointer(event: CanvasPointerEvent) = Unit

    override fun wetStrokeBrush(frame: StrokeFrame): BrushSpec {
        val style = context.style.value
        return BrushSpec(
            familyId = PenTool.FAMILY,
            size = (style.widthPx / frame.transform.scale).toFloat(),
            colorArgb = style.colorArgb,
            opacity = 1f,
            epsilon = PenTool.EPSILON,
        )
    }

    override fun onWetStrokeFinished(stroke: FinishedStroke) = commit(stroke.frame, stroke.points, stroke.brush, stroke.kind)

    /** Commits one stroke as a new top-most element; a stroke without samples is ignored. */
    internal fun commit(frame: StrokeFrame, points: StrokePoints, brush: BrushSpec, kind: PointerKind) {
        if (points.count == 0) return
        val now = context.clock()
        val sheet = context.sheet
        val element = StrokeElement(
            id = ElementId(context.ulid.next()),
            anchor = frame.anchor,
            frameLevel = frame.level,
            z = FractionalIndex.between(sheet.snapshot.value.ordered.lastOrNull()?.z, null),
            bounds = boundsOf(frame, points, brush),
            version = 1,
            versionNonce = context.random.nextInt(),
            createdAt = now,
            updatedAt = now,
            brush = brush,
            points = points,
        )
        sheet.transact(label = "Pen stroke") { add(element) }
    }

    private fun boundsOf(frame: StrokeFrame, points: StrokePoints, brush: BrushSpec): WorldRect {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (i in 0 until points.count) {
            minX = minOf(minX, points.x(i))
            maxX = maxOf(maxX, points.x(i))
            minY = minOf(minY, points.y(i))
            maxY = maxOf(maxY, points.y(i))
        }
        val pad = brush.size / 2f
        val unit = FrameLevels.worldUnitsPerLocalUnit(frame.level)
        return WorldRect(
            left = frame.anchor.x + (minX - pad) * unit,
            top = frame.anchor.y + (minY - pad) * unit,
            right = frame.anchor.x + (maxX + pad) * unit,
            bottom = frame.anchor.y + (maxY + pad) * unit,
        )
    }
}
