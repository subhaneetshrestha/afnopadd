# Afnopadd docs

Specs and tasks are kept per feature, so everything about one capability is in one folder. Feature ids are the module ids in [product/capability-map.md](product/capability-map.md).

| Path | What it holds |
|---|---|
| [product/spec.md](product/spec.md) | Project-wide spec: objective, stack, code style, testing strategy, boundaries, v1 success criteria |
| [product/capability-map.md](product/capability-map.md) | The feature (module) ids, their responsibilities and dependencies |
| [product/roadmap.md](product/roadmap.md) | Implementation plan: architecture decisions, order of work, checkpoints, definition of done, risks |
| `features/<id>/spec.md` | The feature's spec |
| `features/<id>/tasks.md` | The feature's tasks with status, acceptance criteria and verification |
| [adr/](adr/) | Architecture decision records (`NNNN-title.md`) |
| [checklists/](checklists/), [perf/](perf/) | Manual checklists and performance notes |
| [research/](research/) | Research notes |

## Features

| Feature | Spec | Tasks |
|---|---|---|
| `sheet-document` | [spec](features/sheet-document/spec.md) | [tasks](features/sheet-document/tasks.md) |
| `canvas-engine` | [spec](features/canvas-engine/spec.md) | [tasks](features/canvas-engine/tasks.md) |
| `drawing-tools` | [spec](features/drawing-tools/spec.md) | [tasks](features/drawing-tools/tasks.md) |
| `image-board` | [spec](features/image-board/spec.md) | [tasks](features/image-board/tasks.md) |
| `sheet-library` | [spec](features/sheet-library/spec.md) | [tasks](features/sheet-library/tasks.md) |
| `sharing` | [spec](features/sharing/spec.md) | [tasks](features/sharing/tasks.md) |
| `app-shell` | [spec](features/app-shell/spec.md) | [tasks](features/app-shell/tasks.md) |
| `tool-config` (later phase) | [spec](features/tool-config/spec.md) | [tasks](features/tool-config/tasks.md) |
| `plugins` (later phase) | [spec](features/plugins/spec.md) | [tasks](features/plugins/tasks.md) |

## Conventions

These apply to every spec and task, including ones added later. Never create `SPEC*.md`, `tasks/` or `todo.md` at the repository root.

- **A new feature** gets a module id in the capability map and a folder `features/<id>/` with `spec.md` and `tasks.md`.
- **Specs** follow this outline: `# Spec: <id>`, a `Status:` line (status, module id, dependencies, package, and that `docs/product/spec.md` applies), then `## Objective`, `## Scope`, `## Contract`, `## Design decisions`, `## Testing`, `## Success criteria`, `## Decisions (owner, <date>)`. An outline for a later phase may use `## Direction`, `## Hooks required from earlier modules` and `## Open questions` instead.
- **Tasks** go in the `tasks.md` of the feature whose package the work mainly touches. Task numbers are global across features and never reused; a follow-up gets a letter suffix (`9b`). Each new task is also added, with a link, at its place in the roadmap's task list.
- **Task file layout:** `# Tasks: <id>`, a line linking the spec and the roadmap, a summary table (`| Task | Title | Status |`) kept in step with the tasks, then each task separated by `---`.
- **Task layout**, sections in this order (leave out one that does not apply):

  ```markdown
  ## Task N: Title

  **Status:** Not started | In progress (what remains) | Done YYYY-MM-DD (what is still pending, if anything)

  **Description:** What and why.

  **Acceptance criteria:**
  - [ ] Observable outcome, with the test that proves it once done.

  **Verification:**
  - [ ] Tests pass: ...
  - [ ] Build succeeds: `.\gradlew.bat assembleDebug`
  - [ ] Manual check: ...

  **Dependencies:** Task M

  **Files likely touched:**
  - `package/File.kt`

  **Estimated scope:** Small | Medium

  **Notes:**

  Findings, deviations from the spec, measured numbers, follow-ups.
  ```

- **Paths in tasks** are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`. Tests mirror the package under `app/src/test/java/...` (JVM) or `app/src/androidTest/java/...` (device).
- **Checkpoints and cross-feature reviews** live in the roadmap, not in a feature.
- **Code comments** cite a spec as `<id> spec decision N` (for example `drawing-tools spec decision 9`), and the project spec as `docs/product/spec.md`.
