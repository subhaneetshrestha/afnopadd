package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.random.Random

/**
 * docs/product/spec.md success criterion 4 and Task 25: a stroke drawn at 1x and viewed at 100,000x keeps a
 * smooth edge exactly where its geometry puts it, and does not jitter against its neighbours as
 * the view moves.
 */
@RunWith(AndroidJUnit4::class)
class DeepZoomRenderingTest {

    @get:Rule
    val rule = createComposeRule()

    private val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(81))

    /**
     * A straight marker stroke from [from] to [to] (world units) drawn in frame [level], sampled
     * every [step] world units; [size] is in the frame's units, as a stroke drawn at that zoom has it.
     */
    private fun line(from: WorldPoint, to: WorldPoint, size: Float, step: Double, z: String, level: Int = 0): StrokeElement {
        val count = (maxOf(abs(to.x - from.x), abs(to.y - from.y)) / step).toInt() + 1
        val perWorld = Math.pow(10.0, level.toDouble())
        val half = size / 2.0 / perWorld
        return StrokeElement(
            id = ElementId(ulid.next()), anchor = from, frameLevel = level, z = FractionalIndex(z),
            bounds = WorldRect(minOf(from.x, to.x) - half, minOf(from.y, to.y) - half, maxOf(from.x, to.x) + half, maxOf(from.y, to.y) + half),
            version = 1, versionNonce = 1, createdAt = 1L, updatedAt = 1L,
            brush = BrushSpec("ink:marker", size, 0xFF000000, 1f, 0.025f),
            points = StrokePoints.of(
                x = FloatArray(count) { ((to.x - from.x) * perWorld * it / (count - 1)).toFloat() },
                y = FloatArray(count) { ((to.y - from.y) * perWorld * it / (count - 1)).toFloat() },
                pressure = FloatArray(count) { 1f },
                tiltRad = FloatArray(count),
                orientationRad = FloatArray(count),
                timeMillis = FloatArray(count) { it * 8f },
            ),
        )
    }

    private fun Bitmap.isDark(x: Int, y: Int) = getPixel(x, y).let { (Color.red(it) + Color.green(it) + Color.blue(it)) / 3 < 128 }

    /** The first paper pixel right of x = 300 on [row]: the end of stroke A's cap. */
    private fun Bitmap.capEdge(row: Int): Int = (300 until width).firstOrNull { !isDark(it, row) } ?: width

    /** The middle of the dark band after the cap edge on [row]: stroke B. */
    private fun Bitmap.neighbourCentre(row: Int): Double {
        val edge = capEdge(row)
        val dark = (edge until width).filter { isDark(it, row) }
        return if (dark.isEmpty()) Double.NaN else dark.average()
    }

    @Test
    fun aStrokeDrawnAt1xStaysSmoothAndStillAt100000x() {
        // A was drawn at 1x: 10,000 units long, ending in a round cap of radius 2 at x = 102. Its
        // end sits 10,000 units from its anchor, where a float resolves only about 0.001 units,
        // which is 100 px at this zoom. B was drawn at 100,000x (frame level 5) just beyond that
        // cap, at x = 102.0005: an upright stroke 20 of its own units (20 px here) wide.
        val a = line(WorldPoint(-9_900.0, 0.0), WorldPoint(100.0, 0.0), size = 4f, step = 10.0, z = "a0")
        val b = line(WorldPoint(102.0005, -0.003), WorldPoint(102.0005, 0.003), size = 20f, step = 0.000_5, z = "a1", level = 5)
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Deep", 0L, 0L), listOf(a, b), writer = {})
        val zoom = 100_000.0
        val controller = CameraController(Camera(WorldPoint(101.999, 0.0), zoom))
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(sheet, controller, object : CanvasInputSink { override fun onPointer(event: CanvasPointerEvent) = Unit }, Modifier.fillMaxSize().testTag("deep"))
            }
        }
        rule.waitForIdle()
        val first = rule.onNodeWithTag("deep").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

        // Pan by exactly 64 pixels.
        rule.runOnUiThread { controller.set(Camera(WorldPoint(101.999 + 64 / zoom, 0.0), zoom)) }
        rule.waitForIdle()
        val second = rule.onNodeWithTag("deep").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

        val rows = (20 until 380 step 20).toList()
        val edges = rows.map { first.capEdge(it) }
        val shiftedEdges = rows.map { second.capEdge(it) }
        Log.i("DeepZoomRenderingTest", "cap edge ${edges.min()}..${edges.max()} then ${shiftedEdges.min()}..${shiftedEdges.max()}; neighbour ${first.neighbourCentre(200)} then ${second.neighbourCentre(200)}")

        // Where the geometry says: (102 - 101.999) * 100,000 + 300 = 400, the same on every row (smooth).
        assertTrue("cap edge $edges", edges.all { it in 398..402 })
        assertTrue("cap edge after the pan $shiftedEdges", shiftedEdges.all { it in 334..338 })
        // B keeps its distance from A: 50 px from the cap edge to B's middle in both frames.
        val gapBefore = first.neighbourCentre(200) - first.capEdge(200)
        val gapAfter = second.neighbourCentre(200) - second.capEdge(200)
        assertTrue("gap $gapBefore then $gapAfter", abs(gapBefore - 50) <= 2.5 && abs(gapAfter - gapBefore) <= 1.5)
    }
}
