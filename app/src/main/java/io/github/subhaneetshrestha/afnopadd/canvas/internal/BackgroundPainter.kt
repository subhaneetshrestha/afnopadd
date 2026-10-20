package io.github.subhaneetshrestha.afnopadd.canvas.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.subhaneetshrestha.afnopadd.canvas.Background
import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Paints the sheet background. Grid and dot spacing is a power of ten times 20 world units,
 * chosen so lines sit 20 to 200 px apart on screen: zooming in or out swaps the spacing bucket
 * instead of letting lines crowd or spread, and everything is drawn on pixel centres in screen space.
 */
internal object BackgroundPainter {

    private val SheetColor = Color.White
    private val LineColor = Color(0xFFD0D0D0)
    private const val BASE_SPACING_WORLD = 20.0
    private const val MIN_SPACING_PX = 20.0
    private const val MAX_SPACING_PX = 200.0

    fun DrawScope.drawBackground(background: Background, camera: Camera, viewport: Viewport) {
        drawRect(SheetColor)
        if (background == Background.PLAIN || viewport.widthPx == 0 || viewport.heightPx == 0) return

        val spacingWorld = spacingWorldUnits(camera.zoom)
        val topLeft = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0))
        val firstX = floor(topLeft.x / spacingWorld) * spacingWorld
        val firstY = floor(topLeft.y / spacingWorld) * spacingWorld

        val xs = generateSequence(firstX) { it + spacingWorld }
            .map { CanvasMath.worldToScreen(camera, viewport, WorldPoint(it, 0.0)).x }
            .takeWhile { it <= viewport.widthPx }
            .map { it.roundToInt() + 0.5f }
            .toList()
        val ys = generateSequence(firstY) { it + spacingWorld }
            .map { CanvasMath.worldToScreen(camera, viewport, WorldPoint(0.0, it)).y }
            .takeWhile { it <= viewport.heightPx }
            .map { it.roundToInt() + 0.5f }
            .toList()

        when (background) {
            Background.GRID -> {
                val height = viewport.heightPx.toFloat()
                val width = viewport.widthPx.toFloat()
                xs.forEach { x -> drawLine(LineColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f) }
                ys.forEach { y -> drawLine(LineColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f) }
            }
            Background.DOTS -> xs.forEach { x -> ys.forEach { y -> drawCircle(LineColor, radius = 1.5f, center = Offset(x, y)) } }
            Background.PLAIN -> Unit
        }
    }

    /** The grid spacing in world units for [zoom], keeping the on-screen spacing in [MIN_SPACING_PX, MAX_SPACING_PX). */
    fun spacingWorldUnits(zoom: Double): Double {
        var spacingWorld = BASE_SPACING_WORLD
        var spacingPx = spacingWorld * zoom
        while (spacingPx < MIN_SPACING_PX) {
            spacingWorld *= 10
            spacingPx *= 10
        }
        while (spacingPx >= MAX_SPACING_PX) {
            spacingWorld /= 10
            spacingPx /= 10
        }
        return spacingWorld
    }
}
