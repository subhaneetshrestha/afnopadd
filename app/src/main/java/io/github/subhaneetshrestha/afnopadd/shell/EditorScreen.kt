package io.github.subhaneetshrestha.afnopadd.shell

import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.ElementIndex
import io.github.subhaneetshrestha.afnopadd.canvas.SheetCanvas
import io.github.subhaneetshrestha.afnopadd.canvas.SheetHitTester
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeHitTest
import android.util.Log
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.SheetFormatException
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
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
import io.github.subhaneetshrestha.afnopadd.tools.shape.ShapeTools
import io.github.subhaneetshrestha.afnopadd.tools.InputPolicy
import io.github.subhaneetshrestha.afnopadd.tools.ZoomReadout
import io.github.subhaneetshrestha.afnopadd.tools.commands.ZoomCommands
import io.github.subhaneetshrestha.afnopadd.tools.hand.HandTool
import io.github.subhaneetshrestha.afnopadd.shell.settings.Settings
import io.github.subhaneetshrestha.afnopadd.canvas.CameraAnimator
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerRole
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.Job
import io.github.subhaneetshrestha.afnopadd.tools.options.StrokeOptionsBar
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import io.github.subhaneetshrestha.afnopadd.tools.stroke.StrokeTool
import kotlinx.coroutines.launch
import java.io.File

/**
 * The editor: opens the sheet, wires the tool and command registries to the canvas and toolbar,
 * marks it opened in the library, and closes it (flush, compaction, asset garbage collection)
 * when it leaves composition, then refreshes its library entry. When it leaves only because the
 * activity is being recreated, the sheet and camera stay open in [EditorSessions] and the new
 * editor continues with them; the camera and tool are also saved for process death.
 */
