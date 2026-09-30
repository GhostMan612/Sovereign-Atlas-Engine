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
- **Working JDK: `C:\Users\612co\.jdks\jbr-21.0.11`.** The Android Studio JBR at
  `C:\Program Files\Android\Android Studio\jbr` is stripped (no `lib/jvm.cfg`) and every
  Gradle invocation fails against it. Set both before any Gradle call:
  `$env:JAVA_HOME = "C:\Users\612co\.jdks\jbr-21.0.11"; $env:ANDROID_HOME = "C:\android"`

## Commands

```powershell
# from apps/atlas-android; JAVA_HOME + ANDROID_HOME must be set (see handoff)
.\gradlew.bat :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest --console=plain
```

## Verification cadence (operator directive, 2026-09-29)

**Batch every edit in a task, then shell out exactly ONCE at the end for the
gates.** No intermediate compiles, no grep sweeps, no per-edit checks. The
incremental-verification instinct in RULES.md 1A and 4.5 does NOT mean
re-verifying after each line edit — it means the gate must actually run before
any claim, not that it must run ten times.

Reads and file edits are free. Only shell out for: the single end-of-task gate
run, `adb` when device work is asked for, and git.

The one exception, which is not an excuse to shell: if a task contains a
contradiction where proceeding means guessing — a device serial that is not the
test device, a spec that will not compile, a root cause with no evidence — STOP
and report. That is cheaper than grinding, and it is what caught a wrong Carto
query parameter and a commit pushed to the wrong branch. One question beats ten
speculative edits.

Corollary for commit messages: do not claim a verification that did not happen.
State exactly which gate ran and what remains unverified.

## Session shape

1. RULES.md → SESSION_HANDOFF.md → task blueprint/ADR → work
2. Inspect before modifying; smallest coherent unit; tests/fixtures with it
3. Gates green (once, at the end) → handoff updated → commit by explicit path →
   **no push unless told**
4. Slash commands in `.opencode/commands/`: `/verify`, `/gates`, `/probe`, `/smoke`,
   `/smoketest`, `/lawcheck`, `/citriage`, `/diagnose`

## Repo-specific agents and tools

`.opencode/agent/*.md` (subagents) and `.opencode/plugin/atlas-tools.ts` (tools). Restart
opencode after changing any of them; config is not hot-reloaded.

Tools: `atlas_purity_scan` (engine/adapters boundary), `atlas_gates` (host gate with real
counts, working JDK baked in), `atlas_device` (install/launch/logcat/screenshot on the Moto G
`ZT4222BMWN`, wireless-TCP transport), `atlas_maplibre_probe` (javap against the Gradle
cache, AAR-aware).

Subagents: `atlas-boundary-auditor`, `atlas-gatekeeper`, `atlas-device-smoker`,
`atlas-api-prober`, `atlas-tracer`, `atlas-reviewer`, `atlas-law-keeper`,
`atlas-handoff-keeper`, `atlas-capability-auditor`, `atlas-ci-triage`,
`atlas-architecture-scout`, `atlas-artifact-prober`, `atlas-smoke-planner`.

## Standing facts (details in SESSION_HANDOFF.md)

- Pure-logic modules forbid MapLibre/Android/Compose types.
- Providers behind interfaces; renderers behind abstractions; offline first.
- Provenance/license travels with data. Unknown > invented. Evidence > status.
- CoT marker drops require Network Profile **Off-Grid Mesh Only** or **Hybrid**; Radio
  Silence and Cloud-Only refuse by design. Check the profile before blaming rendering.
- `style.addLayerBelow(layer, anchor)` has produced a layer that exists in `style.layers` and
  never paints. Plain `addLayer` works. Compare those two first when features reach their
  source but nothing draws.
- `getSourceAs<GeoJsonSource>(id)` returns null instead of throwing, so a style push is a
  silent no-op.
- CI `verify` is the only workflow. It has been green since run #72 (2026-09-29);
  judge a red run by per-step conclusions, not the job conclusion. Reads of CI
  output need a signed-in human: the step log is sign-in gated, the REST logs
  endpoint returns 403, and artifact download returns 401.
