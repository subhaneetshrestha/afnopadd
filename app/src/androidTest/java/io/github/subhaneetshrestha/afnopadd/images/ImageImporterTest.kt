package io.github.subhaneetshrestha.afnopadd.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The import pipeline against real files: orientation, alpha, size cap, bad input, and one undoable batch. */
@RunWith(AndroidJUnit4::class)
class ImageImporterTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "image-import-${System.nanoTime()}")
    private val fixtures = File(root, "fixtures").apply { mkdirs() }
    private lateinit var sheet: Sheet
    private val importer = AndroidImageImporter(context)
    private val placement = Placement(center = WorldPoint(0.0, 0.0), frameLevel = 0, shortSideWorld = 1_000.0, gapWorld = 16.0)

    @Before
    fun openSheet() {
        sheet = runBlocking { RoomSheetStore(context).create(File(root, "sheet"), "Images") }
    }

    @After
    fun cleanUp() {
        runBlocking { sheet.close() }
        root.deleteRecursively()
    }

    private fun fixture(name: String, width: Int, height: Int, format: Bitmap.CompressFormat, config: Bitmap.Config = Bitmap.Config.ARGB_8888, paint: (Bitmap) -> Unit): Uri {
        val bitmap = Bitmap.createBitmap(width, height, config)
        paint(bitmap)
        val file = File(fixtures, name)
        file.outputStream().use { bitmap.compress(format, 95, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun storedBitmap(element: ImageElement): Bitmap = BitmapFactory.decodeFile(sheet.assets.file(element.asset).absolutePath)

    private fun images() = sheet.snapshot.value.ordered.filterIsInstance<ImageElement>()

    @Test
    fun anExifRotatedJpegImportsUpright() {
        // Stored sideways: left half red, right half blue, with orientation 6 (turn 90 degrees clockwise to view).
        val uri = fixture("rotated.jpg", 200, 100, Bitmap.CompressFormat.JPEG) { bitmap ->
            for (x in 0 until 200) for (y in 0 until 100) bitmap.setPixel(x, y, if (x < 100) Color.RED else Color.BLUE)
        }
        ExifInterface(File(uri.path!!)).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }

        val summary = runBlocking { importer.import(listOf(uri), sheet, placement) }

        assertEquals(1, summary.imported.size)
        val stored = storedBitmap(images().single())
        assertEquals("upright is portrait", 100, stored.width)
        assertEquals(200, stored.height)
        // Turned clockwise, the red left half is now on top.
        assertTrue(Color.red(stored.getPixel(50, 40)) > 200 && Color.blue(stored.getPixel(50, 40)) < 60)
        assertTrue(Color.blue(stored.getPixel(50, 160)) > 200 && Color.red(stored.getPixel(50, 160)) < 60)
        assertEquals(stored.width.toFloat() / stored.height, images().single().size.width / images().single().size.height, 1e-3f)
    }

    @Test
    fun aPngWithAlphaKeepsItsTransparency() {
        val uri = fixture("alpha.png", 64, 64, Bitmap.CompressFormat.PNG) { bitmap ->
            for (x in 0 until 64) for (y in 0 until 64) bitmap.setPixel(x, y, if (x < 32) Color.TRANSPARENT else Color.GREEN)
        }

        runBlocking { importer.import(listOf(uri), sheet, placement) }

        val stored = storedBitmap(images().single())
        assertEquals(0, Color.alpha(stored.getPixel(10, 10)))
        assertEquals(255, Color.alpha(stored.getPixel(50, 10)))
    }

    @Test
    fun aFortyEightMegapixelJpegLandsAtTheSizeCap() {
        val uri = fixture("big.jpg", 8_000, 6_000, Bitmap.CompressFormat.JPEG, Bitmap.Config.RGB_565) { it.eraseColor(Color.rgb(90, 140, 200)) }

        runBlocking { importer.import(listOf(uri), sheet, placement) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sheet.assets.file(images().single().asset).absolutePath, bounds)
        assertEquals(4_096, bounds.outWidth)
        assertEquals(3_072, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
    }

    @Test
    fun aSmallUprightImageIsStoredAsItCame() {
        val uri = fixture("small.png", 30, 20, Bitmap.CompressFormat.PNG) { it.eraseColor(Color.YELLOW) }

        runBlocking { importer.import(listOf(uri), sheet, placement) }

        assertTrue(File(uri.path!!).readBytes().contentEquals(sheet.assets.file(images().single().asset).readBytes()))
    }

    @Test
    fun anUnsupportedFileFailsAloneAndTheBatchContinues() {
        val text = File(fixtures, "notes.txt").apply { writeText("not an image") }
        val good = fixture("good.png", 40, 40, Bitmap.CompressFormat.PNG) { it.eraseColor(Color.BLACK) }
        val missing = Uri.fromFile(File(fixtures, "missing.jpg"))

        val summary = runBlocking { importer.import(listOf(Uri.fromFile(text), good, missing), sheet, placement) }

        assertEquals(1, summary.imported.size)
        assertEquals(listOf(Uri.fromFile(text).toString(), missing.toString()), summary.failed.map { it.source })
        assertEquals(ImportFailure.UNSUPPORTED, summary.failed[0].reason)
        assertEquals(ImportFailure.UNREADABLE, summary.failed[1].reason)
        assertEquals(1, images().size)
    }

    @Test
    fun aCancelledBatchStopsBetweenImagesAndAddsNone() {
        val uris = (1..5).map { i -> fixture("cancel$i.png", 50 * i, 50, Bitmap.CompressFormat.PNG) { it.eraseColor(Color.GRAY) } }
        var done = 0

        runBlocking {
            val job = launch(kotlinx.coroutines.Dispatchers.Default) {
                importer.import(uris, sheet, placement) { finished, _ ->
                    done = finished
                    if (finished == 1) this.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                }
            }
            job.join()
        }

        assertTrue("stopped early, after $done", done < uris.size)
        assertTrue("half a batch is never added", images().isEmpty())
    }

    @Test
    fun anOversizedSourceIsRefusedWithoutFillingTheCache() {
        // A valid PNG, refused only because it is over the cap: noise does not compress.
        val random = kotlin.random.Random(3)
        val big = fixture("noise.png", 200, 200, Bitmap.CompressFormat.PNG) { bitmap ->
            for (x in 0 until 200) for (y in 0 until 200) bitmap.setPixel(x, y, random.nextInt() or (0xFF shl 24))
        }
        assertTrue("fixture is over the cap", File(big.path!!).length() > 16 * 1024)
        val smallCap = AndroidImageImporter(context, maxSourceBytes = 16 * 1024)

        val summary = runBlocking { smallCap.import(listOf(big), sheet, placement) }

        assertEquals(ImportFailure.UNSUPPORTED, summary.failed.single().reason)
        assertEquals("the same file is fine under the normal cap", 1, runBlocking { importer.import(listOf(big), sheet, placement) }.imported.size)
        assertTrue("no partial copy left behind", File(context.cacheDir, "import").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aBatchIsOneUndoableTransactionLaidOutInARow() {
        val uris = (1..3).map { i -> fixture("row$i.png", 100 * i, 100, Bitmap.CompressFormat.PNG) { it.eraseColor(Color.GRAY) } }

        runBlocking { importer.import(uris, sheet, placement) }

        val placed = images()
        assertEquals(3, placed.size)
        assertTrue("left to right", placed.sortedBy { it.anchor.x }.map { it.id } == placed.map { it.id })
        assertTrue(placed.zipWithNext().all { (a, b) -> b.bounds.left - a.bounds.right in 15.9..16.1 })
        sheet.undo()
        assertTrue(images().isEmpty())
    }
}
