package io.github.subhaneetshrestha.afnopadd.canvas.internal

import androidx.compose.ui.geometry.Offset
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.StrokeInput
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasInputSink
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.FinishedStroke
import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeFrame
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.canvas.internal.InkBridge.toAndroidMatrix
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints

/**
 * Sits between the gesture handler and the tool. Every pointer event is forwarded downstream; the
 * ones that start, extend or cancel a stroke also drive Jetpack Ink's [InProgressStrokesView]
 * through its `StrokeInput` API, in the zoom frame fixed at the stroke's first sample.
 *
 * Ink only ever draws the stroke while the pen is down. At pen-up the stroke goes to the tool,
 * built from the samples recorded here, and is committed at once; the dry layer draws it in the
 * next frame and Ink's copy is then cancelled ([releaseAfterDraw]), so the stroke never blinks.
 * Ink is never asked to finish a stroke and hand it over, so that leaving the editor or rotating
 * right after lifting the pen cannot lose the stroke. (Ink 1.0.0 also crashes when its view is
 * detached within about half a second of drawing, which is why the view itself is never detached
 * while its activity lives; see InkHost and ADR 0003.)
 *
 * One router lives as long as its canvas; [downstream] is read when a gesture starts, and that
 * gesture's events and its finished stroke go to that sink.
 */
