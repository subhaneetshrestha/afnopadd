package io.github.subhaneetshrestha.afnopadd.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A tool's colour and width; width is in screen pixels at the moment of drawing. */
data class ToolStyle(val colorArgb: Long, val widthPx: Float)

/** Per-tool styles, so switching from pen to highlighter and back restores each tool's choices. */
class ToolStyles(defaults: Map<String, ToolStyle>) {

    private val _styles = MutableStateFlow(defaults)
    val styles: StateFlow<Map<String, ToolStyle>> = _styles.asStateFlow()

    fun styleFor(toolId: String): ToolStyle = _styles.value[toolId] ?: FALLBACK

    fun update(toolId: String, transform: (ToolStyle) -> ToolStyle) {
        _styles.update { current ->
            val next = transform(current[toolId] ?: FALLBACK)
            current + (toolId to next.copy(widthPx = next.widthPx.coerceIn(MIN_WIDTH_PX, MAX_WIDTH_PX)))
        }
    }

    companion object {
        const val MIN_WIDTH_PX = 0.5f
        const val MAX_WIDTH_PX = 64f
        val WIDTH_PRESETS_PX = listOf(1f, 2f, 4f, 8f, 16f)
        val FALLBACK = ToolStyle(colorArgb = 0xFF000000, widthPx = 4f)
    }
}
