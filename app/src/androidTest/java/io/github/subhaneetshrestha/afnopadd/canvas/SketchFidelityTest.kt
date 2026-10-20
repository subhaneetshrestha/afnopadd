package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.canvas.internal.DryLayer
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sin

/** A stroke small enough to be drawn as a sketch must look like its Ink mesh at that size. */
@RunWith(AndroidJUnit4::class)
class SketchFidelityTest {

    private val count = 60
    private val xs = FloatArray(count) { it * 2.5f }
    private val ys = FloatArray(count) { (sin(it / 6.0) * 20).toFloat() }
    private val stroke = StrokeElement(
        id = ElementId("01JAX9Q8K0TZ3M4N5P6R7S0042"),
        anchor = WorldPoint(0.0, 0.0),
        frameLevel = 0,
        z = FractionalIndex.first(),
        bounds = WorldRect(-10.0, -30.0, 160.0, 30.0),
        version = 1, versionNonce = 1, createdAt = 1L, updatedAt = 1L,
        brush = BrushSpec("ink:marker", 16f, 0xFF000000, 1f, 0.025f),
        points = StrokePoints.of(xs, ys, FloatArray(count) { 1f }, FloatArray(count), FloatArray(count), FloatArray(count) { it * 8f }),
    )

    private class Coverage(val total: Double, val centerX: Double, val centerY: Double)

    private fun coverage(bitmap: Bitmap): Coverage {
        var total = 0.0
        var sx = 0.0
        var sy = 0.0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val c = bitmap.getPixel(x, y)
            val ink = (255 - (Color.red(c) + Color.green(c) + Color.blue(c)) / 3) / 255.0
            total += ink
            sx += ink * x
            sy += ink * y
        }
        return Coverage(total, sx / total, sy / total)
    }

    @Test
    fun aSketchCoversTheSameInkAsTheMesh() {
        // At 0.15x the stroke spans about 25 px, under the sketch threshold.
        val scale = 0.15
        val region = WorldRect(-40.0, -100.0, 200.0, 100.0)
        val mesh = SoftwareRenderer().renderBitmap(ElementSnapshot(mapOf(stroke.id to stroke)), region, scale)

        val sketch = Bitmap.createBitmap(mesh.width, mesh.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sketch).apply { drawColor(Color.WHITE) }
        val viewport = Viewport(mesh.width, mesh.height)
        val camera = Camera(WorldPoint((region.left + region.right) / 2, (region.top + region.bottom) / 2), zoom = scale)
        val layer = DryLayer()
        layer.draw(canvas, listOf(stroke), emptySet(), camera, viewport)
        assertEquals("drawn as a sketch", 1, layer.sketchCount)

        val a = coverage(mesh)
        val b = coverage(sketch)
        Log.i("SketchFidelityTest", "mesh ink ${"%.1f".format(a.total)} at (${"%.1f".format(a.centerX)}, ${"%.1f".format(a.centerY)}); " +
            "sketch ink ${"%.1f".format(b.total)} at (${"%.1f".format(b.centerX)}, ${"%.1f".format(b.centerY)})")
        assertTrue("ink differs: mesh ${a.total}, sketch ${b.total}", b.total in a.total * 0.7..a.total * 1.3)
        assertTrue("sketch is offset", kotlin.math.abs(a.centerX - b.centerX) < 1.0 && kotlin.math.abs(a.centerY - b.centerY) < 1.0)
    }
}
