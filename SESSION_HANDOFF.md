# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence docs stay in
> `blueprints/app-track/`; this file points at them, never duplicates them.

## Where we are (2026-09-11)

- **Baseline:** `eb2586b` (Slices 1–3 closure) clean pre-flight; Slice 4A
  IMPLEMENTED locally (commit hash below), unpushed. NO PUSH performed
  (operator decision).
- **Slice 4A — measurement (local commit):** host-only `MeasureState`
  (`apps/atlas/lib/measure/measure_state.dart`: ephemeral A/B/unit,
  engine haversine/bearing, coincident→undefined bearing) + persistent
  bottom sheet (Scaffold key; modal would swallow map taps) + `onTap` B
  capture + amber polyline + green/orange endpoint dots + GPS/map-center
  labeled A + units/DMS readouts + dismiss/restart clears. No engine
  changes (0-line `packages/` diff), no new deps, no Kotlin/permissions.
  Test note: flutter_map holds single taps ~300 ms for double-tap
  detection — widget tests pump 500 ms after `tapAt`; taps must land on
  open map above the sheet.
- **Analyzer hygiene (pushed in `176e382`):** removed 2 dead rule entries
  (`unused_import`, `unnecessary_null_comparison`) from root
  `analysis_options.yaml` + satisfied `require_trailing_commas` /
  `prefer_final_locals` across 4 files (formatting-only; engine suite
  identical 453/413/0/8/32). Root `dart analyze` now reports
  "No issues found!".
- **Slices 2+3 (DEC-022, local commit):** `TYPE_ROTATION_VECTOR` via new
  host-only `HeadingChannel` (no new deps; magnetic proven from android-36
  platform sources; true = magnetic + `GeomagneticField` declination at
  last-known, altitude 0 documented) + `HeadingService` (injectable
  source; TRUE-preferred display; accuracy dimming; no engine changes) +
  compass dial overlay (tap = Face North) + heading-up toggle driving
  `rotate(-true)` in place + MAP readout carries orient token
  (BottomAppBar `height: 96.0` per SDK-read M3 chrome math). Follow mode
  explicitly excluded (deferred per graph).
- **Track status:** Offline Areas + DEC-020 + Slices 1–3 (closed) +
  Slice 4A (implemented, NOT device-smoked — no closure claimed).
  Remaining: Slice 4B waypoint/journal, 4C go-to, 4D track, 4E GPX
  export, follow policy, tactical UI, terrain/LOS UI, CARTO, full MGRS.
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

## Slices 1–3 closure — Moto G 2025 physical smoke (2026-09-11)

- **Device:** Moto G 2025 physical hardware (not emulator — strictly more
  valuable than emulator for this slice). Implementation baseline
  `18895c3`; scope audit at closure: engine diff `e8d6211..HEAD` is
  formatting-only (4 files, suite-identical), and `apps/atlas/lib`
  contains no follow/GPX/waypoint/measure/track/terrain/CARTO/MGRS/
  background-location code (sole `background` hit is pre-existing
  offline-prefetch copy).
- **Results:**

| Capability | Result |
|---|---|
| Location permission/state/fix/marker/circle/recenter/no-fix safety | Previously verified |
| Compass enable/disable | PASS |
| Physical heading response (rotates with device) | PASS |
| Cardinal labeling | PASS |
| TRUE frame (with location fix) | PASS |
| MAG fallback (location disabled) | PASS |
| Face North (compass tap → 0°) | PASS |
| Heading-up (rotates, center fixed) | PASS |
| Heading-up exit → 0° | PASS |
| Sensor accuracy dimming | NOT OBSERVED (not manufactured; not a failure) |
| Sensor-less unsupported state | N/A — physical device |
| Follow mode / course display | Correctly absent |

- **Bearing-imprecision observation (recorded, NOT a defect):**
  "Physical device confirms live heading response. Heading tracks device
  rotation but exhibits observable bearing error; no quantitative accuracy
  characterization was performed." Pipeline function (A) and bearing
  accuracy (B) are separate questions. No offsets, smoothing, calibration
  constants, or algorithm tweaks authorized on this observation alone;
  compass-grade accuracy, if ever wanted, becomes its own
  evidence/diagnostic investigation — never drive-by drift.
- **No-fix clarification:** location-unavailable yields TRUE-unavailable
  with MAG still available; no coordinate is invented for declination.
  Granted-but-no-fix remains covered by Slice 1 states, not re-proven
  here.
- **Status: Slices 1–3 CLOSED.** No code changed by this closure.

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
- Physical device: Moto G 2025 — structured Slices 1–3 closure smoke
  recorded above (previously: single unstructured launch observation).

## Verification posture

- Host: 128/128 app tests (101 Slice 1–3 baseline + 12 measure-state
  unit + 15 measure widget, all fake/ephemeral-state sourced) + engine
  453/413/0/8/32 untouched + analyze clean. Engine source untouched
  (zero `packages/` diff for Slice 4A).
- Device (emulator, prior sessions): picker, acquisition, render
  (4 tiles / 8 serves / 56 attempts), blocked-transport failure,
  forced-timeout mapping — all green when run.
- Slices 1–3 device status: CLOSED on Moto G 2025 physical smoke (see
  closure section). No DEVICE-00X emulator runs claimed or needed.
  Dimming NOT OBSERVED; sensor-less path N/A on hardware.
- Full MGRS blocked (MGRS-001). DEC-001..019 + MGRS-001 live outside
  this tree; in-repo decisions: ADR-001..005, DEC-020..022.
- Open threads: timeout engine-side scope (out — DEC-020 is app-only by
  decision); flutter_map's implicit 168h HTTP cache coexisting with
  engine-indexed packs (flagged for productization, no action).

## Next actions

1. Physical-device smoke for Slice 4A in Android Studio (tap measure,
   GPS/center A, tap-B polyline, units, dismiss) when the operator runs it.
2. Slice 4B forensic/implementation gate (waypoint + journal) when
   authorized — planning only until then, no implementation creep.
2. Follow-policy decision when authorized (camera-center policy).
3. Compass-accuracy investigation ONLY if ever wanted, as its own
   evidence pass — never as drive-by tuning.
4. CARTO provider contract (keys stay out of repo).
