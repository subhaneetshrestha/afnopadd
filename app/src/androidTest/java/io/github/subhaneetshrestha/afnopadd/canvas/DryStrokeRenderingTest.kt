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
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
                val points = stroke.points
                val pad = stroke.brush.size / 2
                val unit = FrameLevels.worldUnitsPerLocalUnit(stroke.frame.level)
                val xs = (0 until points.count).map { points.x(it) }
                val ys = (0 until points.count).map { points.y(it) }
                val id = ElementId(ulid.next())
                sheet.transact("pen") {
                    add(
                        StrokeElement(
                            id = id,
                            anchor = stroke.frame.anchor,
                            frameLevel = stroke.frame.level,
                            z = FractionalIndex.first(),
                            bounds = WorldRect(
                                stroke.frame.anchor.x + (xs.min() - pad) * unit,
                                stroke.frame.anchor.y + (ys.min() - pad) * unit,
                                stroke.frame.anchor.x + (xs.max() + pad) * unit,
                                stroke.frame.anchor.y + (ys.max() + pad) * unit,
                            ),
                            version = 1,
                            versionNonce = 1,
                            createdAt = 1L,
                            updatedAt = 1L,
                            brush = stroke.brush,
                            points = points,
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

        // Committed at pen-up while Ink still draws it wet, then handed over: every frame shows it once.
        repeat(4) { frame ->
            rule.waitForIdle()
            val image = rule.onNodeWithTag(CANVAS_TAG).captureToImage()
            assertTrue("frame $frame should still show the stroke", image.isDark(300, 200))
        }
    }

    @Test
    fun theStrokeCacheEvictsUnderItsBudgetAndRebuilds() {
        val elements = List(20) { horizontalStroke(0.0, 500.0, it * 10.0, sizeWorld = 4f) }
        // Size the budget from a real stroke so the test follows the measured estimate: room for about five.
        val perStroke = StrokeCache().also { it.strokeFor(elements.first()) }.estimatedBytes
        val budget = perStroke * 5 + perStroke / 2
        val cache = StrokeCache(maxBytes = budget)

        elements.forEach { cache.strokeFor(it) }

        assertTrue("estimated ${cache.estimatedBytes} bytes, budget $budget", cache.estimatedBytes <= budget)
        assertTrue(cache.size < elements.size)
        val rebuilt = cache.strokeFor(elements.first())
        assertEquals(elements.first().points.count, rebuilt.inputs.size)
        assertEquals(elements.first().brush.size, rebuilt.brush.size, 1e-6f)
    }

    /**
     * More strokes on screen than the budget holds, drawn in the same order every frame: a plain
     * LRU evicts each mesh just before it is needed (4.9 s frames in PERF.md). Meshes drawn this
     * frame must stay, and the overflow is drawn uncached.
     */
    @Test
    fun theStrokeCacheKeepsWhatFitsWhenEveryFrameDrawsMoreThanItsBudget() {
        val elements = List(5) { horizontalStroke(0.0, 500.0, it * 10.0, sizeWorld = 4f) }
        val perStroke = StrokeCache().also { it.strokeFor(elements.first()) }.estimatedBytes
        val cache = StrokeCache(maxBytes = perStroke * 3 + perStroke / 2)

        repeat(4) {
            cache.beginFrame()
            elements.forEach { cache.strokeFor(it) }
        }

        assertEquals(3, cache.size)
        // First frame builds all five; each later frame rebuilds only the two that do not fit (LRU: 20).
        assertEquals(5 + 3 * 2, cache.buildCount)
    }

    @Test
    fun aStrokeBiggerThanTheWholeBudgetIsDrawnWithoutEvictingAnything() {
        val small = List(2) { horizontalStroke(0.0, 50.0, it * 10.0, sizeWorld = 4f) }
        // Mesh size follows the samples, so "huge" means thousands of wiggling samples.
        val wiggles = 3_000
        val huge = horizontalStroke(0.0, 50.0, 500.0, sizeWorld = 4f).copy(
            points = StrokePoints.of(
                x = FloatArray(wiggles) { it * 0.5f },
                y = FloatArray(wiggles) { if (it % 2 == 0) 0f else 6f },
                pressure = FloatArray(wiggles) { 1f },
                tiltRad = FloatArray(wiggles),
                orientationRad = FloatArray(wiggles),
                timeMillis = FloatArray(wiggles) { it * 4f },
            ),
        )
        val perSmall = StrokeCache().also { it.strokeFor(small.first()) }.estimatedBytes
        val hugeBytes = StrokeCache(maxBytes = Long.MAX_VALUE).also { it.strokeFor(huge) }.estimatedBytes
        assertTrue("fixture: $hugeBytes bytes is not more than the ${perSmall * 3} budget", hugeBytes > perSmall * 3)
        val cache = StrokeCache(maxBytes = perSmall * 3)
        small.forEach { cache.strokeFor(it) }

        cache.strokeFor(huge)

        assertEquals("the oversized stroke evicted the others", 2, cache.size)
        assertTrue(cache.estimatedBytes <= perSmall * 3)
    }

    /** More finely tessellated deep-zoom strokes than a short LRU held, drawn in the same order each frame: built once each. */
    @Test
    fun deepZoomVariantsAreBuiltOnceWhileEveryFrameDrawsThem() {
        val elements = List(80) { horizontalStroke(0.0, 500.0, it * 10.0, sizeWorld = 4f) }
        val cache = StrokeCache()

        repeat(3) {
            cache.beginFrame()
            elements.forEach { cache.fineFor(it) }
        }

        assertEquals("80 fine meshes, built on the first frame only", 80L, cache.buildCount)
        assertTrue(cache.variantEstimatedBytes > 0)
        assertEquals("the plain meshes are untouched", 0, cache.size)
    }

    @Test
    fun aChangedStrokeReplacesItsCachedMesh() {
        val stroke = horizontalStroke(0.0, 500.0, 0.0, sizeWorld = 4f)
        val cache = StrokeCache()
        val first = cache.strokeFor(stroke)

        val moved = stroke.copy(points = horizontalStroke(0.0, 200.0, 0.0, sizeWorld = 4f).points)
        val second = cache.strokeFor(moved)

        assertEquals(1, cache.size)
        assertTrue("a new mesh is built for new points", first !== second)
        val alone = StrokeCache().also { it.strokeFor(moved) }.estimatedBytes
        assertEquals("the old mesh is still counted", alone, cache.estimatedBytes)
    }

    private companion object {
        const val CANVAS_TAG = "sheet-canvas"
    }
}
