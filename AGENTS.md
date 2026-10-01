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
- **Prefer the `atlas_gates` tool over typing `gradlew` yourself.** It sets both
  variables, runs both flavors, and reports real test counts. Hand-written
  `gradlew` is the fallback when the tool cannot express the task, not the default.

## Commands

```powershell
# from apps/atlas-android; JAVA_HOME + ANDROID_HOME must be set (see handoff)
.\gradlew.bat :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest --console=plain
```

Prefer the equivalent tool call first. See the tool law below.

## Verification cadence (operator directive, 2026-09-29, AMENDED 2026-09-30)

**One shell per PHASE. Not per edit. Not per compile error.** A phase is the
whole task in the authorization you were handed.

### The tool law

There is a dedicated tool for almost everything, and the shell is the last
resort. Using the wrong one is the single most expensive mistake in this repo,
because a PowerShell round trip costs more wall clock than ten file reads.

| Need | Use | NEVER use |
|------|-----|-----------|
| Find text in a file | `grep` tool | `Select-String` in shell |
| Find a file by name | `glob` tool | `Get-ChildItem` in shell |
| Read a file | `read` tool | `Get-Content` in shell |
| Edit a file | `edit` / `write` tools | PS text pipelines (`RULES.md` §3 forbids) |
| Verify a MapLibre/Android signature | `atlas_maplibre_probe` | shell + `javap` |
| Check the pure boundary | `atlas_purity_scan` | shell + grep loop |
| Run the gates | `atlas_gates`, ONE call | ad-hoc `gradlew` |
| Install / launch / logcat | `atlas_device` | raw `adb` in shell |
| git | shell (no tool exists) | — |

`Select-String`, `Get-Content`, `Get-ChildItem`, and `Test-Path` in a shell have
no place in normal work. They exist in this repo only for the `atlas_*` tools'
own internals. Reaching for one means the equivalent tool was missed.

### The phase law

1. Do the whole task with `read` / `grep` / `glob` / `edit` / `write`.
2. Re-read the files you changed and fix every error you can find by eye.
3. Shell out ONCE, at the very end, for the gate.
4. If the gate fails, fix the reported errors with `edit`, then shell once more.
   Do NOT re-run the gate to inspect one line, and do NOT run it per error.

### The stop rule

A compile error from the gate is not an emergency. Read the whole error, fix it,
and let the next gate confirm. Re-running after each fix costs minutes each time
and buys nothing, because the errors were reported in one batch anyway.

If a gate failure suggests a deeper contradiction — a spec that cannot compile,
a signature that does not exist, an unknown that changes the design — STOP and
report. That is not an excuse to shell; it is the opposite. Ask, then continue
with the fix once answered.

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

`.opencode/agent/*.md` (subagents), `.opencode/plugin/atlas-tools.ts` (tools), and
`.opencode/opencode.json` (permissions). Restart opencode after changing any of them;
config is not hot-reloaded.

**Session archive:** `blueprints/app-track/HISTORY.md` holds every closed sprint
record that used to sit in `SESSION_HANDOFF.md`. It is NOT live state — do not read it
on a cold start. Its counts and statuses are stale by construction.

`.opencode/opencode.json` **denies** `Select-String*`, `Get-Content*`,
`Get-ChildItem*` and `Test-Path*` in bash. That is the hard version of the tool law
above: those four exist here only for the `atlas_*` tools' own internals. A denial
means the tool call is refused, not that a warning is printed — so if you reach for
one and it comes back denied, the answer is `grep`/`glob`/`read`, not a retry.

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
