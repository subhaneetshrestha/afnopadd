# Spec: drawing-tools

Status: **APPROVED by the owner on 2026-10-20.** Module id `drawing-tools`. Depends on: `canvas-engine`, `sheet-document`. Package: `io.github.subhaneetshrestha.afnopadd.tools`. Project-wide rules in `docs/product/spec.md` apply.

## Objective

Everything the user picks up to change a sheet or move around it: the tool registry with stable IDs, each tool's state machine, brush presets, colours and thickness, the hand tool for panning and pinch-zooming, the toolbar that is rendered from a declarative spec rather than hard-coded, and the undo/redo and zoom controls. Built so that `tool-config` can later replace the toolbar spec with user JSON and `plugins` can register new tools, without touching this module's core.

## Scope

**In:** `ToolRegistry`; tool interfaces and controllers; v1 tools (pen, pencil, brush, highlighter, eraser, line, rectangle, ellipse, arrow, select/move, hand); brush presets mapped to Ink brush families; colour palette, picker, recents; thickness presets and slider; `ToolbarSpec` and its Compose renderer; the built-in default layout shipped as a JSON asset; undo/redo and zoom controls; finger-versus-stylus policy; keyboard shortcuts for keyboard-equipped devices.

**Out:** image import and image-specific transforms (`image-board`, which reuses the select tool's handles), rendering (`canvas-engine`), JSON import from the user (`tool-config`).

## Contract

```kotlin
interface Tool {
    val id: String                       // stable, "tool:pen"
    val label: StringResource; val icon: IconSpec
    val capabilities: Set<ToolCapability> // STROKE, SHAPE, SELECT, ERASE, NAVIGATE
    fun createController(context: ToolContext): ToolController
}
interface ToolController : CanvasInputSink { fun onActivated(); fun onDeactivated(); val options: StateFlow<ToolOptions>; val hiddenElements: StateFlow<Set<ElementId>> } // hidden = in-gesture preview (eraser)
class ToolContext(val sheet: Sheet, val camera: CameraController, val hitTester: HitTester, val style: StateFlow<CurrentStyle>)
interface ToolRegistry { fun register(tool: Tool); fun get(id: String): Tool?; val all: List<Tool> }
data class ToolPreset(val id: String, val baseToolId: String, val label: String, val params: Map<String, ParamValue>)
sealed interface ParamValue { data class Const(val value: Double) : ParamValue; data class Curve(val input: String, val points: List<Pair<Float, Float>>) : ParamValue; data class Color(val argb: Long) : ParamValue }
data class ToolbarSpec(val schemaVersion: Int, val toolbars: List<Toolbar>)
data class Toolbar(val id: String, val placement: Placement, val items: List<ToolbarItem>)
sealed interface ToolbarItem { data class ToolRef(val id: String) : ToolbarItem; data class PresetRef(val id: String) : ToolbarItem; data object Separator : ToolbarItem; data class Group(val id: String, val label: String, val items: List<ToolbarItem>) : ToolbarItem; data class Command(val id: String) : ToolbarItem }
interface CommandRegistry { fun register(id: String, action: Action); fun invoke(id: String, args: JsonObject = JsonObject(emptyMap())) }
@Composable fun ToolbarHost(spec: ToolbarSpec, registry: ToolRegistry, commands: CommandRegistry, state: ToolbarState, modifier: Modifier)
```

Built-in commands: `cmd:undo`, `cmd:redo`, `cmd:zoom-in`, `cmd:zoom-out`, `cmd:zoom-fit`, `cmd:zoom-100`, `cmd:delete-selection`, `cmd:duplicate-selection`, `cmd:bring-to-front`, `cmd:send-to-back`.

## Design decisions

1. **Tools are state machines.** Each controller is a small explicit state machine (`Idle`, `Pointing`, `Dragging`, `Finishing`) driven by `CanvasPointerEvent`; it emits exactly one `sheet.transact` per completed gesture. Unit tests feed scripted pointer sequences and assert the resulting transactions.
2. **Stroke tools delegate to Ink.** Pen, pencil, brush, and highlighter set the wet brush through `wetStrokeBrush` and, on `onWetStrokeFinished`, convert the Ink inputs to `StrokePoints`, assign the anchor and frame level from the first point and the current camera, and commit a `StrokeElement`. Ink mapping: `tool:pen` → `ink:pressure-pen`; `tool:pencil` → `ink:pressure-pen` at 60 percent opacity and 0.6x size (true textured pencil waits for Ink 1.1 stable); `tool:brush` → `ink:marker` at 2x size; `tool:highlighter` → `ink:highlighter` at 40 percent opacity (Ink handles self-overlap).
3. **Thickness is chosen in screen pixels at drawing time** (0.5 to 64 px, five presets plus a slider) and converted to frame units, so a "thin" line feels thin at any zoom.
4. **Colours**: a 16-swatch palette, HSV picker with hex entry, eight recents; each tool remembers its last colour; highlighter forces alpha.
5. **Shapes** (`tool:line`, `tool:rect`, `tool:ellipse`, `tool:arrow`) are drag-to-create with a live preview through the overlay; no fill in v1; a second pointer held down constrains proportions (square, circle, 45-degree lines). Only a second pointer that lands after the drag has started constrains; this is the one exception to the canvas engine's rule that two pointers belong to the camera (canvas-engine spec decision 9).
6. **Eraser** (`tool:eraser`) removes whole strokes and shapes whose geometry intersects the eraser path (Ink geometry intersection for strokes); images are never erased, they are deleted via select. Partial stroke erasing is a later feature pending Ink 1.1.
7. **Select** (`tool:select`): tap selects the topmost hit element; drag on empty space makes a marquee; a selection shows move, uniform-scale, and rotate handles plus a context bar (delete, duplicate, bring to front, send to back). Multi-select via marquee or long-press-add. Transforms change `anchor`, `size`, `rotationDeg`, or scale stroke points, each as one transaction per gesture.
8. **Hand** (`tool:hand`): one pointer of any type (finger or stylus) pans; two pointers pinch-zoom around the focal point and pan together; double-tap zooms 2x toward the tap. It never changes the sheet. The hand tool is always present in the default toolbar regardless of finger mode, so users on any device have an explicit "move around" tool. Independently of the hand tool, two-pointer pinch zoom and pan work in every tool; the finger-mode policy only decides what a single finger does (draw, or pan when a stylus has been seen).
9. **Zoom controls**: `cmd:zoom-in` and `cmd:zoom-out` step by a factor of 1.5 around the viewport centre with a 150 ms animation; `cmd:zoom-fit` frames all content; `cmd:zoom-100` returns to 1x. The default layout shows a compact zoom readout that opens these; layouts place it with the ref `widget:zoom`, a widget the host supplies to `ToolbarHost` (Task 19).
10. **Toolbar from spec.** `ToolbarHost` renders whatever `ToolbarSpec` it is given; the default spec ships as `assets/tool_layout_default.json` and is parsed at startup, which proves the hook `tool-config` will use. Unknown IDs render nothing and log a warning. Placement: bottom bar on phones, side rail on tablets, switchable in settings, mirrored for left-handed mode.
11. **Commands** are registered by ID so toolbar items, keyboard shortcuts (Ctrl+Z, Ctrl+Shift+Z, Ctrl+plus, Ctrl+minus, Ctrl+0 fit), and later plugins invoke the same code.

## Testing

- JVM unit tests per tool: scripted pointer sequences produce the expected transactions (count, element kinds, geometry within tolerance); cancellation mid-gesture commits nothing; constrain modifier produces exact squares and 45-degree lines; the hand tool produces camera changes and zero transactions; pinch sequences keep the focal point fixed in world space.
- Registry and spec tests: parsing the default layout; unknown references are kept in the parsed spec (so a newer layout round-trips) and skipped with a logged warning when rendered; round trip of `ToolbarSpec` through JSON; untrusted layouts are bounded (64 K characters, groups nested at most 4 deep, 128 items per list, ids and labels of at most 128 characters, `schemaVersion` of at least 1) and anything outside the bounds is a `ToolbarSpecException`. Until side rails are built, `left`/`right` toolbars render as rows (2026-10-20 review).
- Compose UI tests: tapping a tool button activates it; the toolbar re-renders when the spec changes; undo button state follows `canUndo`; zoom readout updates.
- Instrumented: drawing a stroke with each stroke tool creates a `StrokeElement` whose Ink mapping matches decision 2.

## Success criteria

1. Every v1 tool, including the hand tool, is reachable from the default toolbar and usable with finger and stylus; each has state-machine tests.
2. Replacing `tool_layout_default.json` with a different layout changes the toolbar with no code change.
3. A pen stroke drawn at 20,000x zoom, then viewed at 1x, is thinner than 0.5 px and still present; a stroke drawn at 1x and viewed at 20,000x is smooth.
4. Undo reverses exactly one gesture per tap; redo restores it; 200 gestures of history without memory growth beyond 20 MB.
5. Selection handles work on strokes, shapes, and images alike.
6. Pinch zoom keeps the point under the fingers stationary to within 1 px at every zoom level, in every tool.

## Decisions (owner, 2026-10-20)

1. The pencil ships in v1 as a pen preset labelled "Pencil"; revisit when Ink 1.1 is stable.
2. No stabilisation option in v1; Ink's motion prediction only.
3. No shape fill in v1.
4. A hand tool with pinch zoom is part of v1 (owner's addition).
