package io.github.subhaneetshrestha.afnopadd.tools.select

import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.canvas.ShapeOutline
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.NoteElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeGeometry
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.UnknownElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Uniform scale and rotation of elements about a world pivot (SPEC-drawing-tools decision 7).
 * Element geometry is stored relative to the anchor, so each transform moves the anchor about the
 * pivot and then changes the local geometry about the anchor; frame levels stay as they are.
 *
 * - Strokes: points (and brush size and epsilon, when scaling) change; rotation also turns each
 *   sample's pen orientation.
 * - Lines and arrows: both endpoints change; they never carry a rotation of their own.
 * - Rectangles, ellipses and images turn about their own centre by `rotationDeg`, so rotation moves
 *   that centre around the pivot and adds to the angle. An image's anchor is its top-left corner
 *   before it is turned.
 * - Notes and unknown kinds keep their geometry; they are returned unchanged, since their shape is
 *   not known well enough to transform.
 *
 * Positive angles are clockwise on screen (y grows downwards). Callers bump `version`.
 */
object Transforms {

    fun scaled(element: Element, pivot: WorldPoint, factor: Double): Element {
        val f = factor.toFloat()
        val anchor = WorldPoint(pivot.x + (element.anchor.x - pivot.x) * factor, pivot.y + (element.anchor.y - pivot.y) * factor)
        // A uniform positive scale maps the bounding box onto the new one exactly.
        val bounds = WorldRect(
            pivot.x + (element.bounds.left - pivot.x) * factor,
            pivot.y + (element.bounds.top - pivot.y) * factor,
            pivot.x + (element.bounds.right - pivot.x) * factor,
            pivot.y + (element.bounds.bottom - pivot.y) * factor,
        )
        return when (element) {
            is StrokeElement -> element.copy(
                anchor = anchor,
                bounds = bounds,
                points = element.points.mapPoints { x, y -> LocalPoint(x * f, y * f) },
                brush = element.brush.copy(size = element.brush.size * f, epsilon = element.brush.epsilon * f),
            )
            is ShapeElement -> element.copy(
                anchor = anchor,
                bounds = bounds,
                geometry = ShapeGeometry(element.geometry.start.times(f), element.geometry.end.times(f)),
                style = element.style.copy(strokeWidth = element.style.strokeWidth * f),
            )
            is ImageElement -> element.copy(
                anchor = anchor,
                bounds = bounds,
                size = LocalSize(element.size.width * f, element.size.height * f),
                crop = element.crop?.let { LocalRect(it.left * f, it.top * f, it.right * f, it.bottom * f) },
            )
            is NoteElement, is UnknownElement -> element
        }
    }

    fun rotated(element: Element, pivot: WorldPoint, degrees: Double): Element {
        val radians = Math.toRadians(degrees)
        val cos = cos(radians)
        val sin = sin(radians)
        fun turnAboutPivot(p: WorldPoint) = WorldPoint(
            pivot.x + (p.x - pivot.x) * cos - (p.y - pivot.y) * sin,
            pivot.y + (p.x - pivot.x) * sin + (p.y - pivot.y) * cos,
        )
        fun turnLocal(p: LocalPoint) = LocalPoint((p.x * cos - p.y * sin).toFloat(), (p.x * sin + p.y * cos).toFloat())

        return when (element) {
            is StrokeElement -> {
                val points = element.points.mapPoints(orientationDelta = radians.toFloat()) { x, y -> turnLocal(LocalPoint(x, y)) }
                val anchor = turnAboutPivot(element.anchor)
                element.copy(anchor = anchor, points = points, bounds = strokeBounds(anchor, element.frameLevel, points, element.brush.size))
            }
            is ShapeElement -> when (element.shape) {
                ShapeKind.LINE, ShapeKind.ARROW -> {
                    val anchor = turnAboutPivot(element.anchor)
                    val geometry = ShapeGeometry(turnLocal(element.geometry.start), turnLocal(element.geometry.end))
                    element.copy(anchor = anchor, geometry = geometry, bounds = shapeBounds(anchor, element.frameLevel, element.copy(geometry = geometry)))
                }
                ShapeKind.RECTANGLE, ShapeKind.ELLIPSE -> {
                    val g = element.geometry
                    val centreLocal = LocalPoint((g.start.x + g.end.x) / 2, (g.start.y + g.end.y) / 2)
                    val anchor = anchorForCentre(turnAboutPivot(centreOf(element.anchor, element.frameLevel, centreLocal)), element.frameLevel, centreLocal)
                    val turned = element.copy(anchor = anchor, rotationDeg = normalised(element.rotationDeg + degrees))
                    turned.copy(bounds = shapeBounds(anchor, element.frameLevel, turned))
                }
            }
            is ImageElement -> {
                val centreLocal = LocalPoint(element.size.width / 2, element.size.height / 2)
                val anchor = anchorForCentre(turnAboutPivot(centreOf(element.anchor, element.frameLevel, centreLocal)), element.frameLevel, centreLocal)
                val rotation = normalised(element.rotationDeg + degrees)
                element.copy(anchor = anchor, rotationDeg = rotation, bounds = imageBounds(anchor, element.frameLevel, element.size, rotation, element.crop))
            }
            is NoteElement, is UnknownElement -> element
        }
    }

