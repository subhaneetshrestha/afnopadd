package io.github.subhaneetshrestha.afnopadd.canvas.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.withMatrix
import androidx.core.graphics.withSave
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.internal.InkBridge.toAndroidMatrix
import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import java.io.File
import java.io.IOException
import kotlin.math.max

/**
 * Draws elements of a world [region][draw] onto a software canvas at a fixed scale, the way the
 * screen does: strokes through Ink with each element's frame transform, shapes as paths, images
 * decoded synchronously at the size they are drawn (when [fileFor] can find their files; else a
 * placeholder outline). Shared by off-screen renders and the tile pyramid. Not thread-safe: one per
 * thread, and [clear] lets go of cached meshes and bitmaps.
 */
internal class RegionPainter(
    private val fileFor: ((AssetId) -> File?)? = null,
    strokeCacheBytes: Long = 16L * 1024 * 1024,
    imageCacheBytes: Int = 16 * 1024 * 1024,
) {
    private val strokeRenderer = CanvasStrokeRenderer.create()
    private val strokeCache = StrokeCache(maxBytes = strokeCacheBytes)
    private val shapePainter = ShapePainter()
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9E9E9E.toInt()
        strokeWidth = 1f
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frameRect = RectF()

    /** Decoded images by asset and bucket size. */
    private val images = object : LruCache<String, Bitmap>(imageCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun draw(canvas: Canvas, elements: List<Element>, region: WorldRect, scalePxPerUnit: Double) {
        for (element in elements) {
            if (element.bounds.right < region.left || element.bounds.left > region.right ||
                element.bounds.bottom < region.top || element.bounds.top > region.bottom
            ) {
                continue
            }
            val transform = CanvasMath.frameToRegion(region, scalePxPerUnit, element.anchor, element.frameLevel)
            when (element) {
                is StrokeElement -> {
                    val matrix = transform.toAndroidMatrix()
                    val stroke = strokeCache.strokeFor(element)
                    canvas.withMatrix(matrix) { strokeRenderer.draw(this, stroke, matrix) }
                }
                is ShapeElement -> shapePainter.draw(canvas, element, transform)
                is ImageElement -> drawImage(canvas, element, transform.scale.toFloat(), transform.translateX.toFloat(), transform.translateY.toFloat(), region, scalePxPerUnit)
                else -> canvas.drawRect(
                    ((element.bounds.left - region.left) * scalePxPerUnit).toFloat(),
                    ((element.bounds.top - region.top) * scalePxPerUnit).toFloat(),
                    ((element.bounds.right - region.left) * scalePxPerUnit).toFloat(),
                    ((element.bounds.bottom - region.top) * scalePxPerUnit).toFloat(),
                    outlinePaint,
                )
            }
        }
    }

    fun clear() {
        strokeCache.clear()
        images.evictAll()
    }

    private fun drawImage(canvas: Canvas, image: ImageElement, scale: Float, tx: Float, ty: Float, region: WorldRect, scalePxPerUnit: Double) {
        val bitmap = imageBitmap(image, max(image.size.width, image.size.height) * scale.toDouble())
        if (bitmap == null) {
            canvas.drawRect(
                ((image.bounds.left - region.left) * scalePxPerUnit).toFloat(),
                ((image.bounds.top - region.top) * scalePxPerUnit).toFloat(),
                ((image.bounds.right - region.left) * scalePxPerUnit).toFloat(),
                ((image.bounds.bottom - region.top) * scalePxPerUnit).toFloat(),
                outlinePaint,
            )
            return
        }
        val crop = image.crop
        canvas.withSave {
            translate(tx, ty)
            scale(scale, scale)
            if (image.rotationDeg != 0f) rotate(image.rotationDeg, image.size.width / 2, image.size.height / 2)
            if (crop != null) clipRect(crop.left, crop.top, crop.right, crop.bottom)
            frameRect.set(0f, 0f, image.size.width, image.size.height)
            drawBitmap(bitmap, null, frameRect, bitmapPaint)
        }
    }

    /** The image decoded to fit [screenLongEdgePx]'s bucket, or null without a readable file. */
    private fun imageBitmap(image: ImageElement, screenLongEdgePx: Double): Bitmap? {
        val files = fileFor ?: return null
        val bucket = ImageBuckets.bucketFor(screenLongEdgePx)
        val key = "${image.asset.sha256Hex}@$bucket"
        images.get(key)?.let { return it }
        val file = files(image.asset)?.takeIf { it.isFile } ?: return null
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > bucket) {
                    val f = bucket.toDouble() / longEdge
                    decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: IOException) {
            return null
        }
        images.put(key, bitmap)
        return bitmap
    }
}
