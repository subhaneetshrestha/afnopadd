package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The selection outline and marquee are dashed screen-space rectangles placed by the camera. */
@RunWith(AndroidJUnit4::class)
class SelectionOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Overlay", 0L, 0L), emptyList(), writer = {})
    private val sink = object : CanvasInputSink { override fun onPointer(event: CanvasPointerEvent) = Unit }

    private fun show(outline: WorldRect?, marquee: WorldRect?, zoom: Double, handles: Boolean = false, controller: CameraController = CameraController(Camera(WorldPoint(0.0, 0.0), zoom))) {
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(
                    sheet, controller, sink, Modifier.fillMaxSize().testTag("canvas"),
                    selectionOutline = outline, marquee = marquee, selectionHandles = handles,
                )
            }
        }
        rule.waitForIdle()
    }

    /** The width of the handle ring drawn around [centre], measured along its row. */
    private fun ringWidthAt(centre: ScreenPoint): Int {
        val pixels = rule.onNodeWithTag("canvas").captureToImage().toPixelMap()
        val y = centre.y.toInt()
        val xs = (centre.x.toInt() - 40..centre.x.toInt() + 40).filter { x -> x in 0 until pixels.width && pixels[x, y].let { it.red > 0.7f && it.blue < 0.45f } }
        return if (xs.isEmpty()) 0 else xs.last() - xs.first() + 1
    }

    @Test
    fun handlesAreTheSameSizeOnScreenAtEveryZoom() {
        val bounds = WorldRect(-2.0, -1.0, 2.0, 1.0)
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), 50.0))
        show(outline = bounds, marquee = null, zoom = 50.0, handles = true, controller = controller)
        val density = rule.density.density
        fun rotateHandleOnScreen() = CanvasMath.worldToScreen(
            controller.camera.value, Viewport(600, 400),
            SelectionHandles.position(SelectionHandles.Handle.ROTATE, bounds, controller.camera.value.zoom, density),
        )
        val at50x = ringWidthAt(rotateHandleOnScreen())

        rule.runOnUiThread { controller.set(Camera(WorldPoint(0.0, 0.0), 20.0)) }
        rule.waitForIdle()
        val at20x = ringWidthAt(rotateHandleOnScreen())

        val expected = 2 * (SelectionHandles.RADIUS_DP + 1) * density
        assertTrue("ring $at50x px at 50x, expected about $expected", abs(at50x - expected) <= 3)
        assertTrue("ring $at20x px at 20x, $at50x px at 50x", abs(at20x - at50x) <= 1)
    }

    /** Counts accent-coloured (vermilion) pixels along a horizontal run, where the dashed edge crosses. */
    private fun accentAlongRow(y: Int, xs: IntRange): Int {
        val pixels = rule.onNodeWithTag("canvas").captureToImage().toPixelMap()
        return xs.count { x -> pixels[x, y].let { it.red > 0.7f && it.blue < 0.45f } }
    }

    @Test
    fun theOutlineIsADashedRectanglePlacedByTheCamera() {
        // At 2x, world (-50, -25)..(50, 25) is screen (200, 150)..(400, 250); the outline sits a few pixels outside.
        show(outline = WorldRect(-50.0, -25.0, 50.0, 25.0), marquee = null, zoom = 2.0)
        val pad = (6 * rule.density.density).toInt()
        val topEdge = 150 - pad
        val dashed = (topEdge - 2..topEdge + 2).maxOf { accentAlongRow(it, 210..390) }
        assertTrue("dashes along the top edge: $dashed", dashed in 40..170)
        assertTrue("nothing inside the selection", accentAlongRow(200, 220..380) == 0)
    }

    @Test
    fun theMarqueeIsTinted() {
        show(outline = null, marquee = WorldRect(-50.0, -25.0, 50.0, 25.0), zoom = 2.0)

        val inside = rule.onNodeWithTag("canvas").captureToImage().toPixelMap()[300, 200]
        val outside = rule.onNodeWithTag("canvas").captureToImage().toPixelMap()[50, 50]
        assertTrue("the marquee is tinted: $inside", inside.red > inside.blue + 0.03f)
        assertTrue("outside is not: $outside", outside.red <= outside.blue + 0.01f)
    }
}
