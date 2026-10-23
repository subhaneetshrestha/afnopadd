# Tasks: drawing-tools

Spec: [spec.md](spec.md). Roadmap, checkpoints and the definition of done: [docs/product/roadmap.md](../../product/roadmap.md). Task numbers are global across features; paths in a task are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`.

| Task | Title | Status |
|---|---|---|
| [11](#task-11-pen-tool-registries-json-toolbar-editor-wiring) | Pen tool, registries, JSON toolbar, editor wiring | Done 2026-10-20 |
| [15](#task-15-stroke-tool-family-presets-thickness) | Stroke tool family, presets, thickness | Done 2026-10-20 |
| [16](#task-16-colour-palette-picker-recents) | Colour palette, picker, recents | Done 2026-10-20 |
| [18](#task-18-shape-tools-and-rendering) | Shape tools and rendering | Done 2026-10-21 |
| [19](#task-19-hand-tool-zoom-commands-finger-mode-policy-settings-store) | Hand tool, zoom commands, finger-mode policy, settings store | Done 2026-10-21 |
| [20](#task-20-select-tool-selection-marquee-move-delete-duplicate-z-order) | Select tool: selection, marquee, move, delete, duplicate, z-order | Done 2026-10-21 |
| [21](#task-21-select-tool-scale-and-rotate-handles) | Select tool: scale and rotate handles | Done 2026-10-21 |

---

## Task 11: Pen tool, registries, JSON toolbar, editor wiring

**Status:** Done 2026-10-20

**Description:** The slice that makes the skeleton walk: `ToolRegistry`, `Tool`/`ToolController`, `CommandRegistry` with `cmd:undo` and `cmd:redo`, the `PenTool` that turns `FinishedStroke` into a `StrokeElement` transaction, the `ToolbarSpec` model with JSON parsing, `ToolbarHost` rendering it, the default layout asset (pen, undo, redo), and an `EditorScreen` that opens a sheet through `SheetStore` (a fixed sheet directory until the library exists in Task 12) and composes canvas plus toolbar.

**Acceptance criteria:**
- [x] Drawing with the pen creates one `StrokeElement` per gesture with anchor and frame level from the stroke start; undo removes it, redo restores it.
- [x] The toolbar renders from `assets/tool_layout_default.json`; an unknown id in the file renders nothing and logs a warning.
- [x] Leaving the editor and reopening the sheet from disk shows all previous strokes (`EditorFlowTest`); durability across process death is covered by Task 6's persistence tests.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.tools.*"` (pen tool, registries, layout JSON; 14 tests), `ToolbarHostTest` and `EditorFlowTest` on the API 36 emulator
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: draw, kill via `adb shell am kill`, reopen, part of the owner's Checkpoint B review

**Dependencies:** Task 6, Task 10

**Files likely touched:**
- `tools/Tool.kt`, `tools/ToolRegistry.kt`, `tools/CommandRegistry.kt`, `tools/ToolbarSpec.kt`, `tools/ToolbarHost.kt`, `tools/pen/PenTool.kt`
- `app/src/main/assets/tool_layout_default.json`
- `shell/EditorScreen.kt`

**Estimated scope:** Medium

---

## Task 15: Stroke tool family, presets, thickness

**Status:** Done 2026-10-20

**Description:** Add pencil, brush, and highlighter as tools backed by `ToolPreset` and `ParamValue`, the `BrushSpec` mapping (pencil = pressure pen at 60 percent opacity and 0.6x size; brush = marker at 2x; highlighter = highlighter at 40 percent), `CurrentStyle` state with per-tool memory, thickness presets (five) and slider (0.5 to 64 px) with screen-pixel to frame-unit conversion, and the tool options panel. Add the tools to the default layout.

**Acceptance criteria:**
- [x] Each tool produces a `StrokeElement` whose `BrushSpec` matches the mapping table in `docs/features/drawing-tools/spec.md`.
- [x] A 3 px thickness chosen at 10x zoom stores a frame-unit size that renders 3 px on screen at that zoom and 0.3 px at 1x.
- [x] Switching tools restores each tool's last thickness and colour (styles live in the app container for the session).

