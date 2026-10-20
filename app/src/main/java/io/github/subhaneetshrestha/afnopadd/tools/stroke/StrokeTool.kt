package io.github.subhaneetshrestha.afnopadd.tools.stroke

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
import io.github.subhaneetshrestha.afnopadd.tools.presets.StrokeParams
import io.github.subhaneetshrestha.afnopadd.tools.presets.ToolPreset

/** A freehand Ink stroke tool configured entirely by a [ToolPreset] (pen, pencil, brush, highlighter). */
class StrokeTool(private val preset: ToolPreset) : Tool {
    override val id = preset.id
    override val labelRes = preset.labelRes
    override val iconRes = preset.iconRes
    override val capabilities = setOf(ToolCapability.STROKE)

    override fun createController(context: ToolContext): ToolController = StrokeController(preset, context)

    companion object {
        /** Ink's recommended quantisation for frames whose unit spans one to ten screen pixels. */
        const val EPSILON = 0.025f
    }
}

internal class StrokeController(private val preset: ToolPreset, private val context: ToolContext) : ToolController {

    private val family = preset.text(StrokeParams.FAMILY, "ink:pressure-pen")
    private val sizeMultiplier = preset.number(StrokeParams.SIZE_MULTIPLIER, 1.0)
    private val opacity = preset.number(StrokeParams.OPACITY, 1.0).toFloat()
    private val forceOpaqueColor = preset.flag(StrokeParams.FORCE_OPAQUE_COLOR)

    /** Pointer samples are rendered by Ink's wet layer; the tool only cares about the finished stroke. */
    override fun onPointer(event: CanvasPointerEvent) = Unit

    override fun wetStrokeBrush(frame: StrokeFrame): BrushSpec {
        val style = context.styles.styleFor(preset.id)
        return BrushSpec(
            familyId = family,
            size = (style.widthPx * sizeMultiplier / frame.transform.scale).toFloat(),
            colorArgb = if (forceOpaqueColor) style.colorArgb or 0xFF000000 else style.colorArgb,
            opacity = opacity,
            epsilon = StrokeTool.EPSILON,
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
        sheet.transact(label = "Stroke") { add(element) }
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
