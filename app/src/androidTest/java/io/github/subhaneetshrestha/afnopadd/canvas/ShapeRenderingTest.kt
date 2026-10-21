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
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeGeometry
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.ShapeStyle
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
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

/** Shapes are drawn as paths from their two points, at any zoom, identically on screen and in the software renderer. */
@RunWith(AndroidJUnit4::class)
class ShapeRenderingTest {

    @get:Rule
    val rule = createComposeRule()

    private val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(61))

    /** A shape at frame level 0 anchored at [start], spanning to [end], in world units. */
    private fun shape(kind: ShapeKind, start: WorldPoint, end: WorldPoint, width: Float, z: String = "a0"): ShapeElement {
        val local = ShapeOutline.localBounds(kind, LocalPoint(0f, 0f), LocalPoint((end.x - start.x).toFloat(), (end.y - start.y).toFloat()), width)
        return ShapeElement(
            id = ElementId(ulid.next()),
            anchor = start,
            frameLevel = 0,
            z = FractionalIndex(z),
            bounds = WorldRect(start.x + local.left, start.y + local.top, start.x + local.right, start.y + local.bottom),
            version = 1, versionNonce = 1, createdAt = 1L, updatedAt = 1L,
            shape = kind,
            geometry = ShapeGeometry(LocalPoint(0f, 0f), LocalPoint((end.x - start.x).toFloat(), (end.y - start.y).toFloat())),
            style = ShapeStyle(strokeWidth = width, colorArgb = 0xFF000000),
        )
    }

    private val ignoringSink = object : CanvasInputSink {
        override fun onPointer(event: CanvasPointerEvent) = Unit
    }

    private fun showCanvas(elements: List<Element>, controller: CameraController, preview: List<Element> = emptyList()) {
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Shapes", 0L, 0L), elements, writer = {})
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(sheet, controller, ignoringSink, Modifier.fillMaxSize().testTag(CANVAS_TAG), previewElements = preview)
            }
        }
        rule.waitForIdle()
    }

    private fun capture(): Bitmap = rule.onNodeWithTag(CANVAS_TAG).captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    private fun Bitmap.isDark(x: Int, y: Int): Boolean {
        val c = getPixel(x, y)
        return (Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < 128
    }

    private fun Bitmap.darkRun(x: Int, centerY: Int): Int {
        if (!isDark(x, centerY)) return 0
        var top = centerY
        while (top > 0 && isDark(x, top - 1)) top--
        var bottom = centerY
        while (bottom < height - 1 && isDark(x, bottom + 1)) bottom++
        return bottom - top + 1
    }

    private fun Bitmap.darkPixels(): Int {
        var count = 0
        for (y in 0 until height) for (x in 0 until width) if (isDark(x, y)) count++
        return count
    }

    @Test
    fun aRectangleIsOutlinedWhereTheCameraPutsIt() {
        // Camera at (0,0), 1x: world (-100,-50)..(100,50) is screen (200,150)..(400,250).
        showCanvas(listOf(shape(ShapeKind.RECTANGLE, WorldPoint(-100.0, -50.0), WorldPoint(100.0, 50.0), 4f)), CameraController(Camera(WorldPoint(0.0, 0.0), 1.0)))

        val image = capture()

        assertTrue("top edge", image.isDark(300, 150))
        assertTrue("left edge", image.isDark(200, 200))
        assertTrue("right edge", image.isDark(400, 200))
        assertTrue("bottom edge", image.isDark(300, 250))
        assertTrue("no fill", !image.isDark(300, 200))
        assertTrue("nothing outside", !image.isDark(150, 200))
    }

    @Test
    fun anEllipseTouchesItsBoxAtTheMidpointsButNotTheCorners() {
        showCanvas(listOf(shape(ShapeKind.ELLIPSE, WorldPoint(-100.0, -50.0), WorldPoint(100.0, 50.0), 4f)), CameraController(Camera(WorldPoint(0.0, 0.0), 1.0)))

        val image = capture()

        assertTrue("top", image.isDark(300, 150))
        assertTrue("left", image.isDark(200, 200))
        assertTrue("corner is outside the ellipse", !image.isDark(202, 152))
        assertTrue("no fill", !image.isDark(300, 200))
    }

    @Test
    fun shapesStayCrispAndScaleAtFiftyTimesZoom() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), 1.0))
        showCanvas(listOf(shape(ShapeKind.LINE, WorldPoint(-100.0, 0.0), WorldPoint(100.0, 0.0), 2f)), controller)
        val at1x = capture().darkRun(300, 200)

        rule.runOnUiThread { controller.set(Camera(WorldPoint(0.0, 0.0), 50.0)) }
        rule.waitForIdle()
        val image = capture()
        val at50x = image.darkRun(300, 200)

        assertTrue("1x width $at1x", at1x in 1..4)
        assertTrue("50x width $at50x should be about 100", at50x in 95..105)
        // A path, not a scaled bitmap: the edge goes from ink to paper within a couple of pixels.
        val top = (0 until 200).first { image.isDark(300, it) }
        assertTrue("edge is sharp", !image.isDark(300, top - 2))
    }

    @Test
    fun previewElementsAreDrawnOverTheSheet() {
        showCanvas(emptyList(), CameraController(Camera(WorldPoint(0.0, 0.0), 1.0)), preview = listOf(shape(ShapeKind.LINE, WorldPoint(-100.0, 0.0), WorldPoint(100.0, 0.0), 4f)))

        assertTrue(capture().isDark(300, 200))
    }

    @Test
    fun theSoftwareRendererMatchesTheScreenForEveryShape() {
        val shapes = listOf(
            shape(ShapeKind.LINE, WorldPoint(-250.0, -150.0), WorldPoint(250.0, -120.0), 4f, "a0"),
            shape(ShapeKind.ARROW, WorldPoint(-200.0, 150.0), WorldPoint(200.0, -50.0), 3f, "a1"),
            shape(ShapeKind.RECTANGLE, WorldPoint(-180.0, -80.0), WorldPoint(-40.0, 60.0), 5f, "a2"),
            shape(ShapeKind.ELLIPSE, WorldPoint(40.0, -80.0), WorldPoint(220.0, 100.0), 5f, "a3"),
        )
        showCanvas(shapes, CameraController(Camera(WorldPoint(0.0, 0.0), 1.0)))
        val onScreen = capture()

        val offScreen = SoftwareRenderer().renderBitmap(ElementSnapshot(shapes.associateBy { it.id }), WorldRect(-300.0, -200.0, 300.0, 200.0), scalePxPerUnit = 1.0)

        assertEquals(onScreen.width, offScreen.width)
        assertEquals(onScreen.height, offScreen.height)
        var differing = 0
        for (y in 0 until offScreen.height) for (x in 0 until offScreen.width) {
            val a = onScreen.getPixel(x, y)
            val b = offScreen.getPixel(x, y)
            if (abs(Color.red(a) - Color.red(b)) > 64 || abs(Color.green(a) - Color.green(b)) > 64 || abs(Color.blue(a) - Color.blue(b)) > 64) differing++
        }
        val fraction = differing.toDouble() / (offScreen.width * offScreen.height)
        Log.i("ShapeRenderingTest", "pixel difference ${"%.3f".format(fraction * 100)}% (ink pixels ${offScreen.darkPixels()})")
        assertTrue("ink should be visible", offScreen.darkPixels() > 2_000)
        assertTrue("difference ${fraction * 100}%", fraction < 0.01)
    }

    private companion object {
        const val CANVAS_TAG = "sheet-canvas"
    }
}
