package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.WorldPoint

/**
 * Where the user is looking: the world point at the viewport centre and the zoom in screen pixels
 * per world unit. Doubles throughout, because a sheet spans eight orders of magnitude of zoom.
 */
data class Camera(val center: WorldPoint, val zoom: Double) {

    init {
        require(zoom > 0.0 && zoom.isFinite()) { "Zoom must be a positive number, got $zoom" }
    }

    companion object {
        const val MIN_ZOOM = 0.001
        const val MAX_ZOOM = 100_000.0
        val DEFAULT = Camera(center = WorldPoint(0.0, 0.0), zoom = 1.0)
    }
}

/** The drawing surface size in pixels. */
data class Viewport(val widthPx: Int, val heightPx: Int) {
    val centerX: Double get() = widthPx / 2.0
    val centerY: Double get() = heightPx / 2.0
}

/** A position on the drawing surface in pixels; kept in double precision until it reaches Compose. */
data class ScreenPoint(val x: Double, val y: Double)
