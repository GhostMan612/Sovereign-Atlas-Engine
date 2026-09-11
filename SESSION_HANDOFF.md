# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence docs stay in
> `blueprints/app-track/`; this file points at them, never duplicates them.

## Where we are (2026-09-11)

- **Baseline:** `1f36043` clean, `origin/main == 1f36043` verified pre-flight.
  Phase B Slice 1 (foreground location) IMPLEMENTED locally, unpushed —
  commit hash recorded below. NO PUSH performed (operator decision).
- **Slice 1 — foreground location (DEC-021, local commit):** platform
  channel on framework `LocationManager` (no new pub/Gradle deps) +
  host adapter `apps/atlas/lib/location/location_service.dart` consuming
  `AtlasLocationFix` unmodified + position marker / accuracy circle /
  recenter on existing `MapController` + MAP/DEVICE readout + north-up
  (`initialRotation 0.0`, `rotate` never called). Manifest gains
  FINE+COARSE only. Stale bound 30 s (DEC-021 §2.G; DEC-012 stays open).
- **Track status:** Offline Areas + offline rendering + hardening +
  chunk-source timeout (DEC-020) all landed and verified. The map is now
  a located instrument foundation (foreground fixes); heading/follow/
  tactical/terrain remain later slices.
- **Rule set:** `RULES.md` canonical (Sovereign Directives ABSOLUTE —
  all 117 `.dart` files stripped to genesis-header-only, gates identical
  before/after), `AGENTS.md` trimmed to ramp, slash commands in
  `.opencode/commands/`.
- **Next milestone:** Phase B forensic/integration planning (NOT
  implementation): inventory `atlas_location` / `atlas_tactical` /
  `atlas_analysis` / `atlas_terrain` + app-host seams for the field
  navigation track, then a bounded plan. CARTO joins via
  evidence → contract → credential-boundary → fixture → implementation
  (keys never enter repo/history/logs).

## Standing environment facts

- **No builds here unless explicitly asked** (RULES.md §1.6). Gates are
  `pub get` / `analyze` / host `test`. `integration_test/` runs only on
  explicit ask (it compiles). Human builds + smoke-tests in Android
  Studio; debug from pasted output.
- Toolchain: Flutter 3.47 / Dart 3.13 / `C:\android` / bundled JDK.
- Emulator here is short-lived (~15–25 min); stale `multiinstance.lock`
  blocks reboot (delete it). `adb kill-server` fixes most wedges.
- Never pipe `adb pull`/binaries through PowerShell pipes; never edit
  sources via PS text pipelines (RULES.md §3).
- Physical device seen once: moto g 2025, launch + render clean, no
  errors (unstructured observation — physical-device proof NOT claimed).

## Verification posture

- Host: 79/79 app tests (52 baseline + 17 location-service unit + 10
  location widget, all fake-sourced) + engine 453/413/0/8/32 unchanged +
  analyze clean. Engine source untouched (zero `packages/*/lib` changes).
- Device (emulator, prior sessions): picker, acquisition, render
  (4 tiles / 8 serves / 56 attempts), blocked-transport failure,
  forced-timeout mapping — all green when run.
- Slice 1 device status: NOT RUN — no DEVICE-00X claimed. Manual smoke
  matrix specified in DEC-021 §5 for the human's Android Studio run
  (permission grant/deny/forever, services off, recenter, no-rotate,
  stale, UNKNOWN, no-fake-coordinate).
- Full MGRS blocked (MGRS-001). DEC-001..019 + MGRS-001 live outside
  this tree; in-repo decisions: ADR-001..005, DEC-020.
- Open threads: timeout engine-side scope (out — DEC-020 is app-only by
  decision); flutter_map's implicit 168h HTTP cache coexisting with
  engine-indexed packs (flagged for productization, no action).

## Next actions

1. Push authorization for the Slice 1 commit (operator decision).
2. Human smoke per DEC-021 §5 in Android Studio (operator-injected fixes).
3. Slice 2 planning (heading acquisition + compass + Face North) when
   authorized — needs its sensor/frame decision first.
4. CARTO provider contract (keys stay out of repo).
