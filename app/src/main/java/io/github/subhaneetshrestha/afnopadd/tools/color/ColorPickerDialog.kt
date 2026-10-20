package io.github.subhaneetshrestha.afnopadd.tools.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.R

object ColorPickerTags {
    const val HEX_FIELD = "color:hex"
    const val APPLY = "color:apply"
    const val HUE = "color:hue"
    const val SATURATION = "color:saturation"
    const val VALUE = "color:value"

    fun swatch(argb: Long) = "color:swatch:%08X".format(argb and 0xFFFFFFFF)

    fun recent(argb: Long) = "color:recent:%08X".format(argb and 0xFFFFFFFF)
}

/**
 * Colour choice for the active tool: tap a palette or recent swatch to pick it at once, or tune
 * hue, saturation and value (or type a hex code) and apply.
 */
@Composable
fun ColorPickerDialog(initial: Long, recents: List<Long>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var hsv by remember { mutableStateOf(Hsv.fromArgb(initial)) }
    var hex by remember { mutableStateOf(HexColor.format(initial)) }
    val parsed = HexColor.parse(hex)

    fun setHsv(next: Hsv) {
        hsv = next
        hex = HexColor.format(next.toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPalette.SWATCHES.chunked(8).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { color -> Swatch(color, ColorPickerTags.swatch(color)) { onPick(color) } }
                    }
                }
                if (recents.isNotEmpty()) {
                    Text(stringResource(R.string.color_recent), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recents.forEach { color -> Swatch(color, ColorPickerTags.recent(color)) { onPick(color) } }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(24.dp)
                        .background(Color(parsed?.toInt() ?: hsv.toArgb().toInt()), RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
                )
                Slider(
                    value = hsv.hue,
                    onValueChange = { setHsv(hsv.copy(hue = it)) },
                    valueRange = 0f..360f,
                    modifier = Modifier.testTag(ColorPickerTags.HUE),
                )
                Slider(
                    value = hsv.saturation,
                    onValueChange = { setHsv(hsv.copy(saturation = it)) },
                    modifier = Modifier.testTag(ColorPickerTags.SATURATION),
                )
                Slider(
                    value = hsv.value,
                    onValueChange = { setHsv(hsv.copy(value = it)) },
                    modifier = Modifier.testTag(ColorPickerTags.VALUE),
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text
                        HexColor.parse(text)?.let { hsv = Hsv.fromArgb(it) }
                    },
                    singleLine = true,
                    isError = parsed == null,
                    label = { Text(stringResource(R.string.color_hex)) },
                    modifier = Modifier.fillMaxWidth().testTag(ColorPickerTags.HEX_FIELD),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onPick) },
                enabled = parsed != null,
                modifier = Modifier.testTag(ColorPickerTags.APPLY),
            ) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Swatch(color: Long, tag: String, onClick: () -> Unit) {
    val label = HexColor.format(color)
    Box(
        Modifier
            .size(28.dp)
            .background(Color(color.toInt()), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
    )
}
