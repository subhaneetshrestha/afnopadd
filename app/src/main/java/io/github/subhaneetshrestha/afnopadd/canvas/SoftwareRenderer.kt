package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withMatrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import io.github.subhaneetshrestha.afnopadd.canvas.internal.BackgroundPainter
import io.github.subhaneetshrestha.afnopadd.canvas.internal.InkBridge.toAndroidMatrix
import io.github.subhaneetshrestha.afnopadd.canvas.internal.StrokeCache
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.floor
import kotlin.math.min

/**
 * [Renderer] on a software canvas. Mirrors the on-screen dry layer (same Ink renderer, same frame
 * transforms) so a thumbnail or export looks like the screen. Safe to call from any thread; calls
 * are serialised because the stroke cache is not thread-safe. The cache only lives for one call
 * (all tiles of a tiled render share it): this renderer is process-wide, and keeping the meshes of
 * the last thumbnailed sheet held about 34 MB of native memory for a 5,000-stroke sheet.
 */
class SoftwareRenderer(private val maxBitmapPixels: Long = DEFAULT_MAX_BITMAP_PIXELS) : Renderer {

    private val lock = Any()
    private val strokeRenderer = CanvasStrokeRenderer.create()
    private val strokeCache = StrokeCache(maxBytes = 16L * 1024 * 1024)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9E9E9E.toInt()
        strokeWidth = 1f
    }
    private val linePaint = Paint().apply { color = 0xFFD0D0D0.toInt(); strokeWidth = 1f }

    override fun renderBitmap(snapshot: ElementSnapshot, bounds: WorldRect, scalePxPerUnit: Double, background: Background): Bitmap {
        val size = RenderGeometry.pixelSize(bounds, scalePxPerUnit)
        require(size.width.toLong() * size.height <= maxBitmapPixels) {
            "A ${size.width}x${size.height} render exceeds $maxBitmapPixels pixels; use renderTiled"
        }
        val bitmap = createBitmap(size.width, size.height)
        renderTo(Canvas(bitmap), snapshot, bounds, scalePxPerUnit, background)
        return bitmap
    }

    override fun renderTo(canvas: Canvas, snapshot: ElementSnapshot, bounds: WorldRect, scalePxPerUnit: Double, background: Background) {
        synchronized(lock) {
            try {
                renderRegion(canvas, snapshot, bounds, scalePxPerUnit, background)
            } finally {
                strokeCache.clear()
            }
        }
    }

    private fun renderRegion(canvas: Canvas, snapshot: ElementSnapshot, bounds: WorldRect, scalePxPerUnit: Double, background: Background) {
        val size = RenderGeometry.pixelSize(bounds, scalePxPerUnit)
        drawBackground(canvas, background, bounds, scalePxPerUnit, size)
        for (element in snapshot.ordered) {
            if (element.bounds.right < bounds.left || element.bounds.left > bounds.right ||
                element.bounds.bottom < bounds.top || element.bounds.top > bounds.bottom
            ) {
                continue
            }
            if (element is StrokeElement) {
                val matrix = CanvasMath.frameToRegion(bounds, scalePxPerUnit, element.anchor, element.frameLevel).toAndroidMatrix()
                val stroke = strokeCache.strokeFor(element)
                canvas.withMatrix(matrix) { strokeRenderer.draw(this, stroke, matrix) }
            } else {
                canvas.drawRect(
                    ((element.bounds.left - bounds.left) * scalePxPerUnit).toFloat(),
                    ((element.bounds.top - bounds.top) * scalePxPerUnit).toFloat(),
                    ((element.bounds.right - bounds.left) * scalePxPerUnit).toFloat(),
                    ((element.bounds.bottom - bounds.top) * scalePxPerUnit).toFloat(),
                    outlinePaint,
                )
            }
        }
    }

    override fun renderTiled(
        snapshot: ElementSnapshot,
        bounds: WorldRect,
        scalePxPerUnit: Double,
        background: Background,
        tileSizePx: Int,
        onTile: (RenderTile) -> Unit,
    ) {
        require(tileSizePx > 0) { "Tile size must be positive" }
        synchronized(lock) {
            try {
                renderTiles(snapshot, bounds, scalePxPerUnit, background, tileSizePx, onTile)
            } finally {
                strokeCache.clear()
            }
        }
    }

    private fun renderTiles(
        snapshot: ElementSnapshot,
        bounds: WorldRect,
        scalePxPerUnit: Double,
        background: Background,
        tileSizePx: Int,
        onTile: (RenderTile) -> Unit,
    ) {
        val size = RenderGeometry.pixelSize(bounds, scalePxPerUnit)
        var top = 0
        while (top < size.height) {
            var left = 0
            val height = min(tileSizePx, size.height - top)
            while (left < size.width) {
                val width = min(tileSizePx, size.width - left)
                val tileBounds = WorldRect(
                    left = bounds.left + left / scalePxPerUnit,
                    top = bounds.top + top / scalePxPerUnit,
                    right = bounds.left + (left + width) / scalePxPerUnit,
                    bottom = bounds.top + (top + height) / scalePxPerUnit,
                )
                val bitmap = createBitmap(width, height)
                try {
                    renderRegion(Canvas(bitmap), snapshot, tileBounds, scalePxPerUnit, background)
                    onTile(RenderTile(left, top, bitmap))
                } finally {
                    bitmap.recycle()
                }
                left += width
            }
            top += height
        }
    }

    private fun drawBackground(canvas: Canvas, background: Background, bounds: WorldRect, scale: Double, size: PixelSize) {
        canvas.drawColor(Color.WHITE)
        if (background == Background.PLAIN) return
        val spacing = BackgroundPainter.spacingWorldUnits(scale)
        val xs = generateSequence(floor(bounds.left / spacing) * spacing) { it + spacing }.takeWhile { it <= bounds.right }
            .map { ((it - bounds.left) * scale).toFloat() }.toList()
        val ys = generateSequence(floor(bounds.top / spacing) * spacing) { it + spacing }.takeWhile { it <= bounds.bottom }
            .map { ((it - bounds.top) * scale).toFloat() }.toList()
        when (background) {
            Background.GRID -> {
                xs.forEach { canvas.drawLine(it, 0f, it, size.height.toFloat(), linePaint) }
                ys.forEach { canvas.drawLine(0f, it, size.width.toFloat(), it, linePaint) }
            }
            Background.DOTS -> xs.forEach { x -> ys.forEach { y -> canvas.drawCircle(x, y, 1.5f, linePaint) } }
            Background.PLAIN -> Unit
        }
    }

    private companion object {
        const val DEFAULT_MAX_BITMAP_PIXELS = 100_000_000L
    }
}
