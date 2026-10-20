package io.github.subhaneetshrestha.afnopadd.canvas.internal

import androidx.ink.geometry.ImmutableTriangle
import androidx.ink.geometry.ImmutableVec
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeHitTest
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import kotlin.math.hypot

/**
 * Tests the padded segment against the stroke's tessellated Ink mesh, so a hit means the eraser
 * really touched ink. Meshes come from a private [StrokeCache]; a stroke is tessellated the first
 * time the eraser reaches its bounds and reused for the rest of the gesture.
 *
 * The padded segment is passed as two triangles with explicit corners rather than as
 * `ImmutableParallelogram.fromSegmentAndPadding`: in Ink 1.0.0 that parallelogram reports coverage
 * far from where it lies (a horizontal one 6 units clear of a stroke covered 56 % of it), which
 * looks like its rotation being misread, while triangles and boxes behave.
 */
internal class InkStrokeHitTest(private val cache: StrokeCache = StrokeCache(maxBytes = 8L * 1024 * 1024)) : StrokeHitTest {

    override fun touches(stroke: StrokeElement, from: LocalPoint, to: LocalPoint, radius: Float): Boolean {
        val mesh = synchronized(cache) { cache.strokeFor(stroke) }.shape
        val length = hypot(to.x - from.x, to.y - from.y)
        // Unit direction along the segment (any direction for a tap) and its normal, both scaled by the radius.
        val dx = if (length > 0f) (to.x - from.x) / length * radius else radius
        val dy = if (length > 0f) (to.y - from.y) / length * radius else 0f
        val a = ImmutableVec(from.x - dx - dy, from.y - dy + dx)
        val b = ImmutableVec(to.x + dx - dy, to.y + dy + dx)
        val c = ImmutableVec(to.x + dx + dy, to.y + dy - dx)
        val d = ImmutableVec(from.x - dx + dy, from.y - dy - dx)
        return mesh.computeCoverageIsGreaterThan(ImmutableTriangle(a, b, c), 0f) ||
            mesh.computeCoverageIsGreaterThan(ImmutableTriangle(a, c, d), 0f)
    }
}
