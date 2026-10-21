package io.github.subhaneetshrestha.afnopadd.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.tools.commands.ZoomCommands

object ZoomReadoutTags {
    const val READOUT = "zoom-readout"
    fun item(commandId: String) = "zoom-readout:$commandId"
}

/** The compact zoom readout of the default layout (`widget:zoom`): shows the zoom and opens the zoom commands. */
@Composable
fun ZoomReadout(zoom: Double, commands: CommandRegistry, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val label = ZoomCommands.readout(zoom)
    val description = stringResource(R.string.zoom_readout_description, label)
    Box(modifier) {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.testTag(ZoomReadoutTags.READOUT).semantics { contentDescription = description },
        ) {
            Text(label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (id in listOf(ZoomCommands.ZOOM_IN, ZoomCommands.ZOOM_OUT, ZoomCommands.ZOOM_FIT, ZoomCommands.ZOOM_100)) {
                val command = commands.get(id) ?: continue
                DropdownMenuItem(
                    text = { Text(stringResource(command.labelRes)) },
                    leadingIcon = { Icon(painterResource(command.iconRes), contentDescription = null) },
                    onClick = {
                        open = false
                        commands.invoke(id)
                    },
                    modifier = Modifier.testTag(ZoomReadoutTags.item(id)),
                )
            }
        }
    }
}
