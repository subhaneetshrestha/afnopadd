package io.github.subhaneetshrestha.afnopadd.tools.hand

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.tools.Tool
import io.github.subhaneetshrestha.afnopadd.tools.ToolCapability
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolController
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools

/**
 * Moves around without editing (SPEC-drawing-tools decision 8). Its [ToolCapability.NAVIGATE]
 * makes the input policy hand every single pointer to the canvas's navigate gestures (pan, and a
 * double tap zooms 2x); two pointers pinch as in every tool. Nothing reaches the controller in
 * practice, and whatever does is ignored, so the sheet never changes.
 */
class HandTool : Tool {
    override val id = BuiltInTools.HAND
    override val labelRes = R.string.tool_hand
    override val iconRes = R.drawable.ic_tool_hand
    override val capabilities = setOf(ToolCapability.NAVIGATE)

    override fun createController(context: ToolContext): ToolController = HandController

    private object HandController : ToolController {
        override fun onPointer(event: CanvasPointerEvent) = Unit
    }
}
