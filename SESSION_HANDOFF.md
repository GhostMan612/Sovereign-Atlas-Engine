# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence docs stay in
> `blueprints/app-track/`; this file points at them, never duplicates them.

## Where we are (2026-09-11)

- **Baseline:** `c6b4a88` verified pre-flight; Slice 4B implemented
  locally (commit hash in final report), unpushed. NO PUSH performed
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
- **Slice 4B — waypoints + journal (local commit):** host-only
  `FieldJournal` (`apps/atlas/lib/field/field_journal.dart`: typed
  `StoredWaypoint` codec, monotonic `wp-000001` IDs restored from max
  suffix, `map_selected`-only provenance, versioned `{version:1,
  waypoints[]}` JSON, flushed direct write mirroring OfflineRepository,
  safe restore with per-record skip + dup-first-wins, no silent
  overwrite) + long-press creation sheet + purple place markers +
  `WaypointsPage` list/detail/edit/delete (selection never moves map) +
  restore-on-launch. `AtlasWaypoint` consumed unmodified. Forensic found
  no blocker (long-press SDK-verified; tap semantics orthogonal).
  Test lessons: real dart:io setup in widget tests needs
  `tester.runAsync`; `MarkerLayer` finders need `skipOffstage: false`
  (two layers now); old `positionMarker` helper widened (test-only).
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
  Slice 4A (closed) + dark charcoal polish + Slice 4B (IMPLEMENTED,
  automated-verified, DEVICE SMOKE PENDING). Remaining: Slice 4C go-to,
  4D track, 4E GPX export, follow policy, tactical UI, terrain/LOS UI,
  CARTO, full MGRS.
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

## Slice 4A closure — Moto G 2025 physical smoke (2026-09-11)

- **Device:** Moto G 2025 physical hardware. Implementation baseline
  `9d65666` (host-only `MeasureState` + persistent sheet + `onTap` B +
  amber polyline + GPS/map-center A + units/DMS; 0-line `packages/`
  diff; 128/128 tests).
- **Results:** measure opens (PASS); live fix 44.9478, -93.111, 3.2 m
  (PASS); B tap + coordinates (PASS); distance (PASS); bearing (PASS);
  units (PASS); DMS (PASS); A/B markers + line (PASS); retap B (PASS);
  close/clear (PASS); clean re-entry (PASS); no map move/rotate (PASS);
  compass intact (PASS); no 0,0 jump (PASS); no stale/no-fix anomaly
  (PASS).
- **Source-path qualification:** GPS source path physically verified;
  map-center fallback covered by automated tests and NOT physically
  exercised (valid fix was available). Fallback MUST NOT be claimed as
  device-proven.
- **A/B graphics size:** slightly large markers noted; NOT a defect,
  no implementation reopened on that observation alone.
- **Post-smoke polish (this operation, device verification PENDING):**
  dark charcoal host presentation (`ColorScheme.dark surface 0xFF262626`;
  semantic marker/compass/map colors preserved; DMS secondary text made
  theme-aware) + `debugShowCheckedModeBanner: false`. Host-only
  (`apps/atlas/lib/main.dart`, +11/−2); no engine diff; no dep changes;
  analyze clean; 128/128 intact. The polish has NOT been physically
  smoked — automated verification only.
- **Status: Slice 4A CLOSED.** Slice 4B NOT STARTED.

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

- Host: 152/152 app tests (128 Slice-4A baseline + 16 field-journal
  unit + 8 waypoint widget, fake/temp-dir sourced) + engine 453/413/0/8/32
  untouched + analyze clean. Engine source untouched (zero `packages/`
  diff for Slice 4B).
- Device (emulator, prior sessions): picker, acquisition, render
  (4 tiles / 8 serves / 56 attempts), blocked-transport failure,
  forced-timeout mapping — all green when run.
- Slices 1–3 device status: CLOSED on Moto G 2025 physical smoke (see
  closure section). No DEVICE-00X emulator runs claimed or needed.
  Dimming NOT OBSERVED; sensor-less path N/A on hardware.
- Slice 4A device status: CLOSED on Moto G 2025 physical smoke (GPS
  path; fallback automated-only). Post-smoke dark-UI polish:
  automated-gates only, user device smoke PENDING.
- Slice 4B device status: NOT SMOKED — implementation verified by
  automated gates only (152/152 + analyze). Manual physical smoke
  required: long-press create, save/cancel, list/detail/edit/delete,
  restart persistence, measure coexistence, no 0,0, camera immobility.
- Full MGRS blocked (MGRS-001). DEC-001..019 + MGRS-001 live outside
  this tree; in-repo decisions: ADR-001..005, DEC-020..022.
- Open threads: timeout engine-side scope (out — DEC-020 is app-only by
  decision); flutter_map's implicit 168h HTTP cache coexisting with
  engine-indexed packs (flagged for productization, no action).

## Next actions

1. User device smoke for Slice 4B + dark-charcoal polish in Android
   Studio (functional 4B unproven on hardware; polish visual-only).
2. Slice 4C forensic/implementation gate (go-to) when authorized —
   planning only until then, no implementation creep.
2. Follow-policy decision when authorized (camera-center policy).
3. Compass-accuracy investigation ONLY if ever wanted, as its own
   evidence pass — never as drive-by tuning.
4. CARTO provider contract (keys stay out of repo).
