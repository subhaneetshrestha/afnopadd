package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * A uniform scale and clockwise turn about [pivot], applied while drawing a tool's preview. Scaling
 * or rotating a selection previews its untouched elements under this one transform, so their cached
 * meshes are reused every frame; the transformed geometry is built only when the gesture commits.
 */
data class PreviewTransform(val pivot: WorldPoint, val scale: Double = 1.0, val rotationDeg: Double = 0.0) {

    fun apply(point: WorldPoint): WorldPoint {
        val radians = Math.toRadians(rotationDeg)
        val dx = (point.x - pivot.x) * scale
        val dy = (point.y - pivot.y) * scale
        return WorldPoint(pivot.x + dx * cos(radians) - dy * sin(radians), pivot.y + dx * sin(radians) + dy * cos(radians))
    }

    /** The box around [rect]'s corners once transformed. */
    fun apply(rect: WorldRect): WorldRect {
        val corners = listOf(
            WorldPoint(rect.left, rect.top), WorldPoint(rect.right, rect.top),
            WorldPoint(rect.right, rect.bottom), WorldPoint(rect.left, rect.bottom),
        ).map(::apply)
        return WorldRect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
    }
}
