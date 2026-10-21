package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import io.github.subhaneetshrestha.afnopadd.tools.select.Transforms
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/** Images are drawn where and as large as their element says, cropped and turned, at a bitmap size that follows the zoom. */
@RunWith(AndroidJUnit4::class)
class ImageRenderingTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "image-render-${System.nanoTime()}")
    private lateinit var sheet: Sheet
    private var picture: AssetId? = null
    private val asset: AssetId get() = picture!!
    private val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(71))

    /** Answers at once with a solid red bitmap of the asked size, and remembers every size asked for. */
    private class RecordingLoader : ImageBitmapLoader {
        val requested = mutableListOf<Int>()
        override fun load(file: File, maxEdgePx: Int, onLoaded: (Bitmap) -> Unit): () -> Unit {
            requested += maxEdgePx
            onLoaded(createBitmap(maxEdgePx, maxEdgePx / 2).apply { eraseColor(Color.RED) })
            return {}
        }
    }

    @Before
    fun openSheet() {
        sheet = runBlocking { RoomSheetStore(context).create(File(root, "sheet"), "Images") }
        // A 2:1 picture, left half red and right half blue, so crops and turns are visible.
        val halves = createBitmap(200, 100).apply { for (x in 0 until 200) for (y in 0 until 100) setPixel(x, y, if (x < 100) Color.RED else Color.BLUE) }
        val bytes = java.io.ByteArrayOutputStream().also { halves.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        picture = runBlocking { sheet.assets.put(bytes.inputStream(), "image/png", 200, 100) }
    }

    @After
    fun cleanUp() {
        runBlocking { sheet.close() }
        root.deleteRecursively()
    }

    /** A 200 x 100 world-unit image with its top-left at [at]. */
    private fun image(at: WorldPoint, crop: LocalRect? = null, rotationDeg: Float = 0f): ImageElement {
        val size = LocalSize(200f, 100f)
        return ImageElement(
            id = ElementId(ulid.next()), anchor = at, frameLevel = 0, z = FractionalIndex.first(),
            bounds = Transforms.imageBounds(at, 0, size, rotationDeg, crop),
            version = 1, versionNonce = 1, createdAt = 1L, updatedAt = 1L,
            asset = asset, size = size, rotationDeg = rotationDeg, crop = crop,
        )
    }

    private fun show(element: ImageElement, controller: CameraController, loader: ImageBitmapLoader? = null) {
        sheet.transact("Add") { add(element) }
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(sheet, controller, object : CanvasInputSink { override fun onPointer(event: CanvasPointerEvent) = Unit }, Modifier.fillMaxSize().testTag(TAG), imageLoader = loader)
            }
        }
        rule.waitForIdle()
    }

    private fun capture(): Bitmap = rule.onNodeWithTag(TAG).captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)

    private fun Bitmap.isRed(x: Int, y: Int) = getPixel(x, y).let { Color.red(it) > 200 && Color.green(it) < 80 && Color.blue(it) < 80 }
    private fun Bitmap.isBlue(x: Int, y: Int) = getPixel(x, y).let { Color.blue(it) > 200 && Color.red(it) < 80 && Color.green(it) < 80 }
    private fun Bitmap.isPaper(x: Int, y: Int) = getPixel(x, y).let { Color.red(it) > 230 && Color.green(it) > 230 && Color.blue(it) > 230 }

    @Test
    fun anImageFillsItsFrameAtEveryZoom() {
        // Centred on the origin at 1x: the image covers screen (200, 150)..(400, 250).
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), 1.0))
        show(image(WorldPoint(-100.0, -50.0)), controller, RecordingLoader())

        var frame = capture()
        assertTrue(frame.isRed(210, 160) && frame.isRed(390, 240))
        assertTrue(frame.isPaper(190, 200) && frame.isPaper(410, 200) && frame.isPaper(300, 140) && frame.isPaper(300, 260))

        rule.runOnUiThread { controller.set(Camera(WorldPoint(0.0, 0.0), 2.0)) }
        rule.waitForIdle()
        frame = capture()
        // At 2x it covers (100, 100)..(500, 300).
        assertTrue(frame.isRed(110, 110) && frame.isRed(490, 290))
        assertTrue(frame.isPaper(90, 200) && frame.isPaper(510, 200))
    }

    @Test
    fun zoomingOutAsksForSmallerBitmaps() {
        val loader = RecordingLoader()
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), 4.0))
        show(image(WorldPoint(-100.0, -50.0)), controller, loader)

        for (zoom in listOf(1.0, 0.25, 0.05)) {
            rule.runOnUiThread { controller.set(Camera(WorldPoint(0.0, 0.0), zoom)) }
            rule.waitForIdle()
        }

        // 800, 200, 50 and 10 px on screen.
        assertEquals(listOf(1_024, 256, 64), loader.requested.distinct())
    }

    @Test
    fun aCropShowsOnlyTheCroppedPart() {
        // Keep only the right (blue) half: local x 100..200, drawn at screen 300..400.
        show(image(WorldPoint(-100.0, -50.0), crop = LocalRect(100f, 0f, 200f, 100f)), CameraController(Camera(WorldPoint(0.0, 0.0), 1.0)))
        rule.waitUntil(timeoutMillis = 5_000) { capture().isBlue(350, 200) }

        val frame = capture()
        assertTrue("the cropped-away red half is gone", frame.isPaper(250, 200))
        assertTrue(frame.isBlue(390, 240))
    }

    @Test
    fun aTurnedImageIsDrawnTurned() {
        // A quarter turn about the centre (0, 0): red on top now, blue below; 100 wide, 200 tall.
        show(image(WorldPoint(-100.0, -50.0), rotationDeg = 90f), CameraController(Camera(WorldPoint(0.0, 0.0), 1.0)))
        rule.waitUntil(timeoutMillis = 5_000) { capture().isRed(300, 150) }

        val frame = capture()
        assertTrue(frame.isBlue(300, 250))
        assertTrue("narrower now", frame.isPaper(240, 200))
    }

    private companion object {
        const val TAG = "image-canvas"
    }
}
