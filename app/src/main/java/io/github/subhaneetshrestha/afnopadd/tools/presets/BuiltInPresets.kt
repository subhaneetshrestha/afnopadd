package io.github.subhaneetshrestha.afnopadd.tools.presets

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyle

object BuiltInTools {
    const val STROKE_BASE = "tool:stroke"
    const val PEN = "tool:pen"
    const val PENCIL = "tool:pencil"
    const val BRUSH = "tool:brush"
    const val HIGHLIGHTER = "tool:highlighter"
    const val ERASER = "tool:eraser"
    const val LINE = "tool:line"
    const val ARROW = "tool:arrow"
    const val RECTANGLE = "tool:rect"
    const val ELLIPSE = "tool:ellipse"
    const val HAND = "tool:hand"
}

/** Parameter keys understood by the stroke tool. */
object StrokeParams {
    const val FAMILY = "family"
    const val SIZE_MULTIPLIER = "sizeMultiplier"
    const val OPACITY = "opacity"
    const val FORCE_OPAQUE_COLOR = "forceOpaqueColor"
}

/**
 * The v1 stroke tools (SPEC-drawing-tools.md decision 2). Pencil is a pen preset until Ink ships
 * a stable textured pencil; brush is a wide marker; the highlighter keeps a fixed 40 % opacity.
 */
object BuiltInPresets {

    val PEN = ToolPreset(
        id = BuiltInTools.PEN,
        baseToolId = BuiltInTools.STROKE_BASE,
        labelRes = R.string.tool_pen,
        iconRes = R.drawable.ic_tool_pen,
        params = mapOf(StrokeParams.FAMILY to ParamValue.Text("ink:pressure-pen")),
    )

    val PENCIL = ToolPreset(
        id = BuiltInTools.PENCIL,
        baseToolId = BuiltInTools.STROKE_BASE,
        labelRes = R.string.tool_pencil,
        iconRes = R.drawable.ic_tool_pencil,
        params = mapOf(
            StrokeParams.FAMILY to ParamValue.Text("ink:pressure-pen"),
            StrokeParams.SIZE_MULTIPLIER to ParamValue.Const(0.6),
            StrokeParams.OPACITY to ParamValue.Const(0.6),
        ),
    )

    val BRUSH = ToolPreset(
        id = BuiltInTools.BRUSH,
        baseToolId = BuiltInTools.STROKE_BASE,
        labelRes = R.string.tool_brush,
        iconRes = R.drawable.ic_tool_brush,
        params = mapOf(
            StrokeParams.FAMILY to ParamValue.Text("ink:marker"),
            StrokeParams.SIZE_MULTIPLIER to ParamValue.Const(2.0),
        ),
    )

    val HIGHLIGHTER = ToolPreset(
        id = BuiltInTools.HIGHLIGHTER,
        baseToolId = BuiltInTools.STROKE_BASE,
        labelRes = R.string.tool_highlighter,
        iconRes = R.drawable.ic_tool_highlighter,
        params = mapOf(
            StrokeParams.FAMILY to ParamValue.Text("ink:highlighter"),
            StrokeParams.OPACITY to ParamValue.Const(0.4),
            StrokeParams.FORCE_OPAQUE_COLOR to ParamValue.Const(1.0),
        ),
    )

    val strokeTools: List<ToolPreset> = listOf(PEN, PENCIL, BRUSH, HIGHLIGHTER)

    val defaultStyles: Map<String, ToolStyle> = mapOf(
        BuiltInTools.PEN to ToolStyle(colorArgb = 0xFF000000, widthPx = 4f),
        BuiltInTools.PENCIL to ToolStyle(colorArgb = 0xFF424242, widthPx = 4f),
        BuiltInTools.BRUSH to ToolStyle(colorArgb = 0xFF000000, widthPx = 8f),
        BuiltInTools.HIGHLIGHTER to ToolStyle(colorArgb = 0xFFFFEB3B, widthPx = 16f),
        BuiltInTools.LINE to ToolStyle(colorArgb = 0xFF000000, widthPx = 3f),
        BuiltInTools.ARROW to ToolStyle(colorArgb = 0xFF000000, widthPx = 3f),
        BuiltInTools.RECTANGLE to ToolStyle(colorArgb = 0xFF000000, widthPx = 3f),
        BuiltInTools.ELLIPSE to ToolStyle(colorArgb = 0xFF000000, widthPx = 3f),
    )
}
