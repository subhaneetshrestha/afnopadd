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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class SoftwareRendererTest {

    @get:Rule
    val rule = createComposeRule()

    private val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(51))

    /** A straight stroke from [from] to [to] in world units at frame level 0. */
    private fun line(from: WorldPoint, to: WorldPoint, size: Float, z: String): StrokeElement {
        val count = 24
        val dx = (to.x - from.x).toFloat()
        val dy = (to.y - from.y).toFloat()
        return StrokeElement(
            id = ElementId(ulid.next()),
            anchor = from,
            frameLevel = 0,
            z = FractionalIndex(z),
            bounds = WorldRect(minOf(from.x, to.x) - size, minOf(from.y, to.y) - size, maxOf(from.x, to.x) + size, maxOf(from.y, to.y) + size),
            version = 1, versionNonce = 1, createdAt = 1L, updatedAt = 1L,
            brush = BrushSpec("ink:marker", size, 0xFF000000, 1f, 0.025f),
            points = StrokePoints.of(
                x = FloatArray(count) { dx * it / (count - 1) },
                y = FloatArray(count) { dy * it / (count - 1) },
                pressure = FloatArray(count) { 1f },
                tiltRad = FloatArray(count) { 0f },
                orientationRad = FloatArray(count) { 0f },
                timeMillis = FloatArray(count) { it * 8f },
            ),
        )
    }

    private val strokes = listOf(
        line(WorldPoint(-250.0, -150.0), WorldPoint(250.0, -150.0), 6f, "a0"),
        line(WorldPoint(-200.0, 150.0), WorldPoint(200.0, -100.0), 4f, "a1"),
        line(WorldPoint(0.0, -180.0), WorldPoint(0.0, 180.0), 10f, "a2"),
    )

    private fun darkPixels(bitmap: Bitmap, rows: IntRange = 0 until bitmap.height): Int {
        var count = 0
        for (y in rows) for (x in 0 until bitmap.width) {
            val c = bitmap.getPixel(x, y)
            if ((Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < 128) count++
        }
        return count
    }

    @Test
    fun renderBitmapMatchesTheOnScreenCanvas() {
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "R", 0L, 0L), strokes, writer = {})
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
        val sink = object : CanvasInputSink { override fun onPointer(event: CanvasPointerEvent) = Unit }
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(sheet, controller, sink, Modifier.fillMaxSize().testTag("canvas"))
            }
        }
        rule.waitForIdle()
        val onScreen = rule.onNodeWithTag("canvas").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

        val offScreen = SoftwareRenderer().renderBitmap(
            ElementSnapshot(strokes.associateBy { it.id }),
            bounds = WorldRect(-300.0, -200.0, 300.0, 200.0),
            scalePxPerUnit = 1.0,
        )

        assertEquals(onScreen.width, offScreen.width)
        assertEquals(onScreen.height, offScreen.height)
        var differing = 0
        for (y in 0 until offScreen.height) for (x in 0 until offScreen.width) {
            val a = onScreen.getPixel(x, y)
            val b = offScreen.getPixel(x, y)
            if (abs(Color.red(a) - Color.red(b)) > 64 || abs(Color.green(a) - Color.green(b)) > 64 || abs(Color.blue(a) - Color.blue(b)) > 64) differing++
        }
        val fraction = differing.toDouble() / (offScreen.width * offScreen.height)
        Log.i("SoftwareRendererTest", "pixel difference ${"%.3f".format(fraction * 100)}% (ink pixels ${darkPixels(offScreen)})")
        assertTrue("ink should be visible", darkPixels(offScreen) > 1_000)
        assertTrue("difference ${fraction * 100}%", fraction < 0.01)
    }

    @Test
    fun aHundredMegapixelRegionRendersInTiles() {
        val snapshot = ElementSnapshot(listOf(line(WorldPoint(100.0, 500.0), WorldPoint(900.0, 500.0), 4f, "a0")).associateBy { it.id })
        var tiles = 0
        var pixels = 0L
        var inkedTiles = 0

        SoftwareRenderer().renderTiled(
            snapshot,
            bounds = WorldRect(0.0, 0.0, 1_000.0, 1_000.0),
            scalePxPerUnit = 10.0,
            tileSizePx = 2_048,
        ) { tile ->
            tiles++
            pixels += tile.bitmap.width.toLong() * tile.bitmap.height
            // The stroke sits on pixel row 5000, inside the third row of tiles.
            if (darkPixels(tile.bitmap) > 0) {
                inkedTiles++
                assertTrue("ink at tile y ${tile.topPx}", 5_000 in tile.topPx until tile.topPx + tile.bitmap.height)
            }
        }

        assertEquals(25, tiles)
        assertEquals(100_000_000L, pixels)
        assertTrue("the stroke spans several tiles, inked $inkedTiles", inkedTiles >= 4)
    }
}
