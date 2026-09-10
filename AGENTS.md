# AGENTS.md — Sovereign Atlas Engine

> Cold-start ramp. **Canonical law is `RULES.md` — it wins every conflict.**
> Then `SESSION_HANDOFF.md` (live state). Then the blueprint section for the task.

## What this is

Reusable, modular, portable geospatial mapping/atlas engine (Flutter+Dart).
Engine core in `packages/`; first host app in `apps/atlas`; integrations in
`integrations/`. See `docs/architecture/ADR-001-*` for the package map,
`blueprints/phase-0/dependency-map.md` for allowed directions,
`blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md` (frozen v1.0, 2026-09-07).

## Environment

- Flutter 3.47 / Dart 3.13 / Android SDK `C:\android` / bundled JDK
- Writable: `C:\Sovereign-Atlas-Engine` only (+ gradle/pub-cache/sdk homes)
- Human builds in Android Studio. **No builds here unless explicitly asked** (RULES.md §1.6).

## Commands

```powershell
flutter pub get
flutter analyze --no-pub 2>&1 | Select-Object -Last 3   # expect "No issues found!"
flutter test test/ 2>&1 | Select-Object -Last 2         # host gates, no build
dart tools/atlas_tool.dart all                          # engine fixture suite
```

## Session shape

1. RULES.md → SESSION_HANDOFF.md → task blueprint/ADR → work
2. Inspect before modifying; smallest coherent unit; tests/fixtures with it
3. Gates green → handoff updated → commit by explicit path → **no push unless told**
4. Slash commands in `.opencode/commands/`: `/verify`, `/probe`, `/smoke`

## Standing facts (details in SESSION_HANDOFF.md)

- Engine contracts forbid Flutter/Android/map-SDK types in `packages/*/lib`.
- Providers behind interfaces; renderers behind abstractions; offline first.
- Provenance/license travels with data. Unknown > invented. Evidence > status.
