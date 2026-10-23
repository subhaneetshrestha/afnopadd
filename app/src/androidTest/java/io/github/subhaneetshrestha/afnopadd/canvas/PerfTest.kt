package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Bitmap
import android.graphics.RenderNode
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.PerfBudget
import io.github.subhaneetshrestha.afnopadd.canvas.internal.DryLayer
import io.github.subhaneetshrestha.afnopadd.canvas.internal.ImagePainter
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileGrid
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileLayer
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import io.github.subhaneetshrestha.afnopadd.fixtures.PerfFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/**
 * docs/product/spec.md success criteria 1 to 3 on the shared fixture (5,000 strokes, 20 images): opening the
 * sheet, drawing a 1,000-point stroke on it, and panning and zooming at every zoom bucket. Times
 * are logged for `docs/checklists/perf-2026.md`; the asserted limits are the spec's, doubled on an
 * emulator ([PerfBudget]). Criterion 4 (zoom range and stability) is `DeepZoomRenderingTest`.
 */
@RunWith(AndroidJUnit4::class)
class PerfTest {

    @get:Rule
    val rule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir, "perf-${System.nanoTime()}")
    private val store = RoomSheetStore(context)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** The fixture on disk, closed, as a user's sheet is before they open it. */
    private fun fixtureOnDisk(): File {
        val dir = File(root, "fixture")
        runBlocking {
            val sheet = store.create(dir, "Fixture")
            PerfFixture.fill(sheet)
            sheet.close()
        }
        return dir
    }

    private val ignoring = object : CanvasInputSink { override fun onPointer(event: CanvasPointerEvent) = Unit }

    @Test
    fun openingTheFixtureReachesItsFirstFrameInTime() {
        val dir = fixtureOnDisk()
        Runtime.getRuntime().gc()

        val start = SystemClock.elapsedRealtime()
        val sheet = runBlocking { store.open(dir) }
        val opened = SystemClock.elapsedRealtime()
        var firstFrameAt = 0L
        rule.setContent {
            SheetCanvas(
                sheet, CameraController(Camera(WorldPoint(PerfFixture.SIDE / 2, PerfFixture.SIDE / 2), 1.0)), ignoring,
                Modifier.fillMaxSize().testTag("perf"),
            )
        }
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback { firstFrameAt = SystemClock.elapsedRealtime() } }
        rule.waitUntil(timeoutMillis = 10_000) { firstFrameAt != 0L }
        rule.waitForIdle()
        val interactive = SystemClock.elapsedRealtime()
        runBlocking { sheet.close() }

        Log.i("PerfTest", "open fixture: database ${opened - start} ms, first frame ${firstFrameAt - start} ms, idle ${interactive - start} ms")
        assertEquals(PerfFixture.STROKES + PerfFixture.IMAGES, sheet.snapshot.value.elements.size)
        assertTrue("first interactive frame after ${interactive - start} ms", interactive - start < PerfBudget.millis(1_500L))
    }

    @Test
    fun aThousandPointStrokeOnTheFixtureDropsNoFrames() {
        val dir = fixtureOnDisk()
        val sheet: Sheet = runBlocking { store.open(dir) }
        val pen = object : CanvasInputSink {
            var finished = 0
            override fun onPointer(event: CanvasPointerEvent) = Unit
            override fun onWetStrokeFinished(stroke: FinishedStroke) {
                finished++
            }
        }
        rule.setContent {
            SheetCanvas(
                sheet, CameraController(Camera(WorldPoint(PerfFixture.SIDE / 2, PerfFixture.SIDE / 2), 1.0)), pen,
                Modifier.fillMaxSize().testTag("perf"),
                wetStrokeBrush = { BrushSpec("ink:pressure-pen", 4f, 0xFF000000, 1f, 0.025f) },
            )
        }
        rule.waitForIdle()

        // Every frame's start time while the stroke is drawn.
        val frames = Collections.synchronizedList(ArrayList<Long>())
        var recording = true
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frames += frameTimeNanos
                if (recording) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        instrumentation.runOnMainSync { Choreographer.getInstance().postFrameCallback(callback) }
        // A slow spiral, a point every 4 ms (a 240 Hz stylus), each injected as it happens.
        fun at(i: Int): Offset {
            val angle = i * 0.02
            val radius = 50 + i * 0.3
            return Offset((540 + radius * kotlin.math.cos(angle)).toFloat(), (1_000 + radius * kotlin.math.sin(angle)).toFloat())
        }
        val canvas = rule.onNodeWithTag("perf")
        canvas.performTouchInput { down(at(0)) }
        for (i in 1 until 1_000) {
            canvas.performTouchInput { moveTo(at(i)) }
            Thread.sleep(4)
        }
        canvas.performTouchInput { up() }
        rule.waitUntil(timeoutMillis = 10_000) { pen.finished == 1 }
        recording = false
        rule.waitForIdle()
        runBlocking { sheet.close() }

        val intervals = frames.toList().zipWithNext { a, b -> (b - a) / 1e6 }
        val vsync = intervals.sorted()[intervals.size / 2]
        val dropped = intervals.count { it > vsync * 1.5 }
        Log.i("PerfTest", "1,000-point stroke: ${intervals.size} frames, vsync ${"%.1f".format(vsync)} ms, dropped $dropped, worst ${"%.1f".format(intervals.max())} ms")
        // No dropped frames is the device target; an emulator shares its host and gets a few.
        val allowed = if (PerfBudget.isEmulator) intervals.size / 20 else 0
        assertTrue("$dropped of ${intervals.size} frames dropped", dropped <= allowed)
    }

    @Test
    fun panAndZoomHoldSixtyFramesASecondAtEveryZoom() {
        val dir = fixtureOnDisk()
        val sheet: Sheet = runBlocking { store.open(dir) }
        val snapshot = sheet.snapshot.value
        val viewport = Viewport(1_080, 2_400)
        val index = ElementIndex().also { it.sync(snapshot) }
        // Images answer at once at the asked size, so this measures drawing, not decoding.
        val loader = ImageBitmapLoader { _, size, onLoaded, _ -> onLoaded(createBitmap(size, size * 3 / 4).apply { eraseColor(0xFF3366CC.toInt()) }); {} }
        var vectors: DryLayer? = null
        var tiles: TileLayer? = null
        instrumentation.runOnMainSync {
            vectors = DryLayer(imagePainter = ImagePainter(loader, fileFor = { File("unused") }, onLoaded = {}))
            tiles = TileLayer(TileGrid.budgetBytes(512), fileFor = { id -> sheet.assets.file(id) }, onTileReady = {})
        }
        val node = RenderNode("perf").apply { setPosition(0, 0, viewport.widthPx, viewport.heightPx) }

        fun frame(camera: Camera): Double {
            var millis = 0.0
            instrumentation.runOnMainSync {
                val start = System.nanoTime()
                val canvas = node.beginRecording()
                val rect = WorldRect(
                    camera.center.x - viewport.centerX / camera.zoom, camera.center.y - viewport.centerY / camera.zoom,
                    camera.center.x + viewport.centerX / camera.zoom, camera.center.y + viewport.centerY / camera.zoom,
                )
                val visible = index.visible(snapshot, rect)
                // The same choice SheetCanvas makes.
                vectors!!.beginFrame()
                tiles!!.sync(snapshot)
                if (tiles!!.shouldTile(visible, camera)) tiles!!.draw(canvas, snapshot, visible, emptySet(), camera, viewport, rect, vectors!!)
                else vectors!!.draw(canvas, visible, emptySet(), camera, viewport)
                node.endRecording()
                millis = (System.nanoTime() - start) / 1e6
            }
            return millis
        }

        val results = ArrayList<String>()
        var worstMedian = 0.0
        var worstP95 = 0.0
        // One zoom per decade of the range and one in between, centred on a stroke of the fixture.
        val centre = snapshot.ordered.first().bounds.let { WorldPoint((it.left + it.right) / 2, (it.top + it.bottom) / 2) }
        for (zoom in generateSequence(0.001) { it * 3.16 }.takeWhile { it <= 100_000.0 }) {
            val camera = Camera(centre, zoom)
            // Let tiles and meshes for this zoom arrive, as they would within a few frames of zooming.
            repeat(40) {
                frame(camera)
                Thread.sleep(10)
            }
            val times = List(60) { i -> frame(camera.copy(center = WorldPoint(centre.x + i * 4 / zoom, centre.y))) }.sorted()
            val median = times[times.size / 2]
            val p95 = times[times.size * 95 / 100]
            worstMedian = maxOf(worstMedian, median)
            worstP95 = maxOf(worstP95, p95)
            results += "${"%g".format(zoom)}x ${"%.2f".format(median)}/${"%.2f".format(p95)} ms"
        }
        instrumentation.runOnMainSync {
            tiles!!.release()
            vectors!!.release()
        }
        runBlocking { sheet.close() }

        Log.i("PerfTest", "pan per zoom (median/p95 UI-thread frame): ${results.joinToString("; ")}")
        // 60 fps leaves 16.7 ms a frame; the UI thread's share must stay well inside it.
        assertTrue("worst median $worstMedian ms", worstMedian < PerfBudget.millis(8.0))
        assertTrue("worst p95 $worstP95 ms", worstP95 < PerfBudget.millis(16.0))
    }
}
