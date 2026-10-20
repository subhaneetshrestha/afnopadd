package io.github.subhaneetshrestha.afnopadd.tools

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which tool is active; shared between the toolbar and the editor. */
class ToolbarState(initialToolId: String) {
    private val _activeToolId = MutableStateFlow(initialToolId)
    val activeToolId: StateFlow<String> = _activeToolId.asStateFlow()

    fun select(toolId: String) {
        _activeToolId.value = toolId
    }
}

object ToolbarTags {
    fun item(id: String) = "toolbar:$id"
}

/**
 * Renders toolbars from a [ToolbarSpec]: tool refs become toggle buttons, command refs become
 * buttons that follow their command's enabled state, groups render inline, and unknown ids are
 * logged and skipped. Nothing here is hard-coded to a particular tool, which is the hook the
 * later layout-import and plugin phases rely on.
 */
@Composable
fun ToolbarHost(
    spec: ToolbarSpec,
    registry: ToolRegistry,
    commands: CommandRegistry,
    state: ToolbarState,
    modifier: Modifier = Modifier,
) {
    val activeToolId by state.activeToolId.collectAsState()
    for (toolbar in spec.toolbars) {
        val horizontal = toolbar.placement == Placement.TOP || toolbar.placement == Placement.BOTTOM
        if (horizontal) {
            Row(
                modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarItems(toolbar.items, registry, commands, state, activeToolId, horizontal = true)
            }
        } else {
            Column(
                modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ToolbarItems(toolbar.items, registry, commands, state, activeToolId, horizontal = false)
            }
        }
    }
}

@Composable
private fun ToolbarItems(
    items: List<ToolbarItem>,
    registry: ToolRegistry,
    commands: CommandRegistry,
    state: ToolbarState,
    activeToolId: String,
    horizontal: Boolean,
) {
    for (item in items) {
        when (item) {
            is ToolbarItem.Ref -> when {
                item.id.startsWith("tool:") -> registry.get(item.id)?.let { tool ->
                    ToolButton(tool, selected = tool.id == activeToolId, onSelect = { state.select(tool.id) })
                } ?: Log.w(TAG, "Layout refers to unknown tool '${item.id}'")
                item.id.startsWith("cmd:") -> commands.get(item.id)?.let { command ->
                    CommandButton(command, onClick = { commands.invoke(command.id) })
                } ?: Log.w(TAG, "Layout refers to unknown command '${item.id}'")
                else -> Log.w(TAG, "Layout item '${item.id}' is not supported yet")
            }
            ToolbarItem.Separator -> if (horizontal) VerticalDivider(Modifier.height(32.dp)) else HorizontalDivider(Modifier.width(32.dp))
            is ToolbarItem.Group -> ToolbarItems(item.items, registry, commands, state, activeToolId, horizontal)
        }
    }
}

@Composable
private fun ToolButton(tool: Tool, selected: Boolean, onSelect: () -> Unit) {
    FilledIconToggleButton(
        checked = selected,
        onCheckedChange = { if (it) onSelect() },
        modifier = Modifier.testTag(ToolbarTags.item(tool.id)).semantics { this.selected = selected },
    ) {
        Icon(painter = painterResource(tool.iconRes), contentDescription = stringResource(tool.labelRes))
    }
}

@Composable
private fun CommandButton(command: Command, onClick: () -> Unit) {
    val enabled by command.enabled.collectAsState()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(ToolbarTags.item(command.id)),
    ) {
        Icon(
            painter = painterResource(command.iconRes),
            contentDescription = stringResource(command.labelRes),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

private const val TAG = "ToolbarHost"
