# Spec: tool-config (later phase, outline)

Status: **OUTLINE, pending owner approval** (2026-10-20). Module id `tool-config`. Depends on: `drawing-tools`. Package: `io.github.subhaneetshrestha.formchecker.toolconfig`. Project-wide rules in `SPEC.md` apply. This outline records the contract `drawing-tools` must honour in v1; a full spec is written when the phase is scheduled.

## Objective

Let users import a JSON file that defines their own toolbar layout (which tools, grouping, order, defaults, shortcuts) and, as a second layer, their own presets (parametric brushes, stamps, macros) without writing code.

## Scope

**In (when scheduled):** layout JSON schema v1 and preset JSON schema v1; import via the documents UI; validation with human-readable errors; apply, preview, and reset to default; multiple saved layouts; export of the current layout.

**Out:** executable behaviour of any kind (`plugins`), tool implementations (`drawing-tools`).

## Contract and hooks required from v1

- `drawing-tools` renders the toolbar only from `ToolbarSpec` and ships its default as `assets/tool_layout_default.json`, which is a valid Layer 1 document.
- Every tool, preset, and command has a stable string ID (`tool:pen`, `preset:fine-liner`, `cmd:undo`); IDs never change once released.
- `ToolPreset(id, baseToolId, params: Map<String, ParamValue>)` with the sealed `ParamValue` (`Const`, `Curve`, `Color`) is the preset model; a preset with an unknown base tool or parameter is dropped with a warning, never fatal.
- Every config file carries `schemaVersion` and `source`; decoding uses `ignoreUnknownKeys = true`; migrations are pure JSON-to-JSON functions applied before typed decoding.

Layer 1, layout JSON (shape, not final):

```json
{ "schemaVersion": 1, "source": "user",
  "toolbars": [ { "id": "main", "placement": "bottom",
    "items": [ { "ref": "tool:pen" }, { "ref": "preset:fine-liner" }, { "separator": true },
               { "group": { "id": "shapes", "label": "Shapes", "items": [ { "ref": "tool:line" }, { "ref": "tool:rect" } ] } },
               { "ref": "cmd:undo" } ] } ],
  "defaults": { "color": "#1E6F5C", "widthPx": 3 },
  "shortcuts": { "ctrl+z": "cmd:undo" } }
```

Layer 2, preset JSON (MyPaint-style base value plus input curves):

```json
{ "schemaVersion": 1, "id": "preset:fine-liner", "base": "tool:pen", "label": "Fine liner",
  "params": { "widthPx": { "value": 2, "curve": { "pressure": [[0, 0.6], [1, 1.0]] } }, "opacity": 1.0, "color": "#000000" } }
```

Config-only ceiling: parametric brushes, stamps and composite shapes, snapping and grid, macros as ordered command lists. Anything conditional or looping is a language and belongs to `plugins`.

## Success criteria (to be refined)

1. A hand-written layout file changes the toolbar without a code change; an invalid file reports the line and reason and leaves the current layout untouched.
2. Presets round-trip through export and import unchanged.
3. Layout files from schema version 1 keep working in every later version.

## Open questions

1. Validate with a JSON Schema library (third-party dependency, needs approval) or hand-written validation on the decoded model? Hand-written is the proposal for Layer 1.
2. Should layouts be shareable as part of a sheet package, or only as separate files?
