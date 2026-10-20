package io.github.subhaneetshrestha.afnopadd.canvas.internal

import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import kotlin.math.roundToInt

/** Conversions between Afnopadd's document types and Jetpack Ink's, kept in one place so Ink stays replaceable. */
internal object InkBridge {

    const val FAMILY_PRESSURE_PEN = "ink:pressure-pen"
    const val FAMILY_MARKER = "ink:marker"
    const val FAMILY_HIGHLIGHTER = "ink:highlighter"
    const val FAMILY_DASHED_LINE = "ink:dashed-line"

    fun brushFamily(familyId: String): BrushFamily = when (familyId) {
        FAMILY_MARKER -> StockBrushes.marker()
        FAMILY_HIGHLIGHTER -> StockBrushes.highlighter()
        FAMILY_DASHED_LINE -> StockBrushes.dashedLine()
        else -> StockBrushes.pressurePen()
    }

    /** Builds the Ink brush; the spec's opacity multiplies the colour's alpha. */
    fun toInkBrush(spec: BrushSpec): Brush = Brush.createWithColorIntArgb(
        family = brushFamily(spec.familyId),
        colorIntArgb = colorIntArgb(spec),
        size = spec.size,
        epsilon = spec.epsilon,
    )

    /** The brush colour as an Android colour int with the spec's opacity folded into its alpha. */
    fun colorIntArgb(spec: BrushSpec): Int {
        val alpha = ((spec.colorArgb ushr 24 and 0xFF) * spec.opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (spec.colorArgb and 0x00FFFFFF).toInt()
    }

    fun toInputToolType(kind: PointerKind): InputToolType = when (kind) {
        PointerKind.FINGER -> InputToolType.TOUCH
        PointerKind.STYLUS, PointerKind.ERASER -> InputToolType.STYLUS
        PointerKind.MOUSE -> InputToolType.MOUSE
        PointerKind.UNKNOWN -> InputToolType.UNKNOWN
    }

    fun strokeInput(x: Float, y: Float, elapsedMillis: Long, kind: PointerKind, pressure: Float): StrokeInput =
        StrokeInput().apply {
            update(
                x = x,
                y = y,
                elapsedTimeMillis = elapsedMillis,
                toolType = toInputToolType(kind),
                pressure = pressure.coerceIn(0f, 1f),
            )
        }

    /** Copies an Ink input batch into Afnopadd's packed point format. */
    fun toStrokePoints(batch: StrokeInputBatch): StrokePoints {
        val count = batch.size
        val x = FloatArray(count)
        val y = FloatArray(count)
        val pressure = FloatArray(count)
        val tilt = FloatArray(count)
        val orientation = FloatArray(count)
        val time = FloatArray(count)
        val scratch = StrokeInput()
        for (i in 0 until count) {
            val input = batch.populate(i, scratch)
            x[i] = input.x
            y[i] = input.y
            pressure[i] = if (input.hasPressure) input.pressure else 1f
            tilt[i] = if (input.hasTilt) input.tiltRadians else 0f
            orientation[i] = if (input.hasOrientation) input.orientationRadians else 0f
            time[i] = input.elapsedTimeMillis.toFloat()
        }
        return StrokePoints.of(x, y, pressure, tilt, orientation, time)
    }

    fun FrameTransform.toAndroidMatrix(): Matrix {
        val values = asFloat()
        return Matrix().apply {
            setScale(values.scale, values.scale)
            postTranslate(values.translateX, values.translateY)
        }
    }
}