**Verification:**
- [x] Tests pass: `StrokeToolTest` (seven JVM tests), `StrokeOptionsBarTest`, and the pencil case in `EditorFlowTest`; full suite of 53 instrumented tests green on `afnopadd36`
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: draw with all four stroke tools at several zooms (owner)

**Dependencies:** Task 11

**Files likely touched:**
- `tools/presets/ToolPreset.kt`, `tools/presets/BuiltInPresets.kt`, `tools/stroke/StrokeTools.kt`, `tools/CurrentStyle.kt`, `tools/options/ThicknessOptions.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

**Notes:**

The four tools are `ToolPreset`s of one `StrokeTool`, which is the shape user presets will take in the tool-config phase. Styles reset when the app restarts; persisting them belongs with the settings store in Task 19.

---

## Task 16: Colour palette, picker, recents

**Status:** Done 2026-10-20

**Description:** Build the colour UI: 16-swatch palette, HSV picker with hex entry, eight recents persisted in DataStore, highlighter alpha forcing, and the toolbar colour button that opens it.

**Acceptance criteria:**
- [x] Picking a swatch or entering a hex value updates the active tool's style and the next stroke uses it.
- [x] Recents hold the last eight distinct colours across process restarts (DataStore `settings`).
- [x] The highlighter ignores the chosen alpha and applies its fixed 40 percent (covered in `StrokeToolTest`).

**Verification:**
- [x] Tests pass: `ColorLogicTest` (JVM: hex, HSV, palette, recents), `ColorPickerTest`, `RecentColorsStoreTest` (on device, because DataStore's atomic rename cannot overwrite on a Windows host JVM), `StrokeOptionsBarTest`, and the palette case in `EditorFlowTest`; full suite of 59 instrumented tests green
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: pick colours, restart, confirm recents (owner)

**Dependencies:** Task 15

**Files likely touched:**
- `tools/color/ColorPalette.kt`, `tools/color/ColorPicker.kt`, `tools/color/RecentColors.kt`, `tools/ToolbarHost.kt`

**Estimated scope:** Medium

---

## Task 18: Shape tools and rendering

**Status:** Done 2026-10-21

**Description:** Render `ShapeElement`s (line, rectangle, ellipse, arrow with heads scaled to width) through the element matrix, and add the four shape tools with drag-to-create, live overlay preview, and the second-pointer constrain modifier (square, circle, 45-degree lines). No fill.

**Acceptance criteria:**
- [x] Each shape tool commits one `ShapeElement` per drag with geometry in frame units and the current colour and thickness (`ShapeToolTest`; `EditorFlowTest.theRectangleToolDrawsOneRectangle` end to end).
- [x] With the constrain modifier held, rectangles are exact squares and lines snap to 45-degree multiples (`ShapeToolTest` on scripted input; `SheetCanvasGestureTest` checks that a second finger during a shape drag constrains rather than pinches).
- [x] Shapes stay crisp at 50x and hit-test through `HitTester` (`ShapeRenderingTest`: a 2-unit line is 100 px wide at 50x with a sharp edge; `HitTesterTest` covers arrowheads; outline geometry is shared in `ShapeOutline`).

**Verification:**
- [x] Tests pass: `.\gradlew.bat check` (162 unit tests, Lint); full instrumented suite 103/103 on a cold `afnopadd36` (2026-10-21), including `ShapeRenderingTest` (screen vs `SoftwareRenderer` within 1 percent for all four shapes). Mutation checks: dropping the dry-layer shape draw fails all 5 rendering tests; ignoring `secondPointerConstrains` fails the gesture test.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: draw all four shapes (owner)

**Dependencies:** Task 17

**Files likely touched:**
- `canvas/internal/ShapePainter.kt`
- `tools/shape/ShapeTools.kt`, `tools/shape/ShapePreviewOverlay.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

**Notes:**

the preview is drawn by `SheetCanvas` from `ToolController.previewElements` after the dry layer, not by a separate overlay file. The second-pointer modifier conflicted with "two pointers always belong to the camera"; resolved in canvas-engine spec decision 9 (only a second pointer that lands after a shape drag starts constrains). With four more tools the default toolbar row is wider than a phone (about 510 dp on a 411 dp screen), so rows now scroll sideways; undo and redo sit past the edge on phones until the layout is revisited.

