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
 *
 * A caller that draws frames calls [beginFrame] first. Within a frame, meshes already drawn are
 * never evicted to make room: when more strokes are on screen than the budget holds, the overflow
 * is built, drawn and dropped instead. Plain LRU would evict each mesh just before the next frame
 * needs it and rebuild everything every frame. Callers that never begin frames get plain LRU.
 */
internal class StrokeCache(private val maxBytes: Long = DEFAULT_MAX_BYTES) {

    private class Entry(val points: StrokePoints, val brushKey: Any, val stroke: Stroke, val bytes: Long, var frame: Long)

    private val entries = object : LinkedHashMap<ElementId, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ElementId, Entry>): Boolean = false
    }

    /** The current frame; 0 until [beginFrame] is first called, which leaves the cache plain LRU. */
    private var frame = 0L

    var estimatedBytes: Long = 0
        private set

    val size: Int get() = entries.size

    /** How many strokes this cache has tessellated, cached or not; for tests and the perf ledger. */
    var buildCount: Long = 0
        private set

    fun beginFrame() {
        frame++
    }

    fun strokeFor(element: StrokeElement): Stroke {
        val cached = entries[element.id]
        if (cached != null && cached.points === element.points && cached.brushKey == element.brush) {
            cached.frame = frame
            return cached.stroke
        }
        val stroke = build(element)
        val bytes = estimateBytes(stroke, element.points)
        cached?.let {
            entries.remove(element.id)
            estimatedBytes -= it.bytes
        }
        evict(toFit = bytes)
        if (estimatedBytes + bytes <= maxBytes || entries.isEmpty()) {
            entries[element.id] = Entry(element.points, element.brush, stroke, bytes, frame)
            estimatedBytes += bytes
        }
        return stroke
    }

    fun clear() {
        entries.clear()
        estimatedBytes = 0
    }

    /** Evicts least recently used entries until [toFit] more bytes fit, sparing those drawn in the current frame. */
    private fun evict(toFit: Long) {
        val iterator = entries.entries.iterator()
        while (estimatedBytes + toFit > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            if (frame > 0 && eldest.value.frame == frame) continue
            iterator.remove()
            estimatedBytes -= eldest.value.bytes
        }
    }

    private fun build(element: StrokeElement): Stroke {
        buildCount++
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

    /** Outline vertices of every stroke built so far; for calibrating [BYTES_PER_OUTLINE_VERTEX]. */
    var outlineVerticesBuilt: Long = 0
        private set

    /**
     * Mesh memory follows the stroke's outline: Ink's public API reports outline vertices (the
     * vertex and triangle counts of the meshes themselves are restricted to the Ink library).
     */
    private fun estimateBytes(stroke: Stroke, points: StrokePoints): Long {
        val shape = stroke.shape
        var outlineVertices = 0L
        for (group in 0 until shape.getRenderGroupCount()) {
            for (outline in 0 until shape.getOutlineCount(group)) outlineVertices += shape.getOutlineVertexCount(group, outline)
        }
        outlineVerticesBuilt += outlineVertices
        return BASE_BYTES + outlineVertices * BYTES_PER_OUTLINE_VERTEX + points.count.toLong() * INPUT_BYTES_PER_POINT
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 48L * 1024 * 1024
        const val BASE_BYTES = 512L
        /** Ink's input batch keeps every sample (position, time, pressure, tilt, orientation). */
        const val INPUT_BYTES_PER_POINT = 32L
        /**
         * Native memory per outline vertex, built and drawn. Measured on the API 36 emulator for
         * 5,000 60-point strokes (280 outline vertices each), which cost 17.8 KB each once built and
         * about 27 KB once drawn through `CanvasStrokeRenderer` (its own GPU-side copy); see
         * docs/perf/PERF.md.
         */
        const val BYTES_PER_OUTLINE_VERTEX = 90L
    }
}
