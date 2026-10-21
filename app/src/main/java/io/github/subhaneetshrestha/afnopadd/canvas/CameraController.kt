package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the camera and applies the gestures the canvas and tools translate into: panning by screen
 * pixels, zooming about a screen pivot, and fitting a world rectangle. Zoom is always clamped to
 * [Camera.MIN_ZOOM]..[Camera.MAX_ZOOM]. Animation is driven by the UI layer with
 * [CanvasMath.interpolate] and [set].
 */
class CameraController(initial: Camera = Camera.DEFAULT) {

    private val _camera = MutableStateFlow(initial.clamped())
    val camera: StateFlow<Camera> = _camera.asStateFlow()

    fun set(camera: Camera) {
        _camera.value = camera.clamped()
    }

    /** Drags the content by ([dxPx], [dyPx]); the centre moves the opposite way in world units. */
    fun panBy(dxPx: Double, dyPx: Double) {
        val current = _camera.value
        _camera.value = current.copy(
            center = WorldPoint(current.center.x - dxPx / current.zoom, current.center.y - dyPx / current.zoom),
        )
    }

    /** Multiplies the zoom by [factor] while the world point under [pivot] stays under it. */
    fun zoomBy(factor: Double, pivot: ScreenPoint, viewport: Viewport) {
        val current = _camera.value
        val newZoom = (current.zoom * factor).coerceIn(Camera.MIN_ZOOM, Camera.MAX_ZOOM)
        val worldAtPivot = CanvasMath.screenToWorld(current, viewport, pivot)
        _camera.value = Camera(
            center = WorldPoint(
                x = worldAtPivot.x - (pivot.x - viewport.centerX) / newZoom,
                y = worldAtPivot.y - (pivot.y - viewport.centerY) / newZoom,
            ),
            zoom = newZoom,
        )
    }

    /** Centres on [bounds] at the largest zoom that shows all of it inside the padded viewport. */
    fun fit(bounds: WorldRect, viewport: Viewport, paddingPx: Double) {
        _camera.value = CanvasMath.fitCamera(bounds, viewport, paddingPx)
    }

    private fun Camera.clamped(): Camera =
        if (zoom in Camera.MIN_ZOOM..Camera.MAX_ZOOM) this else copy(zoom = zoom.coerceIn(Camera.MIN_ZOOM, Camera.MAX_ZOOM))
}
