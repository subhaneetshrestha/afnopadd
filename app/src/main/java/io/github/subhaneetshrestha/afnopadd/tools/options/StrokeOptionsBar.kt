package io.github.subhaneetshrestha.afnopadd.tools.options

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyles
import kotlin.math.abs
import kotlin.math.roundToInt

object StrokeOptionsTags {
    const val SLIDER = "thickness:slider"
    const val COLOR = "stroke:color"

    fun preset(widthPx: Float) = "thickness:$widthPx"
}

/**
 * Options for the active stroke tool: a colour swatch that opens the picker, five width presets
 * and a slider from 0.5 to 64 screen pixels.
 */
@Composable
fun StrokeOptionsBar(
    widthPx: Float,
    onWidthChange: (Float) -> Unit,
    colorArgb: Long,
    onColorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorLabel = stringResource(R.string.color_title)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(Color(colorArgb.toInt()), CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onColorClick)
                .semantics { contentDescription = colorLabel }
                .testTag(StrokeOptionsTags.COLOR),
        )
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
