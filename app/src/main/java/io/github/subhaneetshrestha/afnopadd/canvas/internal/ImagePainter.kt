package io.github.subhaneetshrestha.afnopadd.canvas.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.withSave
import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.ImageBitmapLoader
import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import java.io.File
import kotlin.math.max

/**
 * Draws images on screen (SPEC-image-board decision 6). Each asset keeps one bitmap, sized to the
 * [ImageBuckets] bucket of how large the image is on screen; when the bucket changes (a zoom), a
 * new size is requested and the old bitmap is drawn, scaled, until it arrives, so pinching never
 * waits on a decode. The image is drawn in its own frame units: turned about its centre, clipped
 * to its crop, filtered bilinearly. Until a first bitmap arrives, a placeholder is drawn.
 *
 * Bitmaps are kept within [maxBytes]; beyond it, assets not drawn in the current frame are dropped,
 * least recently drawn first. Used from the UI thread only.
 */
internal class ImagePainter(
    private val loader: ImageBitmapLoader,
    private val fileFor: (AssetId) -> File?,
    private val onLoaded: () -> Unit,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private class Entry {
        var bitmap: Bitmap? = null
        var bucket = 0
        var pendingBucket = 0
        var cancel: (() -> Unit)? = null
        var lastFrame = 0L
    }

    private val entries = LinkedHashMap<AssetId, Entry>(16, 0.75f, true)
    private var frame = 0L

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderFill = Paint().apply { color = 0xFFEEEEEE.toInt() }
    private val placeholderOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9E9E9E.toInt()
    }
    private val frameRect = RectF()

    /** Bytes of bitmaps currently held. */
    val heldBytes: Long get() = entries.values.sumOf { it.bitmap?.allocationByteCount?.toLong() ?: 0L }

    fun beginFrame() {
        frame++
    }

    fun draw(canvas: Canvas, image: ImageElement, transform: FrameTransform) {
        val scale = transform.scale.toFloat()
        val entry = entryFor(image, max(image.size.width, image.size.height) * transform.scale)
        val visible = image.crop
        canvas.withSave {
            translate(transform.translateX.toFloat(), transform.translateY.toFloat())
            scale(scale, scale)
            if (image.rotationDeg != 0f) rotate(image.rotationDeg, image.size.width / 2, image.size.height / 2)
            if (visible != null) clipRect(visible.left, visible.top, visible.right, visible.bottom)
            frameRect.set(0f, 0f, image.size.width, image.size.height)
            val bitmap = entry?.bitmap
            if (bitmap != null) {
                drawBitmap(bitmap, null, frameRect, bitmapPaint)
            } else {
                drawRect(frameRect, placeholderFill)
                placeholderOutline.strokeWidth = 1f / scale
                drawRect(frameRect, placeholderOutline)
            }
        }
    }

    /** Cancels every load in flight and drops every bitmap. */
    fun release() {
        entries.values.forEach { it.cancel?.invoke() }
        entries.clear()
    }

    private fun entryFor(image: ImageElement, screenLongEdgePx: Double): Entry? {
        val bucket = ImageBuckets.bucketFor(screenLongEdgePx)
        val entry = entries.getOrPut(image.asset) { Entry() }
        entry.lastFrame = frame
        if (entry.bucket != bucket && entry.pendingBucket != bucket) {
            val file = fileFor(image.asset) ?: return entry
            entry.cancel?.invoke()
            entry.pendingBucket = bucket
            val cancel = loader.load(file, bucket) { bitmap ->
                if (entry.pendingBucket != bucket) return@load
                entry.bitmap = bitmap
                entry.bucket = bucket
                entry.pendingBucket = 0
                entry.cancel = null
                trim()
                onLoaded()
            }
            // A loader may answer before returning (a memory-cache hit); then there is nothing to cancel.
            if (entry.pendingBucket == bucket) entry.cancel = cancel
        }
        return entry
    }

    private fun trim() {
        var held = heldBytes
        val iterator = entries.entries.iterator()
        while (held > maxBytes && iterator.hasNext()) {
            val (_, entry) = iterator.next()
            if (entry.lastFrame == frame) continue
            held -= entry.bitmap?.allocationByteCount?.toLong() ?: 0L
            entry.cancel?.invoke()
            iterator.remove()
        }
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 96L * 1024 * 1024
    }
}
