package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.ElementIndex
import io.github.subhaneetshrestha.afnopadd.canvas.SheetCanvas
import io.github.subhaneetshrestha.afnopadd.canvas.SheetHitTester
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeHitTest
import android.util.Log
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.SheetFormatException
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.tools.Command
import io.github.subhaneetshrestha.afnopadd.tools.CommandRegistry
import io.github.subhaneetshrestha.afnopadd.tools.ToolCapability
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolRegistry
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarHost
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarSpec
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarSpecJson
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarState
import io.github.subhaneetshrestha.afnopadd.tools.color.ColorPickerDialog
import io.github.subhaneetshrestha.afnopadd.tools.eraser.EraserTool
import io.github.subhaneetshrestha.afnopadd.tools.options.StrokeOptionsBar
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import io.github.subhaneetshrestha.afnopadd.tools.stroke.StrokeTool
import kotlinx.coroutines.launch
import java.io.File

/**
 * The editor: opens the sheet, wires the tool and command registries to the canvas and toolbar,
 * marks it opened in the library, and closes it (flush, compaction, asset garbage collection)
 * when it leaves composition, then refreshes its library entry.
 */
@Composable
fun EditorScreen(sheetId: String, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    var sheet by remember(sheetId) { mutableStateOf<Sheet?>(null) }
    var failed by remember(sheetId) { mutableStateOf(false) }

    LaunchedEffect(sheetId) {
        try {
            sheet = container.sheetStore.open(File(sheetsRoot(context), sheetId))
            container.library.markOpened(SheetId(sheetId))
        } catch (e: SheetFormatException) {
            Log.w("EditorScreen", "Could not open sheet $sheetId", e)
            failed = true
        }
    }
    DisposableEffect(sheet) {
        val open = sheet
        onDispose {
            open?.let {
                val snapshot = it.snapshot.value
                val updatedAt = it.meta.value.updatedAt
                container.appScope.launch {
                    it.close()
                    container.thumbnails.update(File(sheetsRoot(context), sheetId), snapshot, updatedAt)
                    container.library.refresh(SheetId(sheetId))
                }
            }
        }
    }

    if (failed) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.editor_open_failed))
        }
        return
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
    val styles = LocalAppContainer.current.toolStyles
    val camera = remember(sheet) { CameraController() }
    // One index serves both the canvas's culling and the tools' hit-testing.
    val index = remember(sheet) { ElementIndex() }
    val toolContext = remember(sheet) {
        ToolContext(sheet, camera, styles, hitTester = SheetHitTester(sheet, index, StrokeHitTest.ink()))
    }
    val registry = remember {
        ToolRegistry().apply {
            BuiltInPresets.strokeTools.forEach { register(StrokeTool(it)) }
            register(EraserTool())
        }
    }
    val toolbarState = remember { ToolbarState(initialToolId = BuiltInTools.PEN) }
    val activeToolId by toolbarState.activeToolId.collectAsState()
    val activeTool = registry.get(activeToolId) ?: registry.get(BuiltInTools.PEN)!!
    val controller = remember(activeTool, toolContext) { activeTool.createController(toolContext) }
    val hiddenElements by controller.hiddenElements.collectAsState()
    val styleMap by styles.styles.collectAsState()
    val container = LocalAppContainer.current
    val recentColors by container.recentColors.colors.collectAsState(initial = emptyList())
    var pickingColor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
        bottomBar = {
            Column {
                if (ToolCapability.STROKE in activeTool.capabilities) {
                    val style = styleMap[activeTool.id] ?: styles.styleFor(activeTool.id)
                    StrokeOptionsBar(
                        widthPx = style.widthPx,
                        onWidthChange = { width -> styles.update(activeTool.id) { it.copy(widthPx = width) } },
                        colorArgb = style.colorArgb,
                        onColorClick = { pickingColor = true },
                    )
                    if (pickingColor) {
                        ColorPickerDialog(
                            initial = style.colorArgb,
                            recents = recentColors,
                            onPick = { color ->
                                pickingColor = false
                                styles.update(activeTool.id) { it.copy(colorArgb = color) }
                                scope.launch { container.recentColors.add(color) }
                            },
                            onDismiss = { pickingColor = false },
                        )
                    }
                }
                ToolbarHost(spec = spec, registry = registry, commands = commands, state = toolbarState)
            }
        },
    ) { innerPadding ->
        SheetCanvas(
            sheet = sheet,
            cameraController = camera,
            inputSink = controller,
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag(ShellTags.EDITOR_CANVAS),
            wetStrokeBrush = controller::wetStrokeBrush,
            index = index,
            hiddenElements = hiddenElements,
        )
    }
}

private const val DEFAULT_LAYOUT_ASSET = "tool_layout_default.json"
