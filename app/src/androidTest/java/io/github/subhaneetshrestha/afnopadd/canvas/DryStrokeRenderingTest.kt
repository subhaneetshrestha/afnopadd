package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.canvas.internal.StrokeCache
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.Sheet
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
import kotlin.random.Random

/** Dry strokes are drawn from the snapshot with the exact per-element transform, survive the wet-to-dry handoff, and stay crisp when zoomed. */
@RunWith(AndroidJUnit4::class)
class DryStrokeRenderingTest {

    @get:Rule
    val rule = createComposeRule()

    private val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(31))

    private fun sheet(vararg elements: StrokeElement): Sheet = SheetImpl(
        meta = SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Dry", 0L, 0L),
        initialElements = elements.toList(),
        writer = {},
    )

    /** A horizontal stroke from (x0, y) to (x1, y) in world units at frame level 0 with the given brush size. */
    private fun horizontalStroke(x0: Double, x1: Double, y: Double, sizeWorld: Float): StrokeElement {
        val count = 20
        val anchor = WorldPoint(x0, y)
        return StrokeElement(
            id = ElementId(ulid.next()),
            anchor = anchor,
            frameLevel = 0,
            z = FractionalIndex.first(),
            bounds = WorldRect(x0 - sizeWorld, y - sizeWorld, x1 + sizeWorld, y + sizeWorld),
            version = 1,
            versionNonce = 1,
            createdAt = 1L,
            updatedAt = 1L,
            brush = BrushSpec(familyId = "ink:marker", size = sizeWorld, colorArgb = 0xFF000000, opacity = 1f, epsilon = 0.025f),
            points = StrokePoints.of(
                x = FloatArray(count) { ((x1 - x0) * it / (count - 1)).toFloat() },
                y = FloatArray(count) { 0f },
                pressure = FloatArray(count) { 1f },
                tiltRad = FloatArray(count) { 0f },
                orientationRad = FloatArray(count) { 0f },
                timeMillis = FloatArray(count) { it * 8f },
            ),
        )
    }

    private val ignoringSink = object : CanvasInputSink {
        override fun onPointer(event: CanvasPointerEvent) = Unit
    }

    private fun showCanvas(sheet: Sheet, controller: CameraController, sink: CanvasInputSink = ignoringSink, brush: (StrokeFrame) -> BrushSpec? = { null }) {
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(
                    sheet = sheet,
                    cameraController = controller,
                    inputSink = sink,
                    modifier = Modifier.fillMaxSize().testTag(CANVAS_TAG),
                    wetStrokeBrush = brush,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun ImageBitmap.isDark(x: Int, y: Int): Boolean {
        val pixel = toPixelMap()[x, y]
        return (pixel.red + pixel.green + pixel.blue) / 3f < 0.5f
    }

    private fun ImageBitmap.darkRun(x: Int, centerY: Int): Int {
        var top = centerY
        while (top > 0 && isDark(x, top - 1)) top--
        var bottom = centerY
        while (bottom < height - 1 && isDark(x, bottom + 1)) bottom++
        return if (isDark(x, centerY)) bottom - top + 1 else 0
    }

    @Test
    fun strokesInTheSnapshotAreDrawnWhereTheCameraPutsThem() {
        // Camera centred on (0,0) at 1x: world (-100, 0)..(100, 0) maps to screen x 200..400 at y 200.
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
        showCanvas(sheet(horizontalStroke(-100.0, 100.0, 0.0, sizeWorld = 6f)), controller)

        val image = rule.onNodeWithTag(CANVAS_TAG).captureToImage()

        assertTrue("stroke centre should be inked", image.isDark(300, 200))
        assertTrue("left of the stroke should be blank", !image.isDark(150, 200))
        assertTrue("above the stroke should be blank", !image.isDark(300, 150))
    }

    @Test
    fun strokesStayCrispAndScaleWhenZoomedIn() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
        showCanvas(sheet(horizontalStroke(-100.0, 100.0, 0.0, sizeWorld = 2f)), controller)
        val widthAt1x = rule.onNodeWithTag(CANVAS_TAG).captureToImage().darkRun(300, 200)

        rule.runOnUiThread { controller.set(Camera(WorldPoint(0.0, 0.0), zoom = 25.0)) }
        rule.waitForIdle()
        val widthAt25x = rule.onNodeWithTag(CANVAS_TAG).captureToImage().darkRun(300, 200)

        assertTrue("1x width $widthAt1x", widthAt1x in 1..6)
        assertTrue("25x width $widthAt25x should be about 50", widthAt25x in 40..60)
    }

    @Test
    fun aFinishedWetStrokeStaysVisibleAcrossTheHandoff() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
        val sheet = sheet()
        // A minimal pen: commit every finished wet stroke as a StrokeElement.
        val pen = object : CanvasInputSink {
            override fun onPointer(event: CanvasPointerEvent) = Unit
            override fun onWetStrokeFinished(stroke: FinishedStroke) {
                val box = stroke.stroke.shape.computeBoundingBox()
                val unit = FrameLevels.worldUnitsPerLocalUnit(stroke.frame.level)
                sheet.transact("pen") {
                    add(
                        StrokeElement(
                            id = ElementId(ulid.next()),
                            anchor = stroke.frame.anchor,
                            frameLevel = stroke.frame.level,
                            z = FractionalIndex.first(),
                            bounds = WorldRect(
                                stroke.frame.anchor.x + (box?.xMin ?: 0f) * unit,
                                stroke.frame.anchor.y + (box?.yMin ?: 0f) * unit,
                                stroke.frame.anchor.x + (box?.xMax ?: 0f) * unit,
                                stroke.frame.anchor.y + (box?.yMax ?: 0f) * unit,
                            ),
                            version = 1,
                            versionNonce = 1,
                            createdAt = 1L,
                            updatedAt = 1L,
                            brush = stroke.brush,
                            points = stroke.points,
                        ),
                    )
                }
            }
        }
        showCanvas(sheet, controller, sink = pen, brush = { BrushSpec("ink:marker", size = 8f, colorArgb = 0xFF000000, opacity = 1f, epsilon = 0.025f) })

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(200f, 200f))
            moveTo(Offset(300f, 200f))
            moveTo(Offset(400f, 200f))
            up()
        }
        rule.waitUntil(timeoutMillis = 5_000) { sheet.snapshot.value.elements.isNotEmpty() }

        // Two consecutive frames after the handoff: the stroke must be inked in both.
        repeat(2) { frame ->
            rule.waitForIdle()
            val image = rule.onNodeWithTag(CANVAS_TAG).captureToImage()
            assertTrue("frame $frame should still show the stroke", image.isDark(300, 200))
        }
    }

    @Test
    fun theStrokeCacheEvictsUnderItsBudgetAndRebuilds() {
        val cache = StrokeCache(maxBytes = 64 * 1024)
        val elements = List(20) { horizontalStroke(0.0, 500.0, it * 10.0, sizeWorld = 4f) }

        elements.forEach { cache.strokeFor(it) }

        assertTrue("estimated ${cache.estimatedBytes} bytes", cache.estimatedBytes <= 64 * 1024)
        assertTrue(cache.size < elements.size)
        val rebuilt = cache.strokeFor(elements.first())
        assertEquals(elements.first().points.count, rebuilt.inputs.size)
        assertEquals(elements.first().brush.size, rebuilt.brush.size, 1e-6f)
    }

    private companion object {
        const val CANVAS_TAG = "sheet-canvas"
    }
}
