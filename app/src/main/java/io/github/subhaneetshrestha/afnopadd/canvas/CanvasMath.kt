package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.pow

/**
 * Pure conversions between world, frame-local and screen coordinates. All arithmetic is done in
 * double precision; the only float values are produced at the very end by [FrameTransform.asFloat],
 * by which point translations are screen-pixel sized, so nothing jitters at 100,000x.
 */
object CanvasMath {

    fun worldToScreen(camera: Camera, viewport: Viewport, point: WorldPoint): ScreenPoint = ScreenPoint(
        x = (point.x - camera.center.x) * camera.zoom + viewport.centerX,
        y = (point.y - camera.center.y) * camera.zoom + viewport.centerY,
    )

    fun screenToWorld(camera: Camera, viewport: Viewport, point: ScreenPoint): WorldPoint = WorldPoint(
        x = (point.x - viewport.centerX) / camera.zoom + camera.center.x,
        y = (point.y - viewport.centerY) / camera.zoom + camera.center.y,
    )

    /** The affine map from an element's frame-local coordinates to screen pixels. */
    fun frameToScreen(camera: Camera, viewport: Viewport, anchor: WorldPoint, frameLevel: Int): FrameTransform =
        FrameTransform(
            scale = camera.zoom * FrameLevels.worldUnitsPerLocalUnit(frameLevel),
            translateX = (anchor.x - camera.center.x) * camera.zoom + viewport.centerX,
            translateY = (anchor.y - camera.center.y) * camera.zoom + viewport.centerY,
        )

    /** The map from frame-local units to pixels of an off-screen region whose top-left is [region]'s top-left. */
    fun frameToRegion(region: WorldRect, scalePxPerUnit: Double, anchor: WorldPoint, frameLevel: Int): FrameTransform =
        FrameTransform(
            scale = scalePxPerUnit * FrameLevels.worldUnitsPerLocalUnit(frameLevel),
            translateX = (anchor.x - region.left) * scalePxPerUnit,
            translateY = (anchor.y - region.top) * scalePxPerUnit,
        )

    /** Linear in the centre, geometric in the zoom, so an animated zoom feels uniform. */
    fun interpolate(from: Camera, to: Camera, fraction: Double): Camera {
        val t = fraction.coerceIn(0.0, 1.0)
        return Camera(
            center = WorldPoint(
                x = from.center.x + (to.center.x - from.center.x) * t,
                y = from.center.y + (to.center.y - from.center.y) * t,
            ),
            zoom = from.zoom * (to.zoom / from.zoom).pow(t),
        )
    }
}

/** Scale-and-translate map from frame-local units to screen pixels (no rotation in v1). */
data class FrameTransform(val scale: Double, val translateX: Double, val translateY: Double) {

    fun apply(local: LocalPoint): ScreenPoint = ScreenPoint(local.x * scale + translateX, local.y * scale + translateY)

    fun asFloat(): FloatAffine = FloatAffine(scale.toFloat(), translateX.toFloat(), translateY.toFloat())
}

/** The float form handed to `android.graphics.Matrix` or Compose at draw time. */
data class FloatAffine(val scale: Float, val translateX: Float, val translateY: Float)
