package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.canvas.internal.HitGeometry
import io.github.subhaneetshrestha.afnopadd.canvas.internal.InkStrokeHitTest
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.pow

/** Answers "what is under this pointer?" against the elements' drawn geometry, not just their bounds. */
interface HitTester {
    /** The top-most element within [tolerancePx] screen pixels of [point] at [camera]'s zoom. */
    fun elementAt(point: WorldPoint, tolerancePx: Float, camera: Camera): ElementId?

    /** Elements whose bounds lie entirely inside [rect], back to front (a marquee selection). */
    fun elementsIn(rect: WorldRect): List<ElementId>

    /** Elements whose drawn geometry comes within [radius] world units of the segment [from]-[to], front to back. */
    fun elementsAlong(from: WorldPoint, to: WorldPoint, radius: Double): List<ElementId>
}

/**
 * Exact test of one stroke's ink against a padded segment. Arguments are in the stroke's own frame
 * units, where its geometry is stored, so precision does not depend on how deep the stroke sits.
 */
fun interface StrokeHitTest {
    fun touches(stroke: StrokeElement, from: LocalPoint, to: LocalPoint, radius: Float): Boolean

    companion object {
        /**
         * The stroke as its centre line widened by half the brush size. Pure arithmetic, so it runs
         * in JVM tests; slightly generous where pressure made the ink thinner than the brush size.
         */
        val Centerline = StrokeHitTest { stroke, from, to, radius ->
            val points = stroke.points
            val reach = radius + stroke.brush.size / 2.0
            val fx = from.x.toDouble()
            val fy = from.y.toDouble()
            val tx = to.x.toDouble()
            val ty = to.y.toDouble()
            if (points.count == 1) {
                return@StrokeHitTest HitGeometry.pointToSegment(points.x(0).toDouble(), points.y(0).toDouble(), fx, fy, tx, ty) <= reach
            }
            (1 until points.count).any { i ->
                HitGeometry.segmentToSegment(
                    points.x(i - 1).toDouble(), points.y(i - 1).toDouble(), points.x(i).toDouble(), points.y(i).toDouble(),
                    fx, fy, tx, ty,
                ) <= reach
            }
        }

        /** Intersection with the tessellated Ink mesh, exactly as the stroke is drawn. Needs Ink's native library. */
        fun ink(): StrokeHitTest = InkStrokeHitTest()
    }
}

/**
 * [HitTester] for one sheet. Candidates come from the shared [ElementIndex] (synced to the sheet's
 * current snapshot on every call); strokes are then tested with [strokeHitTest], shapes against
 * their outline (shapes have no fill in v1), and images and other elements against their bounds.
 */
class SheetHitTester(
    private val sheet: Sheet,
    private val index: ElementIndex = ElementIndex(),
    private val strokeHitTest: StrokeHitTest = StrokeHitTest.Centerline,
) : HitTester {

    override fun elementAt(point: WorldPoint, tolerancePx: Float, camera: Camera): ElementId? =
        elementsAlong(point, point, tolerancePx / camera.zoom).firstOrNull()

    override fun elementsIn(rect: WorldRect): List<ElementId> {
        val snapshot = sheet.snapshot.value
        index.sync(snapshot)
        return index.query(rect)
            .mapNotNull { snapshot.elements[it] }
            .filter { it.bounds.left >= rect.left && it.bounds.right <= rect.right && it.bounds.top >= rect.top && it.bounds.bottom <= rect.bottom }
            .sortedBy { it.z }
            .map { it.id }
    }

    override fun elementsAlong(from: WorldPoint, to: WorldPoint, radius: Double): List<ElementId> {
        val snapshot = sheet.snapshot.value
        index.sync(snapshot)
        val reach = WorldRect(
            left = minOf(from.x, to.x) - radius,
            top = minOf(from.y, to.y) - radius,
            right = maxOf(from.x, to.x) + radius,
            bottom = maxOf(from.y, to.y) + radius,
        )
        return index.query(reach)
            .mapNotNull { snapshot.elements[it] }
            .filter { touches(it, from, to, radius) }
            .sortedByDescending { it.z }
            .map { it.id }
    }

    private fun touches(element: Element, from: WorldPoint, to: WorldPoint, radius: Double): Boolean = when (element) {
        is StrokeElement -> strokeHitTest.touches(
            element,
            FrameLevels.worldToLocal(from, element.anchor, element.frameLevel),
            FrameLevels.worldToLocal(to, element.anchor, element.frameLevel),
            (radius * 10.0.pow(element.frameLevel)).toFloat(),
        )
        is ShapeElement -> shapeTouches(element, from, to, radius)
        else -> HitGeometry.segmentToRect(
            from.x, from.y, to.x, to.y,
            element.bounds.left, element.bounds.top, element.bounds.right, element.bounds.bottom,
        ) <= radius
    }

    private fun shapeTouches(shape: ShapeElement, from: WorldPoint, to: WorldPoint, radius: Double): Boolean {
        val a = FrameLevels.worldToLocal(from, shape.anchor, shape.frameLevel)
        val b = FrameLevels.worldToLocal(to, shape.anchor, shape.frameLevel)
        val reach = radius * 10.0.pow(shape.frameLevel) + shape.style.strokeWidth / 2.0
        // The same outline the painter draws, arrowheads included.
        return ShapeOutline.segments(shape.shape, shape.geometry.start, shape.geometry.end, shape.style.strokeWidth, shape.rotationDeg).any { s ->
            HitGeometry.segmentToSegment(
                s.start.x.toDouble(), s.start.y.toDouble(), s.end.x.toDouble(), s.end.y.toDouble(),
                a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(),
            ) <= reach
        }
    }
}
