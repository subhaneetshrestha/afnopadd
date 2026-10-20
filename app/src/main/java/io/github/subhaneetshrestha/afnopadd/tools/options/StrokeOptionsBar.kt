package io.github.subhaneetshrestha.afnopadd.tools.options

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyles
import kotlin.math.abs
import kotlin.math.roundToInt

object StrokeOptionsTags {
    const val SLIDER = "thickness:slider"

    fun preset(widthPx: Float) = "thickness:$widthPx"
}

/** Thickness for the active stroke tool: five presets and a slider from 0.5 to 64 screen pixels. */
@Composable
fun StrokeOptionsBar(widthPx: Float, onWidthChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (preset in ToolStyles.WIDTH_PRESETS_PX) {
            FilterChip(
                selected = abs(widthPx - preset) < 0.01f,
                onClick = { onWidthChange(preset) },
                label = { Text(preset.roundToInt().toString()) },
                modifier = Modifier.testTag(StrokeOptionsTags.preset(preset)),
            )
        }
        Slider(
            value = widthPx,
            onValueChange = onWidthChange,
            valueRange = ToolStyles.MIN_WIDTH_PX..ToolStyles.MAX_WIDTH_PX,
            modifier = Modifier.weight(1f).testTag(StrokeOptionsTags.SLIDER),
        )
    }
}
