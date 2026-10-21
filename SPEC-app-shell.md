# Spec: app-shell

Status: **APPROVED by the owner on 2026-10-20.** Module id `app-shell`. Depends on: `sheet-library`, `drawing-tools`, `image-board`, `sharing`. Package: `io.github.subhaneetshrestha.afnopadd.shell`. Project-wide rules in `SPEC.md` apply.

## Objective

The frame everything else hangs on: the activity, navigation between the library and the editor, theme, dependency wiring, settings, and lifecycle handling (flush on stop, state across rotation and process death). A two-screen skeleton of this module is the first implementation task so every other module can be exercised in the running app from day one.

## Scope

**In:** `MainActivity`; Navigation 3 back stack with `Library` and `Editor(sheetId)` destinations; Material 3 theme (light/dark follows the system, no dynamic colour so screenshots are consistent); manual dependency container; settings screen backed by DataStore; about screen with licence and third-party notices; lifecycle hooks; edge-to-edge and predictive back; keep-screen-on while editing; orientation and window-size handling.

**Out:** any feature logic; those live in the modules the shell composes.

## Contract

```kotlin
class AppContainer(context: Context) { val sheetStore: SheetStore; val library: SheetLibrary; val toolRegistry: ToolRegistry; val commands: CommandRegistry; val exporter: Exporter; val settings: SettingsRepository }
sealed interface Destination { data object Library : Destination; data class Editor(val sheetId: SheetId) : Destination; data object Settings : Destination; data object About : Destination }
interface SettingsRepository { val settings: Flow<Settings>; suspend fun update(block: (Settings) -> Settings) }
data class Settings(val fingerMode: FingerMode = FingerMode.AUTO, val toolbarPlacement: ToolbarPlacement = ToolbarPlacement.AUTO, val defaultBackground: Background = Background.PLAIN, val keepScreenOn: Boolean = true, val leftHanded: Boolean = false, val stylusSeen: Boolean = false)
```

As built in Task 19: `FingerMode { AUTO, DRAW, NAVIGATE }` lives in `drawing-tools` (`tools/InputPolicy.kt`), `ToolbarPlacement { AUTO, BOTTOM, SIDE }` in `shell/settings` (the toolbar spec's own `Placement` has no AUTO), and the persisted "stylus seen" flag is part of `Settings`. Settings share the `settings` preferences file with the recent colours; enums are stored by name and an unknown name reads as the default.

## Design decisions

1. **Navigation 3** (`navigation3-runtime` and `navigation3-ui` 1.2.0) with a serializable back stack saved in `rememberSaveable`, so rotation and process death restore the editor on the same sheet; the editor's camera is saved alongside.
2. **Manual dependency injection** through one `AppContainer` created in `Application`; no Hilt or Koin (both would need approval and neither is needed at this size).
3. **Lifecycle.** `onStop` calls `sheet.flush()` and schedules a thumbnail refresh; `onDestroy` with `isFinishing` closes the sheet. The activity handles `ACTION_VIEW`/`ACTION_SEND` by delegating to `sharing.PackageReceiver` and then navigating to the editor.
4. **Finger mode AUTO** means fingers draw until a stylus pointer has been seen on the device (persisted flag), after which fingers pan; the setting can force either.
5. **Window sizes.** Compact width uses the bottom toolbar; medium and expanded use a side rail; the editor is usable in split-screen.
6. **Theme** uses the Material 3 baseline palette with a single brand colour; the canvas background is independent of the theme.
7. **About** lists the licence (GPL-3.0-or-later), version, and an auto-generated third-party notice from the dependency list.

## Testing

- Compose UI tests: library → editor → back; rotation keeps the sheet and camera; settings toggles persist.
- Instrumented: process-death restoration through `ActivityScenario.recreate()` and the "Don't keep activities" developer option path; intent delivery into the import flow.

## Success criteria

1. Cold start to the library in under 1 s on the reference phone (measured with `adb shell am start -W`).
2. Rotating the editor keeps sheet, camera, tool, and selection; the same after process death.
3. Every screen has a Compose preview and renders correctly in light and dark theme.
4. The navigation skeleton (library and editor placeholders) is the first commit of implementation.

## Decisions (owner, 2026-10-20)

1. The placeholder green-check icon and colour stay until a design pass; a real icon is a release blocker listed in `SPEC.md`.
2. Left-handed mode (mirrored toolbar) is a v1 setting.
3. Finger mode AUTO as described in decision 4; the hand tool from `drawing-tools` is always available in addition.
