package io.github.subhaneetshrestha.afnopadd.canvas.internal

import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
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

/**
 * Sits between the gesture handler and the tool. Every pointer event is forwarded downstream; the
 * ones that start, extend, finish or cancel a stroke also drive Jetpack Ink's [InProgressStrokesView]
 * through its `StrokeInput` API, in the zoom frame fixed at the stroke's first sample.
 *
 * Finished Ink strokes come back as [FinishedStroke]s. Ink keeps drawing its own copy until the
 * canvas has drawn the committed element once, then [drainHandoff] releases them, so the stroke
 * never blinks between the wet and dry layers.
 */
internal class WetStrokeRouter(
    private val downstream: CanvasInputSink,
    private val cameraController: CameraController,
    private val viewport: () -> Viewport,
    private val wetStrokeBrush: (StrokeFrame) -> BrushSpec?,
    private val onHandoffPending: () -> Unit = {},
) : CanvasInputSink, InProgressStrokesFinishedListener {

    private class ActiveStroke(
        val id: InProgressStrokeId,
        val frame: StrokeFrame,
        val brush: BrushSpec,
        val kind: PointerKind,
        val downTimeMillis: Long,
        var lastElapsedMillis: Long = 0L,
    )

    var view: InProgressStrokesView? = null
        set(value) {
            field?.removeFinishedStrokesListener(this)
            field = value
            value?.addFinishedStrokesListener(this)
        }

    private var active: ActiveStroke? = null
    private val awaitingFinish = HashMap<InProgressStrokeId, ActiveStroke>()
    private val pendingHandoff = LinkedHashSet<InProgressStrokeId>()

    override fun onPointer(event: CanvasPointerEvent) {
        downstream.onPointer(event)
        when (event.phase) {
            PointerPhase.DOWN -> begin(event)
            PointerPhase.MOVE -> active?.let { extend(it, event) }
            PointerPhase.UP -> active?.let { finish(it, event) }
            PointerPhase.CANCEL -> active?.let { cancel(it) }
        }
    }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        for ((id, stroke) in strokes) {
            val record = awaitingFinish.remove(id) ?: continue
            downstream.onWetStrokeFinished(
                FinishedStroke(
                    stroke = stroke,
                    frame = record.frame,
                    points = InkBridge.toStrokePoints(stroke.inputs),
                    brush = record.brush,
                    kind = record.kind,
                ),
            )
        }
        pendingHandoff += strokes.keys
        onHandoffPending()
    }

    /** Called by the canvas after it has drawn a frame; returns the Ink strokes that can now be released. */
    fun drainHandoff(): Set<InProgressStrokeId>? {
        if (pendingHandoff.isEmpty()) return null
        val ids = pendingHandoff.toSet()
        pendingHandoff.clear()
        return ids
    }

    private fun begin(event: CanvasPointerEvent) {
        val inkView = view ?: return
        val camera = cameraController.camera.value
        val level = FrameLevels.levelFor(camera.zoom)
        val frame = StrokeFrame(
            anchor = event.world,
            level = level,
            transform = CanvasMath.frameToScreen(camera, viewport(), event.world, level),
        )
        val brush = wetStrokeBrush(frame) ?: return
        val record = ActiveStroke(
            id = inkView.startStroke(
                InkBridge.strokeInput(x = 0f, y = 0f, elapsedMillis = 0L, kind = event.kind, pressure = event.pressure),
                InkBridge.toInkBrush(brush),
                frame.transform.toAndroidMatrix(),
            ),
            frame = frame,
            brush = brush,
            kind = event.kind,
            downTimeMillis = event.timeMillis,
        )
        active = record
    }

    private fun extend(record: ActiveStroke, event: CanvasPointerEvent) {
        val inkView = view ?: return
        inkView.addToStroke(MutableStrokeInputBatch().add(record.input(event)), record.id)
    }

    private fun finish(record: ActiveStroke, event: CanvasPointerEvent) {
        val inkView = view ?: return
        awaitingFinish[record.id] = record
        active = null
        inkView.finishStroke(record.input(event), record.id)
    }

    private fun cancel(record: ActiveStroke) {
        active = null
        view?.cancelStroke(record.id)
    }

    private fun ActiveStroke.input(event: CanvasPointerEvent) = run {
        val local = FrameLevels.worldToLocal(event.world, frame.anchor, frame.level)
        val elapsed = (event.timeMillis - downTimeMillis).coerceAtLeast(lastElapsedMillis)
        lastElapsedMillis = elapsed
        InkBridge.strokeInput(local.x, local.y, elapsed, kind, event.pressure)
    }
}
