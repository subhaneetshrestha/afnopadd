package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderNode
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.PerfBudget
import io.github.subhaneetshrestha.afnopadd.canvas.internal.DryLayer
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileGrid
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileLayer
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.fixtures.PerfFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The tile pyramid: what it draws, what it costs, what a transaction redraws, and what it holds. */
@RunWith(AndroidJUnit4::class)
class TileLayerTest {

    @get:Rule
    val rule = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val viewport = Viewport(1080, 2400)
    private val overview = Camera(WorldPoint(PerfFixture.SIDE / 2, PerfFixture.SIDE / 2), zoom = 1_080.0 / PerfFixture.SIDE)

    private fun visibleRect(camera: Camera) = WorldRect(
        CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0)).x,
        CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0)).y,
        CanvasMath.screenToWorld(camera, viewport, ScreenPoint(viewport.widthPx.toDouble(), viewport.heightPx.toDouble())).x,
        CanvasMath.screenToWorld(camera, viewport, ScreenPoint(viewport.widthPx.toDouble(), viewport.heightPx.toDouble())).y,
    )

    /** Records one frame of [layer] into a hardware display list, as the UI thread does; returns milliseconds. */
    private fun frame(layer: TileLayer, vectors: DryLayer, index: ElementIndex, snapshot: ElementSnapshot, camera: Camera, node: RenderNode): Double {
        var millis = 0.0
        instrumentation.runOnMainSync {
            val start = System.nanoTime()
            val canvas = node.beginRecording()
            val rect = visibleRect(camera)
            val visible = index.visible(snapshot, rect)
            layer.sync(snapshot)
            layer.draw(canvas, snapshot, visible, emptySet(), camera, viewport, rect, vectors)
            node.endRecording()
            millis = (System.nanoTime() - start) / 1e6
        }
        return millis
    }

    /** Draws frames until no tile needs patching, as a screen would while tiles arrive. */
    private fun settle(layer: TileLayer, vectors: DryLayer, index: ElementIndex, snapshot: ElementSnapshot, camera: Camera, node: RenderNode) {
        val deadline = System.currentTimeMillis() + 60_000
        do {
            frame(layer, vectors, index, snapshot, camera, node)
            if (layer.lastPatchedTiles == 0) return
            Thread.sleep(20)
        } while (System.currentTimeMillis() < deadline)
        error("tiles did not settle: ${layer.lastPatchedTiles} still patched")
    }

    private fun newLayer(budget: Long = TileGrid.budgetBytes(512)): TileLayer {
        var layer: TileLayer? = null
        instrumentation.runOnMainSync { layer = TileLayer(budget, fileFor = { null }, onTileReady = {}) }
        return layer!!
    }

    @Test
    fun theOverviewDrawsFromTilesFarFasterThanFromVectors() {
        val snapshot = ElementSnapshot(PerfFixture.strokes().associateBy { it.id })
        val index = ElementIndex().also { it.sync(snapshot) }
        val vectors = DryLayer()
        val layer = newLayer()
        val node = RenderNode("tiles").apply { setPosition(0, 0, viewport.widthPx, viewport.heightPx) }
        assertTrue(layer.shouldTile(index.visible(snapshot, visibleRect(overview)), overview))

        val vectorTimes = List(10) { i ->
            var t = 0.0
            instrumentation.runOnMainSync {
                val start = System.nanoTime()
                val canvas = node.beginRecording()
                vectors.draw(canvas, index.visible(snapshot, visibleRect(overview)), emptySet(), overview.copy(center = WorldPoint(overview.center.x + i, overview.center.y)), viewport)
                node.endRecording()
                t = (System.nanoTime() - start) / 1e6
            }
            t
        }.sorted()
        settle(layer, vectors, index, snapshot, overview, node)
        val tileTimes = List(60) { i -> frame(layer, vectors, index, snapshot, overview.copy(center = WorldPoint(overview.center.x + i * 0.01, overview.center.y)), node) }.sorted()
        instrumentation.runOnMainSync { layer.release() }

        val vectorMedian = vectorTimes[vectorTimes.size / 2]
        val tileMedian = tileTimes[tileTimes.size / 2]
        Log.i("TileLayerTest", "overview of 5,000 strokes: vectors median ${"%.2f".format(vectorMedian)} ms, tiles median ${"%.2f".format(tileMedian)} ms p95 ${"%.2f".format(tileTimes[tileTimes.size * 95 / 100])} ms, ${layer.heldCount} tiles ${layer.heldBytes / 1024} KiB")
        assertTrue("tiles median $tileMedian ms", tileMedian < PerfBudget.millis(4.0))
        assertTrue("tiles $tileMedian ms vs vectors $vectorMedian ms", tileMedian * 10 < vectorMedian)
    }

    @Test
    fun aTransactionRedrawsOnlyTheTilesItTouched() {
        val strokes = PerfFixture.strokes()
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Tiles", 0L, 0L), strokes, writer = {})
        val index = ElementIndex()
        val vectors = DryLayer()
        val layer = newLayer()
        val node = RenderNode("tiles").apply { setPosition(0, 0, viewport.widthPx, viewport.heightPx) }
        index.sync(sheet.snapshot.value)
        settle(layer, vectors, index, sheet.snapshot.value, overview, node)
        val held = layer.heldCount

        // A small stroke in the middle of one tile.
        val level = TileGrid.levelFor(overview.zoom)
        val tile = TileGrid.worldSize(level)
        val added = PerfFixture.strokes(count = 1, seed = 99).single().let { s ->
            val dx = tile * 3.5 - s.anchor.x
            val dy = tile * 4.5 - s.anchor.y
            s.copy(
                anchor = WorldPoint(s.anchor.x + dx, s.anchor.y + dy),
                bounds = WorldRect(s.bounds.left + dx, s.bounds.top + dy, s.bounds.right + dx, s.bounds.bottom + dy),
                z = FractionalIndex.between(sheet.snapshot.value.ordered.last().z, null),
            )
        }
        sheet.transact("stroke") { add(added) }
        index.sync(sheet.snapshot.value)
        frame(layer, vectors, index, sheet.snapshot.value, overview, node)

        assertEquals("only the tile under the new stroke is redrawn", 1, layer.lastPatchedTiles)
        assertEquals(held - 1, layer.heldCount)
        instrumentation.runOnMainSync { layer.release() }
    }

    @Test
    fun tileMemoryNeverExceedsTheBudget() {
        val snapshot = ElementSnapshot(PerfFixture.strokes().associateBy { it.id })
        val index = ElementIndex().also { it.sync(snapshot) }
        val vectors = DryLayer()
        // 150 tiles: more than one screen needs (tiles on screen this frame are never evicted), fewer than the pan visits.
        val budget = 150L * 256 * 256 * 4
        val layer = newLayer(budget)
        val node = RenderNode("tiles").apply { setPosition(0, 0, viewport.widthPx, viewport.heightPx) }

        var peak = 0L
        // Zoomed in enough to need hundreds of tiles across the pan, but still crowded enough to tile.
        for (step in 0 until 40) {
            val camera = Camera(WorldPoint(2_000.0 + step * 400.0, 4_000.0 + step * 300.0), zoom = 0.09)
            repeat(5) {
                frame(layer, vectors, index, snapshot, camera, node)
                peak = maxOf(peak, layer.heldBytes)
                Thread.sleep(15)
            }
        }
        instrumentation.runOnMainSync { layer.release() }

        Log.i("TileLayerTest", "budget ${budget / 1024} KiB, peak ${peak / 1024} KiB")
        assertTrue("peak $peak over budget $budget", peak <= budget)
        assertTrue("tiles were used", peak > 0)
    }

    @Test
    fun tilesLookLikeTheVectorsTheyReplace() {
        val strokes = PerfFixture.strokes()
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Tiles", 0L, 0L), strokes, writer = {})
        var layer: TileLayer? = null
        val controller = CameraController(Camera(WorldPoint(PerfFixture.SIDE / 2, PerfFixture.SIDE / 2), zoom = 600.0 / PerfFixture.SIDE))
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalTileLayerObserver provides { layer = it }) {
                Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                    SheetCanvas(sheet, controller, object : CanvasInputSink { override fun onPointer(event: CanvasPointerEvent) = Unit }, Modifier.fillMaxSize().testTag("canvas"))
                }
            }
        }
        rule.waitUntil(timeoutMillis = 60_000) { rule.runOnIdle { layer!!.heldCount > 0 && layer!!.lastPatchedTiles == 0 } }
        val tiled = rule.onNodeWithTag("canvas").captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

        val camera = controller.camera.value
        val region = WorldRect(
            camera.center.x - 300 / camera.zoom, camera.center.y - 200 / camera.zoom,
            camera.center.x + 300 / camera.zoom, camera.center.y + 200 / camera.zoom,
        )
        val direct = SoftwareRenderer().renderBitmap(sheet.snapshot.value, region, camera.zoom)

        // Compare ink coverage per 20 px cell: tiles are rendered at a power-of-two scale and
        // resampled, so single pixels differ, but the same strokes must be in the same places.
        var cells = 0
        var differing = 0
        for (cy in 0 until 400 / 20) for (cx in 0 until 600 / 20) {
            fun ink(bitmap: Bitmap): Int {
                var n = 0
                for (y in cy * 20 until cy * 20 + 20) for (x in cx * 20 until cx * 20 + 20) if (Color.red(bitmap.getPixel(x, y)) < 200) n++
                return n
            }
            val a = ink(tiled)
            val b = ink(direct)
            if (a + b == 0) continue
            cells++
            if (abs(a - b) > maxOf(8, (a + b) / 4)) differing++
        }
        Log.i("TileLayerTest", "inked cells $cells, differing $differing")
        assertTrue("cells with ink: $cells", cells > 100)
        assertTrue("$differing of $cells cells differ", differing * 20 < cells)
    }

    @Test
    fun goingToTheBackgroundReleasesEveryTile() {
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Tiles", 0L, 0L), PerfFixture.strokes(), writer = {})
        var layer: TileLayer? = null
        val scenario = ActivityScenario.launch(androidx.activity.ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalTileLayerObserver provides { layer = it }) {
                    SheetCanvas(sheet, CameraController(overview), object : CanvasInputSink { override fun onPointer(event: CanvasPointerEvent) = Unit }, Modifier.fillMaxSize())
                }
            }
        }
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            var held = 0
            instrumentation.runOnMainSync { held = layer?.heldCount ?: 0 }
            if (held > 10) break
            Thread.sleep(50)
        }

        var before = 0L
        instrumentation.runOnMainSync { before = layer!!.heldBytes }
        // moveToState returns once the activity is stopped; the tiles must be gone within a second of that.
        scenario.moveToState(Lifecycle.State.CREATED)
        val stopped = System.currentTimeMillis()
        var remaining: Long
        do {
            var held = 0L
            instrumentation.runOnMainSync { held = layer!!.heldBytes }
            remaining = held
            if (remaining == 0L) break
            Thread.sleep(20)
        } while (System.currentTimeMillis() - stopped < 1_000)
        Log.i("TileLayerTest", "held ${before / 1024} KiB before going to the background, $remaining bytes after")
        assertTrue("tiles were held before", before > 0)
        scenario.close()
        assertEquals("tiles left a second after going to the background", 0L, remaining)
    }
}
