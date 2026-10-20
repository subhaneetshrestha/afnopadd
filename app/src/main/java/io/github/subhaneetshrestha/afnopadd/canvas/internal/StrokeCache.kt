package io.github.subhaneetshrestha.afnopadd.canvas.internal

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import kotlin.math.roundToLong

/**
 * Builds Ink [Stroke]s (input batch plus tessellated mesh) from stored [StrokePoints] on demand
 * and keeps the most recently drawn ones, up to a byte budget. Meshes are never persisted: this
 * cache is the only place they live, and evicting one just means re-tessellating it next time.
 * Used from the UI thread only.
 */
internal class StrokeCache(private val maxBytes: Long = DEFAULT_MAX_BYTES) {

    private class Entry(val points: StrokePoints, val brushKey: Any, val stroke: Stroke, val bytes: Long)

    private val entries = object : LinkedHashMap<ElementId, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ElementId, Entry>): Boolean = false
    }

    var estimatedBytes: Long = 0
        private set

    val size: Int get() = entries.size

    fun strokeFor(element: StrokeElement): Stroke {
        val cached = entries[element.id]
        if (cached != null && cached.points === element.points && cached.brushKey == element.brush) return cached.stroke
        val stroke = build(element)
        val bytes = estimateBytes(element.points)
        cached?.let { estimatedBytes -= it.bytes }
        entries[element.id] = Entry(element.points, element.brush, stroke, bytes)
        estimatedBytes += bytes
        evict()
        return stroke
    }

    fun remove(id: ElementId) {
        entries.remove(id)?.let { estimatedBytes -= it.bytes }
    }

    fun clear() {
        entries.clear()
        estimatedBytes = 0
    }

    private fun evict() {
        val iterator = entries.entries.iterator()
        while (estimatedBytes > maxBytes && entries.size > 1 && iterator.hasNext()) {
            val eldest = iterator.next()
            iterator.remove()
            estimatedBytes -= eldest.value.bytes
        }
    }

    private fun build(element: StrokeElement): Stroke {
        val points = element.points
        val batch = MutableStrokeInputBatch()
        val input = StrokeInput()
        var lastTime = 0L
        for (i in 0 until points.count) {
            val time = points.timeMillis(i).roundToLong().coerceAtLeast(lastTime)
            lastTime = time
            input.update(
                x = points.x(i),
                y = points.y(i),
                elapsedTimeMillis = time,
                toolType = InputToolType.STYLUS,
                pressure = points.pressure(i).coerceIn(0f, 1f),
                tiltRadians = points.tiltRad(i),
                orientationRadians = points.orientationRad(i),
            )
            batch.add(input)
        }
        return Stroke(InkBridge.toInkBrush(element.brush), batch)
    }

    private fun estimateBytes(points: StrokePoints): Long = BASE_BYTES + points.count.toLong() * BYTES_PER_POINT

    private companion object {
        const val DEFAULT_MAX_BYTES = 48L * 1024 * 1024
        /** Inputs (24 bytes) plus a tessellated mesh of a few vertices per sample. */
        const val BYTES_PER_POINT = 160L
        const val BASE_BYTES = 512L
    }
}
