package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.awaitCancellation
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
        val opened = try {
            container.sheetStore.open(File(sheetsRoot(context), sheetId))
        } catch (e: SheetFormatException) {
            Log.w("EditorScreen", "Could not open sheet $sheetId", e)
            failed = true
            return@LaunchedEffect
        }
        // The sheet is closed by the coroutine that opened it, so leaving the editor at any moment,
        // even between the open finishing and the screen showing the sheet, still releases its database.
        try {
            sheet = opened
            container.library.markOpened(SheetId(sheetId))
            awaitCancellation()
        } finally {
            val snapshot = opened.snapshot.value
            val updatedAt = opened.meta.value.updatedAt
            container.appScope.launch {
                opened.close()
                container.thumbnails.update(File(sheetsRoot(context), sheetId), snapshot, updatedAt)
                container.library.refresh(SheetId(sheetId))
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

    // The canvas always fills the editor and the bars float over it. Resizing the canvas resizes
    // Jetpack Ink's InProgressStrokesView, and Ink 1.0.0 can then crash its render thread (an
    // IllegalStateException in CanvasInProgressStrokesRenderHelperV33, or a SIGSEGV in libhwui);
    // the options bar appearing and disappearing on tool switches did exactly that (ADR 0003).
    Box(modifier = modifier.fillMaxSize()) {
        SheetCanvas(
            sheet = sheet,
            cameraController = camera,
            inputSink = controller,
            modifier = Modifier.fillMaxSize().statusBarsPadding().testTag(ShellTags.EDITOR_CANVAS),
            wetStrokeBrush = controller::wetStrokeBrush,
            index = index,
            hiddenElements = hiddenElements,
        )
        // A Surface paints the bars' background and keeps touches on them from reaching the canvas.
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 3.dp) {
            Column(modifier = Modifier.navigationBarsPadding()) {
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
        }
    }
}

private const val DEFAULT_LAYOUT_ASSET = "tool_layout_default.json"
