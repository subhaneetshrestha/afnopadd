package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.canvas.internal.DeepZoom
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepZoomTest {

    private val viewport = Viewport(1_080, 2_400)

    /** The frame transform of a stroke anchored at the world origin, frame level 0, seen at [zoom] centred on ([cx], [cy]). */
    private fun transform(zoom: Double, cx: Double, cy: Double) = FrameTransform(
        scale = zoom,
        translateX = (0.0 - cx) * zoom + viewport.centerX,
        translateY = (0.0 - cy) * zoom + viewport.centerY,
    )

    @Test
    fun `strokes keep their mesh up to 100x, get a finer one to 10,000x, and a clipped one beyond`() {
        assertEquals(DeepZoom.Base, DeepZoom.variantFor(transform(100.0, 0.0, 0.0), viewport))
        assertEquals(DeepZoom.Fine, DeepZoom.variantFor(transform(101.0, 0.0, 0.0), viewport))
        assertEquals(DeepZoom.Fine, DeepZoom.variantFor(transform(10_000.0, 0.0, 0.0), viewport))
        assertTrue(DeepZoom.variantFor(transform(10_001.0, 0.0, 0.0), viewport) is DeepZoom.Clipped)
    }

    @Test
    fun `a clipped variant draws every point exactly where the plain transform would`() {
        val t = transform(123_456.0, 101.9987, 0.000_4)
        val clipped = DeepZoom.variantFor(t, viewport) as DeepZoom.Clipped
        val draw = DeepZoom.drawTransform(t, clipped)

        for (p in listOf(LocalPoint(101.998f, 0f), LocalPoint(101.9995f, 0.001f), LocalPoint(102.001f, -0.002f))) {
            val local = DeepZoom.rebased(p, clipped)
            val direct = t.apply(p)
            assertEquals(direct.x, draw.scale * local.x + draw.translateX, 0.05)
            assertEquals(direct.y, draw.scale * local.y + draw.translateY, 0.05)
        }
        // The rebased drawing works in small numbers: a moderate scale and a translation near the screen.
        assertTrue(draw.scale in 1.0..10.0)
        assertTrue(kotlin.math.abs(draw.translateX) < 1e5 && kotlin.math.abs(draw.translateY) < 1e5)
    }

    @Test
    fun `the clipped variant is reused while the view stays in its cell and covers the whole view`() {
        val zoom = 200_000.0
        val a = DeepZoom.variantFor(transform(zoom, 50.0001, 3.0), viewport) as DeepZoom.Clipped
        val b = DeepZoom.variantFor(transform(zoom, 50.0001 + 1.0 / zoom, 3.0), viewport) as DeepZoom.Clipped
        assertEquals("a one-pixel pan keeps the variant", a, b)

        // The view, in the stroke's units, lies inside the clip rectangle.
        val left = 50.0001 - viewport.widthPx / 2 / zoom
        val right = 50.0001 + viewport.widthPx / 2 / zoom
        val top = 3.0 - viewport.heightPx / 2 / zoom
        val bottom = 3.0 + viewport.heightPx / 2 / zoom
        assertTrue(a.clipLeft <= left && a.clipRight >= right && a.clipTop <= top && a.clipBottom >= bottom)
    }

    @Test
    fun `clipping keeps the runs of points whose ink can reach the view, with their neighbours`() {
        // A straight run of 100 points 1 unit apart, crossing the clip window near points 40 to 42.
        val points = StrokePoints.of(
            FloatArray(100) { it.toFloat() }, FloatArray(100), FloatArray(100) { 1f }, FloatArray(100), FloatArray(100), FloatArray(100) { it * 8f },
        )

        val runs = DeepZoom.keptRuns(points, left = 40.5, top = -0.5, right = 41.5, bottom = 0.5, margin = 0.2)

        assertEquals(1, runs.size)
        val run = runs.single()
        assertTrue("$run covers the crossing", 40 in run && 42 in run)
        assertTrue("$run is short", run.last - run.first < 10)
    }

    @Test
    fun `a stroke that leaves and comes back into view gives two runs`() {
        // Out along x from 0 to 50, then back from 50 to 0, 10 units lower.
        val xs = FloatArray(102) { if (it < 51) it.toFloat() else (101 - it).toFloat() }
        val ys = FloatArray(102) { if (it < 51) 0f else 10f }
        val points = StrokePoints.of(xs, ys, FloatArray(102) { 1f }, FloatArray(102), FloatArray(102), FloatArray(102) { it * 8f })

        val runs = DeepZoom.keptRuns(points, left = 10.0, top = -1.0, right = 12.0, bottom = 11.0, margin = 0.5)

        assertEquals(2, runs.size)
    }
}