@Composable
fun EditorScreen(sheetId: String, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = LocalActivity.current
    val cameraMemory = rememberSaveable(sheetId, saver = CameraMemory.Saver) { CameraMemory() }
    var session by remember(sheetId) { mutableStateOf<EditorSessions.Session?>(null) }
    var failed by remember(sheetId) { mutableStateOf(false) }

    LaunchedEffect(sheetId) {
        val current = container.editorSessions.get(sheetId) ?: try {
            val opened = container.sheetStore.open(File(sheetsRoot(context), sheetId))
            EditorSessions.Session(opened, CameraController(cameraMemory.camera ?: Camera.DEFAULT)).also {
                container.editorSessions.put(sheetId, it)
            }
        } catch (e: SheetFormatException) {
            Log.w("EditorScreen", "Could not open sheet $sheetId", e)
            failed = true
            return@LaunchedEffect
        }
        // The sheet is closed by the coroutine that opened it, so leaving the editor at any moment,
        // even between the open finishing and the screen showing the sheet, still releases its database.
        try {
            session = current
            container.library.markOpened(SheetId(sheetId))
            current.camera.camera.collect { cameraMemory.camera = it }
        } finally {
            if (activity?.isChangingConfigurations != true) {
                container.editorSessions.remove(sheetId)
                val opened = current.sheet
                val snapshot = opened.snapshot.value
                val updatedAt = opened.meta.value.updatedAt
                container.appScope.launch {
                    container.library.closeEditing(SheetId(sheetId)) { dir ->
                        opened.close()
                        container.thumbnails.update(dir, snapshot, updatedAt)
                    }
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
    val current = session
    if (current == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text(text = stringResource(R.string.editor_loading), modifier = Modifier.padding(top = 64.dp))
        }
        return
    }
    EditorContent(current.sheet, current.camera, modifier)
}

/** The camera last seen by the editor, kept in saved state so a process restart reopens the sheet where it was. */
private class CameraMemory(var camera: Camera? = null) {
    companion object {
        val Saver = Saver<CameraMemory, DoubleArray>(
            save = { memory -> memory.camera?.let { doubleArrayOf(it.center.x, it.center.y, it.zoom) } ?: doubleArrayOf() },
            restore = { values -> CameraMemory(if (values.size == 3) Camera(WorldPoint(values[0], values[1]), values[2]) else null) },
        )
    }
}

@Composable
private fun EditorContent(sheet: Sheet, camera: CameraController, modifier: Modifier) {
    val context = LocalContext.current
    val styles = LocalAppContainer.current.toolStyles
    // One index serves both the canvas's culling and the tools' hit-testing.
    val index = remember(sheet) { ElementIndex() }
    val toolContext = remember(sheet) {
        ToolContext(sheet, camera, styles, hitTester = SheetHitTester(sheet, index, StrokeHitTest.ink()))
    }
    val registry = remember {
        ToolRegistry().apply {
            BuiltInPresets.strokeTools.forEach { register(StrokeTool(it)) }
            register(EraserTool())
            ShapeTools.all.forEach { register(it) }
            register(HandTool())
        }
    }
    // The selected tool survives rotation and process death with the rest of the editor's saved state.
    var savedToolId by rememberSaveable { mutableStateOf(BuiltInTools.PEN) }
    val toolbarState = remember { ToolbarState(initialToolId = savedToolId) }
    val activeToolId by toolbarState.activeToolId.collectAsState()
    LaunchedEffect(activeToolId) { savedToolId = activeToolId }
    val activeTool = registry.get(activeToolId) ?: registry.get(BuiltInTools.PEN)!!
    val controller = remember(activeTool, toolContext) { activeTool.createController(toolContext) }
    val hiddenElements by controller.hiddenElements.collectAsState()
    val previewElements by controller.previewElements.collectAsState()
    val styleMap by styles.styles.collectAsState()
    val container = LocalAppContainer.current
    val recentColors by container.recentColors.colors.collectAsState(initial = emptyList())
    var pickingColor by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(controller) {
        controller.onActivated()
        onDispose { controller.onDeactivated() }
    }
    // Null until the settings file has been read; the defaults stand in meanwhile.
    val settings = container.settings.settings.collectAsState(initial = null).value ?: Settings()
    val view = LocalView.current
    DisposableEffect(view, settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // The canvas's size, for zoom commands; not state, since nothing redraws when it changes.
    val viewport = remember { arrayOf(Viewport(0, 0)) }
    val commands = remember(sheet, camera) {
        val animator = CameraAnimator(camera)
        var zoomAnimation: Job? = null
        CommandRegistry().apply {
            register(Command("cmd:undo", R.string.command_undo, R.drawable.ic_undo, sheet.canUndo) { sheet.undo() })
            register(Command("cmd:redo", R.string.command_redo, R.drawable.ic_redo, sheet.canRedo) { sheet.redo() })
            ZoomCommands(
                camera = camera,
                viewport = { viewport[0] },
                contentBounds = { ZoomCommands.boundsOf(sheet.snapshot.value.elements.values.map { it.bounds }) },
                animateTo = { target ->
                    zoomAnimation?.cancel()
                    zoomAnimation = scope.launch { animator.animateTo(target) }
                },
            ).all.forEach(::register)
        }
    }
    // Asked once per gesture by the canvas: the first stylus touch flips finger mode AUTO to panning, for good.
    var stylusMarked by remember { mutableStateOf(false) }
    val pointerRole: (PointerKind) -> PointerRole = { kind ->
        if (InputPolicy.isStylus(kind) && !settings.stylusSeen && !stylusMarked) {
            stylusMarked = true
            scope.launch { container.settings.update { it.copy(stylusSeen = true) } }
        }
        InputPolicy.roleFor(kind, activeTool.capabilities, settings.fingerMode, settings.stylusSeen || stylusMarked)
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
            background = settings.defaultBackground,
            pointerRole = pointerRole,
            wetStrokeBrush = controller::wetStrokeBrush,
            onViewportChanged = { viewport[0] = it },
            index = index,
            hiddenElements = hiddenElements,
            previewElements = previewElements,
            secondPointerConstrains = ToolCapability.SHAPE in activeTool.capabilities,
        )
        // A Surface paints the bars' background and keeps touches on them from reaching the canvas.
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 3.dp) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                if (ToolCapability.STROKE in activeTool.capabilities || ToolCapability.SHAPE in activeTool.capabilities) {
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
                ToolbarHost(
                    spec = spec,
                    registry = registry,
                    commands = commands,
                    state = toolbarState,
                    widgets = mapOf(
                        ZOOM_WIDGET to {
                            // Read here, so a pan or zoom recomposes only the readout.
                            val zoom by camera.camera.collectAsState()
                            ZoomReadout(zoom.zoom, commands)
                        },
                    ),
                )
            }
        }
    }
}

private const val DEFAULT_LAYOUT_ASSET = "tool_layout_default.json"
private const val ZOOM_WIDGET = "widget:zoom"
