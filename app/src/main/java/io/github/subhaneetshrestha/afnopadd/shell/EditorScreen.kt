package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.SheetCanvas
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.SheetStore
import io.github.subhaneetshrestha.afnopadd.tools.Command
import io.github.subhaneetshrestha.afnopadd.tools.CommandRegistry
import io.github.subhaneetshrestha.afnopadd.tools.CurrentStyle
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolRegistry
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarHost
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarSpec
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarSpecJson
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarState
import io.github.subhaneetshrestha.afnopadd.tools.pen.PenTool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The editor: opens the sheet, wires the tool and command registries to the canvas and toolbar,
 * and closes the sheet (flush, compaction, asset garbage collection) when it leaves composition.
 * Until the library exists every sheet id maps to the placeholder sheet directory.
 */
@Composable
fun EditorScreen(sheetId: String, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var sheet by remember(sheetId) { mutableStateOf<Sheet?>(null) }
    val defaultTitle = stringResource(R.string.sheet_default_title)

    LaunchedEffect(sheetId) {
        sheet = openOrCreate(container.sheetStore, placeholderSheetDir(context), defaultTitle)
    }
    DisposableEffect(sheet) {
        val open = sheet
        onDispose { open?.let { container.appScope.launch { it.close() } } }
    }

    val current = sheet
    if (current == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text(text = stringResource(R.string.editor_loading), modifier = Modifier.padding(top = 64.dp))
        }
        return
    }
    EditorContent(current, modifier)
}

@Composable
private fun EditorContent(sheet: Sheet, modifier: Modifier) {
    val context = LocalContext.current
    val camera = remember(sheet) { CameraController() }
    val style = remember { MutableStateFlow(CurrentStyle()) }
    val toolContext = remember(sheet) { ToolContext(sheet, camera, style) }
    val registry = remember { ToolRegistry().apply { register(PenTool()) } }
    val toolbarState = remember { ToolbarState(initialToolId = PenTool.ID) }
    val activeToolId by toolbarState.activeToolId.collectAsState()
    val controller = remember(activeToolId, toolContext) {
        (registry.get(activeToolId) ?: registry.get(PenTool.ID)!!).createController(toolContext)
    }
    DisposableEffect(controller) {
        controller.onActivated()
        onDispose { controller.onDeactivated() }
    }
    val commands = remember(sheet) {
        CommandRegistry().apply {
            register(Command("cmd:undo", R.string.command_undo, R.drawable.ic_undo, sheet.canUndo) { sheet.undo() })
            register(Command("cmd:redo", R.string.command_redo, R.drawable.ic_redo, sheet.canRedo) { sheet.redo() })
        }
    }
    val spec: ToolbarSpec = remember {
        ToolbarSpecJson.parse(context.assets.open(DEFAULT_LAYOUT_ASSET).bufferedReader().use { it.readText() })
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { ToolbarHost(spec = spec, registry = registry, commands = commands, state = toolbarState) },
    ) { innerPadding ->
        SheetCanvas(
            sheet = sheet,
            cameraController = camera,
            inputSink = controller,
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(ShellTags.EDITOR_CANVAS),
            wetStrokeBrush = controller::wetStrokeBrush,
        )
    }
}

private suspend fun openOrCreate(store: SheetStore, dir: File, defaultTitle: String): Sheet =
    if (File(dir, SheetStore.DATABASE_FILE_NAME).isFile) store.open(dir) else store.create(dir, defaultTitle)

private const val DEFAULT_LAYOUT_ASSET = "tool_layout_default.json"
