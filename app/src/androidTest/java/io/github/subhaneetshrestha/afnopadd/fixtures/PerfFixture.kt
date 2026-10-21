package io.github.subhaneetshrestha.afnopadd.fixtures

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * The shared performance fixture (Task 25): 5,000 hand-like strokes of 60 points scattered over a
 * 20,000-unit square, the same as the renderer measurements in `docs/perf/PERF.md`, and 20 images
 * of 1,024 x 768 px spread over the same square. Deterministic: the same seed gives the same sheet.
 */
object PerfFixture {
    const val SIDE = 20_000.0
    const val STROKES = 5_000
    const val IMAGES = 20

    /** The whole fixture fits this rectangle. */
    val BOUNDS = WorldRect(0.0, 0.0, SIDE, SIDE)

    fun strokes(count: Int = STROKES, seed: Int = 5): List<StrokeElement> {
        val random = Random(seed)
        val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(seed + 72))
        var z: FractionalIndex? = null
        return List(count) {
            z = FractionalIndex.between(z, null)
            val anchor = WorldPoint(random.nextDouble(0.0, SIDE), random.nextDouble(0.0, SIDE))
            val points = 60
            val xs = FloatArray(points)
            val ys = FloatArray(points)
            for (i in 1 until points) {
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
                points = StrokePoints.of(xs, ys, FloatArray(points) { 0.6f }, FloatArray(points), FloatArray(points), FloatArray(points) { it * 8f }),
            )
        }
    }

    /** The 20 images, stored in [sheet]'s assets, on top of whatever [after] is. */
    suspend fun images(sheet: Sheet, after: FractionalIndex?, seed: Int = 9): List<ImageElement> {
        val random = Random(seed)
        val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(seed + 11))
        var z = after
        return List(IMAGES) { i ->
            val picture = createBitmap(1_024, 768).apply { eraseColor(Color.rgb(40 + i * 10, 120, 200 - i * 5)) }
            val bytes = ByteArrayOutputStream().also { picture.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
            picture.recycle()
            val asset = sheet.assets.put(bytes.inputStream(), "image/jpeg", 1_024, 768)
            z = FractionalIndex.between(z, null)
            val anchor = WorldPoint(random.nextDouble(0.0, SIDE - 1_024), random.nextDouble(0.0, SIDE - 768))
            ImageElement(
                id = ElementId(ulid.next()), anchor = anchor, frameLevel = 0, z = z!!,
                bounds = WorldRect(anchor.x, anchor.y, anchor.x + 1_024, anchor.y + 768),
                version = 1, versionNonce = 1, createdAt = 1L, updatedAt = 1L,
                asset = asset, size = LocalSize(1_024f, 768f), rotationDeg = 0f, crop = null,
            )
        }
    }

    /** Adds the strokes and images to [sheet] in one transaction, as a sheet that was drawn over time would hold them. */
    suspend fun fill(sheet: Sheet): List<Element> {
        val strokes = strokes()
        val images = images(sheet, strokes.last().z)
        val all = strokes + images
        sheet.transact("Fixture") { all.forEach { add(it) } }
        return all
    }
}
