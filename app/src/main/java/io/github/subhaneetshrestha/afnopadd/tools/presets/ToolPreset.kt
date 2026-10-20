package io.github.subhaneetshrestha.afnopadd.tools.presets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** A parameter value in a preset; the shapes later JSON presets (SPEC-tool-config.md, Layer 2) will use. */
sealed interface ParamValue {
    data class Const(val value: Double) : ParamValue
    data class Text(val value: String) : ParamValue
    data class Color(val argb: Long) : ParamValue
    data class Curve(val input: String, val points: List<Pair<Float, Float>>) : ParamValue
}

/**
 * A tool defined by parameters on top of a base tool. The built-in pen, pencil, brush and
 * highlighter are presets of the base stroke tool, which is exactly how user presets will plug in.
 */
data class ToolPreset(
    val id: String,
    val baseToolId: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val params: Map<String, ParamValue>,
) {
    fun number(key: String, default: Double): Double = (params[key] as? ParamValue.Const)?.value ?: default

    fun text(key: String, default: String): String = (params[key] as? ParamValue.Text)?.value ?: default

    fun flag(key: String): Boolean = number(key, 0.0) != 0.0
}