    /**
     * The world bounds of the visible part of an image of [size] whose corner is [anchor], turned
     * [rotationDeg] about its centre: the [crop] rectangle when there is one, else the whole image.
     */
    fun imageBounds(anchor: WorldPoint, frameLevel: Int, size: LocalSize, rotationDeg: Float, crop: LocalRect? = null): WorldRect {
        val unit = FrameLevels.worldUnitsPerLocalUnit(frameLevel)
        val radians = Math.toRadians(rotationDeg.toDouble())
        val cx = size.width / 2.0
        val cy = size.height / 2.0
        val visible = crop ?: LocalRect(0f, 0f, size.width, size.height)
        val corners = listOf(
            visible.left.toDouble() to visible.top.toDouble(),
            visible.right.toDouble() to visible.top.toDouble(),
            visible.right.toDouble() to visible.bottom.toDouble(),
            visible.left.toDouble() to visible.bottom.toDouble(),
        ).map { (x, y) ->
            val dx = x - cx
            val dy = y - cy
            (cx + dx * cos(radians) - dy * sin(radians)) to (cy + dx * sin(radians) + dy * cos(radians))
        }
        return WorldRect(
            anchor.x + corners.minOf { it.first } * unit,
            anchor.y + corners.minOf { it.second } * unit,
            anchor.x + corners.maxOf { it.first } * unit,
            anchor.y + corners.maxOf { it.second } * unit,
        )
    }

    private fun shapeBounds(anchor: WorldPoint, frameLevel: Int, shape: ShapeElement): WorldRect {
        val local = ShapeOutline.localBounds(shape.shape, shape.geometry.start, shape.geometry.end, shape.style.strokeWidth, shape.rotationDeg)
        val unit = FrameLevels.worldUnitsPerLocalUnit(frameLevel)
        return WorldRect(anchor.x + local.left * unit, anchor.y + local.top * unit, anchor.x + local.right * unit, anchor.y + local.bottom * unit)
    }

    private fun strokeBounds(anchor: WorldPoint, frameLevel: Int, points: StrokePoints, brushSize: Float): WorldRect {
        val unit = FrameLevels.worldUnitsPerLocalUnit(frameLevel)
        val pad = brushSize / 2.0
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (i in 0 until points.count) {
            minX = minOf(minX, points.x(i).toDouble()); maxX = maxOf(maxX, points.x(i).toDouble())
            minY = minOf(minY, points.y(i).toDouble()); maxY = maxOf(maxY, points.y(i).toDouble())
        }
        if (points.count == 0) { minX = 0.0; minY = 0.0; maxX = 0.0; maxY = 0.0 }
        return WorldRect(anchor.x + (minX - pad) * unit, anchor.y + (minY - pad) * unit, anchor.x + (maxX + pad) * unit, anchor.y + (maxY + pad) * unit)
    }

    private fun centreOf(anchor: WorldPoint, frameLevel: Int, centreLocal: LocalPoint): WorldPoint {
        val unit = FrameLevels.worldUnitsPerLocalUnit(frameLevel)
        return WorldPoint(anchor.x + centreLocal.x * unit, anchor.y + centreLocal.y * unit)
    }

    private fun anchorForCentre(centre: WorldPoint, frameLevel: Int, centreLocal: LocalPoint): WorldPoint {
        val unit = FrameLevels.worldUnitsPerLocalUnit(frameLevel)
        return WorldPoint(centre.x - centreLocal.x * unit, centre.y - centreLocal.y * unit)
    }

    /** Into (-180, 180], so a full turn comes back to exactly 0 and stays unrotated on disk. */
    private fun normalised(degrees: Double): Float {
        var d = degrees % 360.0
        if (d <= -180.0) d += 360.0
        if (d > 180.0) d -= 360.0
        return if (kotlin.math.abs(d) < 1e-4) 0f else d.toFloat()
    }

    private fun LocalPoint.times(f: Float) = LocalPoint(x * f, y * f)

    /**
     * A copy with every sample's position mapped and, for rotation, its pen orientation turned and
     * wrapped into [0, 2π) as Ink requires; a negative orientation means "not recorded" and stays.
     */
    private fun StrokePoints.mapPoints(orientationDelta: Float = 0f, transform: (Float, Float) -> LocalPoint): StrokePoints {
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        for (i in 0 until count) {
            val p = transform(x(i), y(i))
            xs[i] = p.x
            ys[i] = p.y
        }
        val twoPi = (2 * PI).toFloat()
        return StrokePoints.of(
            x = xs,
            y = ys,
            pressure = FloatArray(count) { pressure(it) },
            tiltRad = FloatArray(count) { tiltRad(it) },
            orientationRad = FloatArray(count) {
                val o = orientationRad(it)
                if (o < 0f || orientationDelta == 0f) o else ((o + orientationDelta) % twoPi + twoPi) % twoPi
            },
            timeMillis = FloatArray(count) { timeMillis(it) },
        )
    }
}