internal class WetStrokeRouter(
    private val downstream: () -> CanvasInputSink,
    private val cameraController: CameraController,
    private val viewport: () -> Viewport,
    private val wetStrokeBrush: (StrokeFrame) -> BrushSpec?,
    private val onReleasePending: () -> Unit = {},
    /** Where the canvas's origin is in [view]'s pixels; the hosted view covers the whole window (InkHost). */
    private val canvasOffsetInView: (InProgressStrokesView) -> Offset = { Offset.Zero },
) : CanvasInputSink {

    private class ActiveStroke(
        val sink: CanvasInputSink,
        val id: InProgressStrokeId,
        val frame: StrokeFrame,
        val brush: BrushSpec,
        val kind: PointerKind,
        val downTimeMillis: Long,
        var lastElapsedMillis: Long = 0L,
        val samples: Samples = Samples(),
    )

    /** The samples fed to Ink, kept so the finished stroke is built without Ink. */
    private class Samples {
        private var size = 0
        private var x = FloatArray(64)
        private var y = FloatArray(64)
        private var pressure = FloatArray(64)
        private var time = FloatArray(64)

        /** Records a sample; false for a repeat of the previous one, which Ink rejects (pen-ups often repeat the last move). */
        fun add(px: Float, py: Float, p: Float, t: Long): Boolean {
            if (size > 0 && x[size - 1] == px && y[size - 1] == py && time[size - 1] == t.toFloat()) return false
            if (size == x.size) {
                x = x.copyOf(size * 2); y = y.copyOf(size * 2); pressure = pressure.copyOf(size * 2); time = time.copyOf(size * 2)
            }
            x[size] = px; y[size] = py; pressure[size] = p; time[size] = t.toFloat()
            size++
            return true
        }

        fun toStrokePoints(): StrokePoints = StrokePoints.of(
            x.copyOf(size), y.copyOf(size), pressure.copyOf(size), FloatArray(size), FloatArray(size), time.copyOf(size),
        )
    }

    var view: InProgressStrokesView? = null
        set(value) {
            if (value === field) return
            field?.let { old ->
                // Nothing may stay on the old view: a stroke still drawn, or a lifted one awaiting release.
                active?.let { old.cancelStroke(it.id) }
                pendingRelease.forEach { old.cancelStroke(it) }
            }
            active = null
            pendingRelease.clear()
            field = value
        }

    private var active: ActiveStroke? = null
    /** The sink that received the current gesture's DOWN; it gets every event until UP or CANCEL. */
    private var gestureSink: CanvasInputSink? = null
    /** Lifted strokes whose Ink copy is cancelled once the dry layer has drawn them. */
    private val pendingRelease = LinkedHashSet<InProgressStrokeId>()

    override fun onPointer(event: CanvasPointerEvent) {
        val sink = if (event.phase == PointerPhase.DOWN) downstream().also { gestureSink = it } else gestureSink ?: downstream()
        sink.onPointer(event)
        when (event.phase) {
            PointerPhase.DOWN -> begin(sink, event)
            PointerPhase.MOVE -> active?.let { extend(it, event) }
            PointerPhase.UP -> active?.let { finish(it, event) }
            PointerPhase.CANCEL -> active?.let { cancel(it) }
        }
        if (event.phase == PointerPhase.UP || event.phase == PointerPhase.CANCEL) gestureSink = null
    }

    /**
     * Called by the canvas after it has drawn a frame: the strokes lifted before that frame are now
     * drawn dry, so Ink's copies can go.
     */
    fun releaseAfterDraw() {
        if (pendingRelease.isEmpty()) return
        val inkView = view
        val ids = pendingRelease.toList()
        pendingRelease.clear()
        if (inkView != null) inkView.post { ids.forEach { inkView.cancelStroke(it) } }
    }

    private fun begin(sink: CanvasInputSink, event: CanvasPointerEvent) {
        val inkView = view ?: return
        val camera = cameraController.camera.value
        val level = FrameLevels.levelFor(camera.zoom)
        val frame = StrokeFrame(
            anchor = event.world,
            level = level,
            transform = CanvasMath.frameToScreen(camera, viewport(), event.world, level),
        )
        val brush = wetStrokeBrush(frame) ?: return
        val pressure = event.pressure.coerceIn(0f, 1f)
        val record = ActiveStroke(
            sink = sink,
            id = inkView.startStroke(
                InkBridge.strokeInput(x = 0f, y = 0f, elapsedMillis = 0L, kind = event.kind, pressure = pressure),
                InkBridge.toInkBrush(brush),
                frame.transform.toAndroidMatrix().apply {
                    val offset = canvasOffsetInView(inkView)
                    postTranslate(offset.x, offset.y)
                },
            ),
            frame = frame,
            brush = brush,
            kind = event.kind,
            downTimeMillis = event.timeMillis,
        )
        record.samples.add(0f, 0f, pressure, 0L)
        active = record
    }

    private fun extend(record: ActiveStroke, event: CanvasPointerEvent) {
        val inkView = view ?: return
        record.input(event)?.let { inkView.addToStroke(MutableStrokeInputBatch().add(it), record.id) }
    }

    private fun finish(record: ActiveStroke, event: CanvasPointerEvent) {
        active = null
        val inkView = view ?: return
        // The last sample is drawn wet too, but the stroke is not finished in Ink (see the class doc).
        record.input(event)?.let { inkView.addToStroke(MutableStrokeInputBatch().add(it), record.id) }
        record.sink.onWetStrokeFinished(
            FinishedStroke(frame = record.frame, points = record.samples.toStrokePoints(), brush = record.brush, kind = record.kind),
        )
        pendingRelease += record.id
        onReleasePending()
    }

    private fun cancel(record: ActiveStroke) {
        active = null
        view?.cancelStroke(record.id)
    }

    /** The next Ink input for [event] in this stroke's frame, also recorded for the finished stroke; null for a repeat. */
    private fun ActiveStroke.input(event: CanvasPointerEvent): StrokeInput? {
        val local = FrameLevels.worldToLocal(event.world, frame.anchor, frame.level)
        val elapsed = (event.timeMillis - downTimeMillis).coerceAtLeast(lastElapsedMillis)
        lastElapsedMillis = elapsed
        val pressure = event.pressure.coerceIn(0f, 1f)
        if (!samples.add(local.x, local.y, pressure, elapsed)) return null
        return InkBridge.strokeInput(local.x, local.y, elapsed, kind, pressure)
    }
}
