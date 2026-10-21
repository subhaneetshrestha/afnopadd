package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.hypot

/**
 * Where a selection's transform handles are, shared by the canvas that draws them and the select
 * tool that grabs them. Offsets are in dp and converted through the zoom, so the handles stay the
 * same size and distance from the selection on screen at every zoom. The scale handle is at the
 * bottom-right corner of the padded outline; the rotate handle floats above its top centre.
 */
object SelectionHandles {

    enum class Handle { SCALE, ROTATE }

    /** Gap between the selection's bounds and its dashed outline. */
    const val PAD_DP = 6f
    /** How far above the outline the rotate handle floats. */
    const val ROTATE_OFFSET_DP = 28f
    /** Drawn radius of a handle. */
    const val RADIUS_DP = 7f
    /** Grab radius of a handle, larger than it looks so a finger finds it. */
    const val TOUCH_RADIUS_DP = 22f

    /** [handle]'s world position for a selection of [bounds] at [zoom] (screen pixels per world unit), [density] pixels per dp. */
    fun position(handle: Handle, bounds: WorldRect, zoom: Double, density: Float): WorldPoint {
        val pad = PAD_DP * density / zoom
        return when (handle) {
            Handle.SCALE -> WorldPoint(bounds.right + pad, bounds.bottom + pad)
            Handle.ROTATE -> WorldPoint((bounds.left + bounds.right) / 2, bounds.top - pad - ROTATE_OFFSET_DP * density / zoom)
        }
    }

    /** The handle within reach of [point], if any; the rotate handle wins where both are. */
    fun handleAt(point: WorldPoint, bounds: WorldRect, zoom: Double, density: Float): Handle? =
        listOf(Handle.ROTATE, Handle.SCALE).firstOrNull { handle ->
            val at = position(handle, bounds, zoom, density)
            hypot(point.x - at.x, point.y - at.y) * zoom <= TOUCH_RADIUS_DP * density
        }
}
