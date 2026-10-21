package io.github.subhaneetshrestha.afnopadd.images

import android.graphics.Bitmap
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.PerfBudget
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * SPEC-image-board success criterion 1: twenty 12-megapixel photos import in under 10 s with peak
 * memory under 300 MB. Half the photos carry EXIF orientation 6, as portrait phone photos do, so
 * they take the full decode-and-re-encode path; the rest are stored as they came.
 */
@RunWith(AndroidJUnit4::class)
class ImageImportPerfTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "image-perf-${System.nanoTime()}")

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    /** A 4,000 x 3,000 photo-like JPEG; [seed] makes each one's bytes different, so none is deduplicated. */
    private fun photo(seed: Int, portrait: Boolean): Uri {
        val width = 4_000
        val height = 3_000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) row[x] = (0xFF shl 24) or (((x + seed * 7) and 0xFF) shl 16) or (((y * 3 + x / 5) and 0xFF) shl 8) or ((x * y + seed) and 0xFF)
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        val file = File(root, "photo$seed.jpg").also { it.parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (portrait) ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        return Uri.fromFile(file)
    }

    private fun usedMegabytes(): Long {
        val runtime = Runtime.getRuntime()
        return (Debug.getNativeHeapAllocatedSize() + runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    @Test
    fun twentyTwelveMegapixelPhotosImportInTimeAndWithinMemory() {
        val uris = (1..20).map { photo(it, portrait = it % 2 == 0) }
        val sheet = runBlocking { RoomSheetStore(context).create(File(root, "sheet"), "Perf") }
        Runtime.getRuntime().gc()
        val baseline = usedMegabytes()
        val peakMegabytes = AtomicLong(baseline)
        val sampling = AtomicBoolean(true)
        val sampler = thread(name = "memory-sampler") {
            while (sampling.get()) {
                peakMegabytes.accumulateAndGet(usedMegabytes(), ::maxOf)
                Thread.sleep(10)
            }
        }

        val start = SystemClock.elapsedRealtime()
        val summary = runBlocking { AndroidImageImporter(context).import(uris, sheet, Placement(WorldPoint(0.0, 0.0), 0, 1_000.0, 16.0)) }
        val elapsed = SystemClock.elapsedRealtime() - start
        sampling.set(false)
        sampler.join()
        val peak = peakMegabytes.get()
        runBlocking { sheet.close() }

        Log.i("ImageImportPerfTest", "20 x 12 MP: $elapsed ms, peak ${peak} MB (baseline $baseline MB, +${peak - baseline} MB)")
        assertEquals(20, summary.imported.size)
        assertTrue("took $elapsed ms", elapsed < PerfBudget.millis(10_000L))
        assertTrue("peak $peak MB", peak < 300)
    }
}
