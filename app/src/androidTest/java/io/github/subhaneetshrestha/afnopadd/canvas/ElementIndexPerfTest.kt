package io.github.subhaneetshrestha.afnopadd.canvas

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.PerfBudget
import io.github.subhaneetshrestha.afnopadd.document.DeviceFixtures
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** The per-frame culling cost while panning a 5,000-stroke sheet, which must leave the frame to drawing. */
@RunWith(AndroidJUnit4::class)
class ElementIndexPerfTest {

    @Test
    fun cullingA5000StrokeSheetTakesUnderAMillisecondPerFrame() {
        val random = Random(3)
        var z: FractionalIndex? = null
        val elements = (0 until 5_000).map {
            z = FractionalIndex.between(z, null)
            val x = random.nextDouble(0.0, 20_000.0)
            val y = random.nextDouble(0.0, 20_000.0)
            DeviceFixtures.stroke(z = z!!, seed = it).copy(anchor = WorldPoint(x, y), bounds = WorldRect(x, y, x + 100.0, y + 100.0))
        }
        val snapshot = ElementSnapshot(elements.associateBy { it.id })
        // A debug build starts out interpreted, so the first build is logged but the budget applies
        // to a warm one, which is what a release build's compiled code resembles.
        val builds = (0 until 4).map {
            val start = System.nanoTime()
            ElementIndex().sync(snapshot)
            (System.nanoTime() - start) / 1e6
        }
        val coldBuildMillis = builds.first()
        val buildMillis = builds.last()
        val index = ElementIndex().also { it.sync(snapshot) }

        // Pan a phone-sized viewport diagonally across the sheet, one frame at a time.
        val frames = (0 until 240).map { frame ->
            val left = frame * 60.0
            val rect = WorldRect(left, left, left + 1_080.0, left + 2_400.0)
            val start = System.nanoTime()
            val visible = index.visible(snapshot, rect)
            val elapsed = (System.nanoTime() - start) / 1e6
            check(visible.isNotEmpty())
            elapsed
        }.sorted()
        val median = frames[frames.size / 2]
        val p95 = frames[frames.size * 95 / 100]

        // Committed strokes, one transaction each: every sync touches only the new element.
        var current = snapshot
        val syncs = (0 until 50).map {
            z = FractionalIndex.between(z, null)
            val added = DeviceFixtures.stroke(z = z!!, seed = 10_000 + it)
            current = current.next(current.elements + (added.id to added), setOf(added.id))
            val start = System.nanoTime()
            index.sync(current)
            (System.nanoTime() - start) / 1e6
        }.sorted()
        val syncMedian = syncs[syncs.size / 2]
        Log.i(
            "ElementIndexPerfTest",
            "build cold ${"%.1f".format(coldBuildMillis)} ms, warm ${"%.1f".format(buildMillis)} ms, culling median ${"%.3f".format(median)} ms, " +
                "p95 ${"%.3f".format(p95)} ms, sync after one stroke median ${"%.3f".format(syncMedian)} ms",
        )

        assertTrue("Warm index build took $buildMillis ms", buildMillis < PerfBudget.millis(50.0))
        assertTrue("Median culling took $median ms", median < PerfBudget.millis(1.0))
        assertTrue("Median sync took $syncMedian ms", syncMedian < PerfBudget.millis(0.5))
    }
}
