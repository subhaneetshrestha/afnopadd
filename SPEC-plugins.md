# Spec: plugins (later phase, outline)

Status: **OUTLINE, direction approved by the owner on 2026-10-20.** Module id `plugins`. Depends on: `tool-config`, `drawing-tools`. Package: `io.github.subhaneetshrestha.afnopadd.plugins`. Project-wide rules in `SPEC.md` apply. A full spec is written when the phase is scheduled; this outline fixes the direction so earlier modules do not close it off.

## Objective

Let users install small plugin packages that add custom tools, commands, and panels through a sandboxed scripting runtime with a capability-scoped drawing API, so behaviour that config cannot express becomes possible without forking the app.

## Direction

- **Package:** a ZIP with `manifest.json` (`id`, semver `version`, `apiVersion`, `minAppVersion`, `permissions[]`, `contributes: { tools, presets, commands, panels }`) and `main.js`, installed from the documents UI into `files/plugins/<id>/`.
- **Runtime:** QuickJS through `quickjs-kt` (Apache-2.0, about 1.2 MB per ABI, to be verified) on a dedicated thread with evaluation timeouts, memory limits, and cancellation. Chosen for the same reason Figma moved to QuickJS: a separate interpreter is a real sandbox, same-VM tricks are not. Adding the dependency requires the owner's approval at the time.
- **Capability API:** the script sees only an object we hand it: `canvas.beginStroke/addPoint/endStroke`, `shapes.add`, `selection.get/set`, `ui.showPanel`, `storage` scoped to the plugin. No file system, no network, no reflection; every capability is listed in `permissions` and shown at install time.
- **Registration:** contributed tools register with `ToolRegistry` using IDs namespaced by plugin (`plugin:<id>/tool:<name>`); contributed commands register with `CommandRegistry`; toolbar placement comes from `tool-config` layouts.
- **Policy:** F-Droid distribution has no rule against interpreted plugins; Google Play, if ever used, allows sandboxed interpreted code but forbids downloaded native or dex code, which this design never does.
- **Threat model to document:** malicious script exhausting CPU or memory (timeouts and limits), reading other sheets (no file API), exfiltration (no network), UI spoofing (panels are clearly labelled with the plugin name).

## Hooks required from earlier modules

- Stable IDs and registries in `drawing-tools`; `ToolbarSpec` from `tool-config`.
- `sheet-document` transactions labelled with their source so plugin changes are undoable like user changes but distinguishable in the op log.

## Success criteria (to be refined)

1. A sample plugin adding a "dotted stamp" tool installs, appears in the toolbar via a layout, draws through the capability API, and can be uninstalled cleanly.
2. A plugin that loops forever is stopped within 2 s and reported, with the app still responsive.
3. A plugin cannot read or write anything outside its scoped storage.

## Open questions

1. Script language: JavaScript via QuickJS (proposal) or Lua via LuaJ (pure JVM, no native code, weaker ecosystem)?
2. Plugin discovery: manual file install only (proposal), or a curated list hosted in the repository (would need network access, which is an ask-first item)?
