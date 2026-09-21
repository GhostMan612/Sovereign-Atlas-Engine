# AGENTS.md — Sovereign Atlas (native Android host)

> Cold-start ramp. **Canonical law is `RULES.md` — it wins every conflict.**
> Then `SESSION_HANDOFF.md` (live state). Then the blueprint section for the task.

## What this is

Native Kotlin + MapLibre mapping/atlas app in `apps/atlas-android/`.
Pure-logic modules (`core/`, `geo/`, `camera/`, `layers/`, `field/`,
`measure/`, `goto/`, `track/`, `offline/`, `tactical/`) carry zero
MapLibre/Android/Compose imports; rendering lives in `map/` + `ui/`.
History docs under `blueprints/` + `docs/` describe the retired
Dart/Flutter lineage — read-only context, never a build target.

## Environment

- Android SDK `C:\android` / JDK 21 via Gradle / AGP 8.13.2 / Kotlin 2.1.0
- Writable: `C:\Sovereign-Atlas-Engine` only (+ gradle/sdk homes)
- Human builds in Android Studio. **No builds here unless explicitly asked** (RULES.md §1.6).

## Commands

```powershell
# from apps/atlas-android; JAVA_HOME + ANDROID_HOME must be set (see handoff)
.\gradlew.bat :app:testDebugUnitTest --console=plain   # host gate, no build
```

## Session shape

1. RULES.md → SESSION_HANDOFF.md → task blueprint/ADR → work
2. Inspect before modifying; smallest coherent unit; tests/fixtures with it
3. Gates green → handoff updated → commit by explicit path → **no push unless told**
4. Slash commands in `.opencode/commands/`: `/verify`, `/probe`, `/smoke`

## Standing facts (details in SESSION_HANDOFF.md)

- Pure-logic modules forbid MapLibre/Android/Compose types.
- Providers behind interfaces; renderers behind abstractions; offline first.
- Provenance/license travels with data. Unknown > invented. Evidence > status.