---

## Task 19: Hand tool, zoom commands, finger-mode policy, settings store

**Status:** Done 2026-10-21

**Description:** Add the hand tool (one pointer pans, two pinch-zoom, never edits), the zoom commands (`cmd:zoom-in`, `cmd:zoom-out`, `cmd:zoom-fit`, `cmd:zoom-100`) with the zoom readout, `SettingsRepository` on DataStore (`fingerMode`, `toolbarPlacement`, `defaultBackground`, `keepScreenOn`, `leftHanded`), the persisted "stylus seen" flag, and the input policy that decides whether a single finger draws or pans.

**Acceptance criteria:**
- [x] The hand tool changes only the camera; the sheet's transaction count stays zero during its use (`HandToolTest`; `EditorFlowTest.theHandToolPansWithoutChangingTheSheet` with finger and stylus: camera moves, no elements, undo stays disabled).
- [x] In AUTO mode a finger draws until a stylus pointer is seen; afterwards a finger pans and the stylus draws; forcing either mode in settings overrides this (`InputPolicyTest`; `EditorFlowTest.aFingerDrawsUntilAStylusIsSeenThenPans` with real stylus events, and `forcingFingerDrawingOverridesASeenStylus`).
- [x] Zoom in/out step by 1.5x around the viewport centre with animation; fit frames all content with padding; 100 percent returns to 1x (`ZoomCommandsTest`, `CameraAnimatorTest`: 150 ms ease-in-out, geometric in zoom; `EditorFlowTest.theZoomReadoutZoomsInAndFitsTheContent`).

**Verification:**
- [x] Tests pass: `.\gradlew.bat check` (183 unit tests, Lint); instrumented `ToolbarHostTest` (6), `SettingsRepositoryTest` (2), `EditorFlowTest` (13). Full suite on a cold `afnopadd36`: 112/114; the two failures were the new `SettingsRepositoryTest` reopening its file before the old DataStore had let go (it cancelled its scope without waiting). Fixed with `cancelAndJoin` there and in `RecentColorsStoreTest`, which had the same latent race; both then passed three runs in a row. Mutation checks: not recording the stylus fails the finger-policy flow; ignoring the active tool in the policy fails the hand-tool flow.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: hand tool and finger policy on both devices (owner)

**Dependencies:** Task 17

**Files likely touched:**
- `tools/hand/HandTool.kt`, `tools/commands/ZoomCommands.kt`, `tools/InputPolicy.kt`, `tools/ZoomReadout.kt`
- `shell/settings/SettingsRepository.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

**Notes:**

the default layout is now two rows (hand and stroke tools; shapes, undo, redo and the zoom readout), so nothing sits off-screen on a 411 dp phone. The readout is a `widget:zoom` ref rendered from a widget the editor supplies. Settings applied so far: finger mode, the stylus flag, keep-screen-on and the default background (used for every sheet until sheets carry their own); toolbar placement and left-handed mode are stored for the side-rail work. The settings screen itself is Task 30's. Follow-up done the same day: per-tool colour and width now persist (`ToolStylesStore` in the settings file, restored at startup over the defaults, saved 300 ms after the last change; `ToolStylesPersistenceTest`, `ToolStylesStoreTest`, which fails if the restore is dropped). `SheetCanvas` now hands its gesture handler one stable role function, so a settings change mid-stroke cannot restart the handler and cut the stroke.

---

## Task 20: Select tool: selection, marquee, move, delete, duplicate, z-order

**Status:** Done 2026-10-21

**Description:** Implement the select tool's first half: tap selects the topmost hit element, drag on empty space draws a marquee, long-press adds to the selection, dragging a selection moves it (anchors change, one transaction per gesture), and the context bar with `cmd:delete-selection`, `cmd:duplicate-selection`, `cmd:bring-to-front`, `cmd:send-to-back` using fractional indexes.

**Acceptance criteria:**
- [x] Tap, marquee, and long-press-add produce the expected selection sets on a scripted fixture (`SelectToolTest`: topmost hit, empty tap clears, long press toggles, marquee takes only fully enclosed elements, a wobble under 8 px is still a tap).
- [x] Moving a selection of a stroke, a shape, and an image updates all three anchors in one undoable transaction (`SelectToolTest`; dragging an unselected element selects and moves only it; a cancelled drag commits nothing).
- [x] Bring to front and send to back reorder only the selected elements' `z` values (`SelectionCommandsTest`, which also covers delete, duplicate with a 16 px offset and new ids, and disabled commands without a selection).

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.tools.select.*"` (17); `.\gradlew.bat check` (205, Lint); instrumented `SelectionOverlayTest` (2) and `EditorFlowTest.theSelectToolSelectsMovesDuplicatesAndDeletes`; full suite 120/120 on a cold `afnopadd36`, no native crash. Mutation checks: ignoring the long press, reordering every element, and not drawing the outline each fail a test.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: select, move, reorder on a device (owner)

