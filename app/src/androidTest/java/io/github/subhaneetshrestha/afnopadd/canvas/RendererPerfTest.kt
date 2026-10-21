package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.RenderNode
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.PerfBudget
import io.github.subhaneetshrestha.afnopadd.canvas.internal.DryLayer
import io.github.subhaneetshrestha.afnopadd.canvas.internal.StrokeCache
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Measures the dry layer the way the UI thread pays for it: recording a frame into a hardware
 * [RenderNode] (GPU time on the render thread is not included). Logs time and allocation per
 * frame and native memory per cached stroke for a 5,000-stroke sheet; the numbers are recorded in
 * `docs/perf/PERF.md`.
 */
@RunWith(AndroidJUnit4::class)
class RendererPerfTest {

    private val viewport = Viewport(1080, 2400)
    private val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(77))

    /** 5,000 hand-like strokes of 60 points each, scattered over a 20,000-unit square. */
    private fun fixture(): ElementSnapshot {
        val random = Random(5)
        var z: FractionalIndex? = null
        val strokes = (0 until 5_000).map {
            z = FractionalIndex.between(z, null)
            val anchor = WorldPoint(random.nextDouble(0.0, 20_000.0), random.nextDouble(0.0, 20_000.0))
            val count = 60
            val xs = FloatArray(count)
            val ys = FloatArray(count)
            for (i in 1 until count) {
                xs[i] = xs[i - 1] + random.nextFloat() * 6f - 1f
                ys[i] = ys[i - 1] + random.nextFloat() * 6f - 3f
            }
            StrokeElement(
                id = ElementId(ulid.next()),
                anchor = anchor,
                frameLevel = 0,
                z = z!!,
                bounds = WorldRect(anchor.x + xs.min() - 2, anchor.y + ys.min() - 2, anchor.x + xs.max() + 2, anchor.y + ys.max() + 2),
                version = 1, versionNonce = 1, createdAt = 1L, updatedAt = 1L,
                brush = BrushSpec("ink:pressure-pen", 4f, 0xFF000000, 1f, 0.025f),
                points = StrokePoints.of(xs, ys, FloatArray(count) { 0.6f }, FloatArray(count), FloatArray(count), FloatArray(count) { it * 8f }),
            )
        }
        return ElementSnapshot(strokes.associateBy { it.id })
    }

    private fun bytesAllocated(): Long = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()

    private fun gcCount(): Long = Debug.getRuntimeStat("art.gc.gc-count").toLong()

    private fun settledNativeHeap(): Long {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
        return Debug.getNativeHeapAllocatedSize()
    }

    private class FrameStats(val medianMillis: Double, val p95Millis: Double, val bytesPerFrame: Long, val gcs: Long)

    private fun recordFrames(layer: DryLayer, index: ElementIndex, snapshot: ElementSnapshot, cameras: List<Camera>): FrameStats {
        val node = RenderNode("perf").apply { setPosition(0, 0, viewport.widthPx, viewport.heightPx) }
        val times = ArrayList<Double>()
        val bytesBefore = bytesAllocated()
        val gcBefore = gcCount()
        for (camera in cameras) {
            val start = System.nanoTime()
            val canvas = node.beginRecording()
            val visibleWorld = WorldRect(
                CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0)).x,
                CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0)).y,
                CanvasMath.screenToWorld(camera, viewport, ScreenPoint(viewport.widthPx.toDouble(), viewport.heightPx.toDouble())).x,
                CanvasMath.screenToWorld(camera, viewport, ScreenPoint(viewport.widthPx.toDouble(), viewport.heightPx.toDouble())).y,
            )
            layer.beginFrame()
            layer.draw(canvas, index.visible(snapshot, visibleWorld), emptySet(), camera, viewport)
            node.endRecording()
            times += (System.nanoTime() - start) / 1e6
        }
        val bytes = (bytesAllocated() - bytesBefore) / cameras.size
        node.discardDisplayList()
        times.sort()
        return FrameStats(times[times.size / 2], times[times.size * 95 / 100], bytes, gcCount() - gcBefore)
    }

    private fun Double.f() = "%.2f".format(this)

    @Test
    fun dryLayerFrameCost() {
        val snapshot = fixture()
        val index = ElementIndex().also { it.sync(snapshot) }
        val layer = DryLayer()

        // Whole sheet in view: every stroke is drawn (the worst case before tiles in Task 24).
        val overview = Camera(WorldPoint(10_000.0, 10_000.0), zoom = 1_080.0 / 20_000.0)
        val coldStart = System.nanoTime()
        recordFrames(layer, index, snapshot, listOf(overview))
        val coldMillis = (System.nanoTime() - coldStart) / 1e6
        val overviewStats = recordFrames(layer, index, snapshot, List(60) { overview.copy(center = WorldPoint(10_000.0 + it, 10_000.0)) })

        // Working zoom: a phone-sized window at 1x panning across the sheet.
        val panning = List(240) { Camera(WorldPoint(2_000.0 + it * 60.0, 2_000.0 + it * 60.0), zoom = 1.0) }
        recordFrames(layer, index, snapshot, panning) // tessellate what the pan reaches
        val panStats = recordFrames(layer, index, snapshot, panning)

        Log.i(
            "RendererPerfTest",
            "overview cold ${coldMillis.f()} ms; overview warm median ${overviewStats.medianMillis.f()} ms p95 ${overviewStats.p95Millis.f()} ms " +
                "${overviewStats.bytesPerFrame} B/frame ${overviewStats.gcs} GCs/60 frames; " +
                "pan median ${panStats.medianMillis.f()} ms p95 ${panStats.p95Millis.f()} ms ${panStats.bytesPerFrame} B/frame ${panStats.gcs} GCs/240 frames; " +
                "meshes cached ${layer.cache.size}, sketches ${layer.sketchCount}",
        )
        // Guards from docs/perf/PERF.md. Allocation is the stable signal on a shared emulator; the
        // time limits are loose and only catch a return of the 4.9 s cache thrash.
        assertTrue("overview allocates ${overviewStats.bytesPerFrame} B/frame", overviewStats.bytesPerFrame < 2_000_000)
        assertTrue("overview median ${overviewStats.medianMillis} ms", overviewStats.medianMillis < PerfBudget.millis(400.0))
        assertTrue("pan median ${panStats.medianMillis} ms", panStats.medianMillis < PerfBudget.millis(4.0))
        assertTrue("pan allocates ${panStats.bytesPerFrame} B/frame", panStats.bytesPerFrame < 100_000)
    }

    @Test
    fun perCommitCostOnA5000StrokeSheet() {
        val snapshot = fixture()
        val sheet = io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl(
            io.github.subhaneetshrestha.afnopadd.document.SheetMeta(
                io.github.subhaneetshrestha.afnopadd.document.SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Perf", 0L, 0L,
            ),
            snapshot.elements.values.toList(),
            writer = {},
        )
        sheet.snapshot.value.ordered // the editor has drawn the sheet once before the user commits
        val extra = fixture().elements.values.toList()
        val transacts = ArrayList<Double>()
        val sorts = ArrayList<Double>()
        repeat(40) {
            val element = (extra[it] as StrokeElement).copy(z = FractionalIndex.between(sheet.snapshot.value.ordered.last().z, null))
            var start = System.nanoTime()
            sheet.transact("stroke") { add(element) }
            transacts += (System.nanoTime() - start) / 1e6
            start = System.nanoTime()
            sheet.snapshot.value.ordered
            sorts += (System.nanoTime() - start) / 1e6
        }
        transacts.sort()
        sorts.sort()
        Log.i(
            "RendererPerfTest",
            "per commit on 5,000 strokes: transact median ${transacts[20].f()} ms (map copy), z-order sort median ${sorts[20].f()} ms",
        )
        assertTrue(transacts.isNotEmpty())
    }

    @Test
    fun strokeCacheMemoryAgainstItsEstimate() {
        val snapshot = fixture()
        val cache = StrokeCache(maxBytes = Long.MAX_VALUE)
        val before = settledNativeHeap()
        snapshot.ordered.forEach { cache.strokeFor(it as StrokeElement) }
        val after = settledNativeHeap()
        val count = cache.size
        Log.i(
            "RendererPerfTest",
            "cache ${count} strokes: native ${(after - before) / 1024} KiB (${(after - before) / count} B/stroke), " +
                "estimate ${cache.estimatedBytes / 1024} KiB (${cache.estimatedBytes / count} B/stroke), " +
                "outline vertices ${cache.outlineVerticesBuilt / count} per stroke",
        )
        assertTrue(count == 5_000)
        // Built-only meshes cost about 2/3 of the estimate; drawing adds the renderer's copy.
        assertTrue("estimate ${cache.estimatedBytes} below built native ${after - before}", cache.estimatedBytes >= after - before)
    }

    @Test
    fun softwareRendererMemoryAfterAThumbnail() {
        val snapshot = fixture()
        val renderer = SoftwareRenderer()
        val before = settledNativeHeap()
        val content = RenderGeometry.contentBounds(snapshot)!!
        val fit = RenderGeometry.fit(content, maxEdgePx = 512)
        renderer.renderBitmap(snapshot, fit.bounds, fit.scale).recycle()
        val after = settledNativeHeap()
        // The renderer is a process-wide singleton in the app, so it stays reachable here too.
        Log.i("RendererPerfTest", "native retained after one thumbnail: ${(after - before) / 1024} KiB (renderer ${renderer.hashCode()})")
        assertTrue("retained ${(after - before) / 1024} KiB", after - before < 16L * 1024 * 1024)
    }
}