**Dependencies:** Task 18

**Files likely touched:**
- `tools/select/SelectTool.kt`, `tools/select/SelectionState.kt`, `tools/select/SelectionOverlay.kt`, `tools/select/SelectionCommands.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

**Notes:**

a long press is recognised when the pointer lifts (held 500 ms or more without moving), since tools only see pointer events; there is no feedback at the 500 ms mark yet. A moved selection is previewed with the existing hidden and preview elements, and its outline follows the copies. The context bar is a built-in `ToolbarSpec` rendered by `ToolbarHost`, shown above the toolbars while the select tool has a selection (it floats, so the canvas never resizes, ADR 0003). `Element.withCommon` and `movedBy` edit any element kind's shared fields, including unknown kinds. Leaving the select tool clears the selection; undo and redo drop selected elements that leave the sheet.

---

## Task 21: Select tool: scale and rotate handles

**Status:** Done 2026-10-21

**Description:** Add uniform-scale and rotate handles to the selection overlay. Scaling multiplies stroke points and shape geometry about the selection centre; rotation sets `rotationDeg` on images and shapes and rotates stroke points; both are one transaction per gesture and work on multi-selections.

**Acceptance criteria:**
- [x] Scaling a selection by 2x doubles distances between element anchors and stroke extents within float tolerance; the inverse gesture restores them (`TransformsTest`; `SelectToolTest` drags the scale handle to twice the distance).
- [x] Rotating 90 degrees produces the expected geometry for strokes, shapes, and images (`TransformsTest`: stroke points and pen orientation, line endpoints, rectangle and image `rotationDeg` about their centre, four quarter turns back to the start; `SelectToolTest` turns the rotate handle a quarter, with 15-degree snapping).
- [x] Handles stay a constant screen size at every zoom (`SelectToolTest` checks their offsets from 0.01x to 20,000x; `SelectionOverlayTest` measures the drawn ring at 20x and 50x).

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.tools.select.*"` (29); `.\gradlew.bat check` (222, Lint); instrumented `SelectionOverlayTest` (3), `ShapeRenderingTest.aRotatedSquareIsDrawnAsADiamond`, `EditorFlowTest.theSelectionHandlesScaleAndRotateIt`; full suite 123/123 on a cold `afnopadd36`, no native crash. Mutation checks: handles that ignore zoom, no snapping, hit-testing or painting without rotation, and a codec that drops it each fail a test.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: scale and rotate mixed selections (owner)

**Dependencies:** Task 20

**Files likely touched:**
- `tools/select/TransformHandles.kt`, `tools/select/Transforms.kt`, `tools/select/SelectTool.kt`

**Estimated scope:** Medium

**Notes:**

`ShapeElement` gained `rotationDeg` (default 0, written only when non-zero, so existing files are unchanged; the format is not frozen until the first sharing release; recorded in sheet-document spec). Rectangles and ellipses turn about their box centre; a turned line or arrow stores turned endpoints. Images already had `rotationDeg`; its meaning is now fixed as a turn about the image's centre with the anchor at its unturned corner (image drawing arrives in Task 23). Notes and unknown element kinds are left untransformed. A press on a handle only transforms once it drags past the slop, so a tap near a small selection's handle still selects (found by the existing tap test). The scale handle is at the outline's bottom-right corner and the rotate handle floats 28 dp above its top centre; both sit in dp converted through the zoom.
