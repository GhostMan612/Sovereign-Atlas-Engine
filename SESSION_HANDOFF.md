# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence docs stay in
> `blueprints/app-track/`; this file points at them, never duplicates them.

## Where we are (2026-09-11)

- **Baseline:** `80ab42f` verified pre-flight; Slice 4D closure
  committed locally (hash in final report), unpushed. NO PUSH performed
  (operator decision).
- **Camera + Layers sprint (local commit, device smoke PENDING):**
  architect-issued order executed end-to-end from `785ec4a` with all 8
  open decisions applied as directed. Host-only adapter
  (`apps/atlas/lib/map/`: `camera_policy.dart` startup/My-Location
  intents validated against `AtlasCameraState`; `layer_stack.dart`
  engine-stack builder + `AtlasAttribution.forVisible` derivation) +
  `main.dart` wiring (one-shot startup cascade fix→last-known→world
  overview at z13; My Location valid-fix + zoom 15 bearing-preserving,
  stale/no-fix no-jump; Go-To zoom preservation unchanged; base+overlay
  picker toggles for graticule/rings/waypoints/track/measure;
  `TileLayer` native ceilings from endpoint descriptors; attribution
  bar follows visible stack). Record: `docs/architecture/DEC-023-*`
  (supersedes DEC-021 ONLY for explicit My Location zoom; DEC-021
  unedited). Zero engine diff (suite-identical 453/413/0/8/32); two
  in-repo path deps justified in DEC-023 (`atlas_map`,
  `atlas_layers`); analyze clean; 240/240 app tests (208 baseline +
  32 sprint). Physical smoke PENDING — no device run here.
- **Slice 4B dependency correction (local commit):** forensic proved the
  `atlas_tactical` path dep was load-bearing nowhere — `fromWaypoint`
  uncalled, `toWaypoint` test-only, no other tactical reference in the
  app. Removed dep + lock entry + both conversion methods; conversion
  test replaced by a dependency-free schema-pin test. Journal schema,
  IDs, provenance, UI, and behavior unchanged. Zero new dependencies
  restored; engine untouched; analyze clean; 152/152 intact. Zero new
  dependencies restored (tactical returns naturally in 4D with its own
  justification). Slice 4B fully CLOSED on Moto G 2025 smoke below.
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
  restore-on-launch. Journal record is field-identical to `AtlasWaypoint`
  (tactical dep added then removed as unnecessary — see correction
  entry; no tactical reference remains). Forensic found no blocker
  (long-press SDK-verified; tap semantics orthogonal).
  Test lessons: real dart:io setup in widget tests needs
  `tester.runAsync`; `MarkerLayer` finders need `skipOffstage: false`
  (two layers now); old `positionMarker` helper widened (test-only).
  Widget-test file IO: never interleave unawaited and awaited persists
  across zones — seed files directly + restore, or quiesce, or FakeAsync
  strands writers (proven by 4C bytes-test diagnosis; production
  unaffected, user-paced mutations).
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
- **Slice 4C — go-to (local commit):** ephemeral `GoToState`
  (`apps/atlas/lib/go_to/go_to_state.dart`: typed target snapshot,
  pure distance/bearing over explicit fix, coincident→null bearing) +
  detail-sheet Go-To button (pops id, no nav logic in page) + camera
  jump via existing `move(target, currentZoom)` + top-left nav card
  (label/coords/distance/bearing-or-unavailable/Clear) reusing the
  waypoint purple marker. Valid-fix-only nav (stale→unavailable); no
  persistence (journal bytes proven unchanged); no rotation/zoom/follow
  change. No engine diff, no new deps. 9 unit + 10 widget tests.
- **Slice 4D — track recording (local commit):** foreground-only,
  event-driven `TrackRecorder` (`apps/atlas/lib/track/track_recorder.dart`:
  idle/recording states, valid-fix-only ingest, identical-notification
  dedup, seed-on-start, stop returns fixes) + journal `StoredTrack`
  codec (`trk-000001`, `gps_recorded`, createdAt-first-at, version 1 with
  optional tracks key) + `TracksPage` list/detail/delete + Start/Stop +
  cyan active polyline + REC badge. Stop-empty persists nothing; crash
  loses active log by design; no rotation/pan/zoom/follow change; no
  go-to/measure/compass interference. Engine untouched (0-line diff);
  `atlas_tactical` path dep restored with justification (load-bearing:
  `AtlasTrack`/`AtlasWaypoint` in save paths). 12 unit + 15 journal +
  13 widget tests. Test lesson: FakeAsync freezes unawaited persists —
  widget file assertions must seed files directly, never interleave
  unawaited and awaited same-file writes across zones.
- **Track status:** Offline Areas + DEC-020 + Slices 1–3 (closed) +
  Slice 4A (closed) + dark charcoal polish + Slice 4B (closed) +
  Slice 4C (closed) + Slice 4D (closed). Remaining: camera/layers
  sprint spec (pending architect review), Slice 4E GPX export, follow
  policy, tactical UI, terrain/LOS UI, CARTO, full MGRS.
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

## Native MapLibre host — Phase 1 + Tracks/Go-To/offline-base (2026-09-20)

- **Commits (local, unpushed — NO PUSH performed):**
  `7b582ab feat(android): add native MapLibre host Phase 1
  (foundation to journal plus Go-To state)` (65 files, 5785 insertions;
  `:app:testDebugUnitTest` 117/117 green) +
  `05df386 feat(android): add TrackRecorder, offline pack base,
  Go-To camera glue` (6 files; `:app:testDebugUnitTest` 139/139 green).
  Flutter oracle untouched (zero Flutter diff both commits).
- **Scope:** `apps/atlas-android/` (`com.sovereignatlas.atlas`,
  MapLibre 13.3.1, Compose BOM 2024.12.01): skeleton + boot screen,
  parity harness (11 byte-identical golden copies), foundation
  (core/geo/overlays/units/camera/layers/models + CompassMath/Mgrs/
  SovereignGrid transplants), camera policy (`max(currentZoom,15)`,
  startup z13, gated rotation), LocationService + Android source
  (LocationManager, no Play), heading, Measure state + panel,
  journal v1 codec + FieldJournal (waypoints + `trk-` tracks),
  layer features/installer, GoToState + `goToToFeature` + Go-To
  camera glue, TrackRecorder (12 tests mirroring Dart), offline
  pack base (record/lifecycle/`countTiles`/formatAge/formatBytes).
- **Parity catch (evidence):** `countTiles` saturation case failed
  natively on first run — 32-bit `Int` span overflow vs Dart 64-bit;
  fixed with `Long` arithmetic, now green. Deferred with reason:
  map-screen UI wiring, pack downloader/rate-limiter/store (engine
  types), engine-plan-backed tile estimates, GPX, follow policy.
- **Hygiene:** nested `apps/atlas-android/.gitignore` (`.gradle/`/
  `.kotlin/`/`build/`/`local.properties` excluded from commits);
  all new `.kt` genesis-header-only; no secrets in tree (grep clean).
  Pre-existing `D analysis_options.yaml` +
  `?? blueprints/dart_analysis-20260915_194625.txt` preserved untouched.
- **Gates here:** native unit 139/139 green; Flutter analyze/test NOT
  re-run in this env (no Flutter SDK present) — justified by zero
  Flutter diff. No build claimed (human builds in Android Studio).

## Native host resume — MapBehavior camera brain (2026-09-21)

- **Commit (local, unpushed):** `e4fc431 feat(android): add
  MapBehavior startup/locate/pending-recenter brain`
  (`map/MapBehavior.kt` + 7 tests; `:app:testDebugUnitTest`
  146/146 green). Zero MapLibre imports in the unit — camera
  application stays in the composable.
- **Semantics (mirrors `main.dart`):** startup once (valid fix →
  z13/bearing-0; null → retry later; user-interacted → never);
  locate via `ensureActive` (valid → `max(currentZoom,15)` +
  bearing passthrough; acquiring-null → pending; denied-null →
  ignored); location updates while pending retry once then clear.
- **Oracle discrepancy preserved:** Dart `myLocationIntent` uses
  fixed z15; native keeps decided `max(currentZoom,15)` semantic.
- **Next:** composable wiring (Activity-scoped services, permission
  launcher, style-load install, journal push, My-Location affordance)
  needs a device/human build for runtime verification — no device
  attached here (`adb devices` empty).

## Native host completion pass (2026-09-21, operator-ordered, no device)

- **Commits (local, unpushed):** `7a36dcd` screen wiring
  (services/layers/journal/position/startup camera) → `ee6531c`
  Locate button → `5ee7e82` tap B-capture, long-press waypoint
  dialog, measure entry + dots → `0e3ccce` waypoints
  list/detail/edit/delete + Go-To activation → `1a17f94` tracks
  list/detail, Start/Stop, REC badge, active polyline → `380762a`
  Go-To nav card + target layer → `74bf6a7` offline packs,
  7-provider mirror, threaded downloader, offline dialog →
  `733b560` compass dial, heading-up, face north, orient token →
  `299b669` base raster picker, overlay toggles, graticule, rings,
  attribution → `a5114e6` GPX export + follow policy.
  Final gate: `:app:testDebugUnitTest` **170/170 green**.
- **Notable:** base map renders real raster tiles (provider
  templates, native zoom ceilings); graticule/rings mirror oracle
  caps (240 lines, step 3); GPX 1.1 + follow (recenter preserving
  zoom/bearing, gesture exits) are NEW — neither exists in the
  Flutter oracle. Serving downloaded packs as map tiles is deferred
  (needs local tile-server/MBTiles integration).
- **Status:** code-complete per the 10-slice list. NO APK built
  since `app-debug.apk` (69.8 MB, 40s build earlier this session);
  NO install/debug performed — awaiting operator go for the single
  build/install/debug round.

## Flutter removal — Kotlin-only tree (2026-09-21, operator-ordered)

- **Commit (local, unpushed):** `2727538 chore: remove Flutter app,
  Dart engine, Dart tests and tooling` (717 files, 24785
  deletions). Zero `.dart` files remain tracked. Condition
  verified first: every user-facing app behavior exists natively
  (plus GPX/follow/radio/geofence which never existed in
  Flutter); engine-only packages with no host surface were
  documented, not ported.
- **Pre-removal parity closed:** download resume + 500 ms
  tile throttle + Diagnostics tab + local-serve hit counter
  (`e1072a9`; 184/184 green, still green after removal).
- **Governance updated:** RULES.md (Gradle gates, module
  boundary, golden path, MapLibre API law), AGENTS.md
  (native-only ramp), `.opencode/commands/verify.md` +
  `smoke.md`. `blueprints/` + `docs/` kept as read-only history.
- **Remaining tracked non-product entries:** `D
  analysis_options.yaml` + `?? blueprints/dart_analysis-
  20260915_194625.txt` (both pre-existing, untouched).

## Feature-gap closure pass (2026-09-21, operator-ordered)

- **Commits (local, unpushed):** `8d93662` (CARTO Positron/Dark
  Matter keyless endpoints in engine registry + native mirror +
  MGRS readout in waypoint detail/Go-To card + radio link tool
  with TAC2-004 vectors + Link dialog) → `d78046c` (radial
  geofence with TAC2-002 vectors + session Fence dialog + fence
  layer). Final gate: `:app:testDebugUnitTest` **182/182 green**.
- **Deliberately left open:** terrain/LOS UI (RADIO-002 reserves
  terrain-aware LOS to a DEM engine BY DESIGN — no elevation
  source exists; building it would invent data); MGRS-001 golden
  stays schema-only (governance: minting vectors forces a library
  choice); polygon fences (vertex-entry + persistence undecided);
  Phase 13 release (never executed).

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
- **Status: Slice 4A CLOSED.** Slice 4B NOT STARTED (at that time).

## Slice 4B closure — Moto G 2025 physical smoke (2026-09-11)

- **Device:** Moto G 2025 physical hardware. Implementation baseline
  `f2e6125` (+ `57ec374` dependency correction: tactical dep removed,
  zero new dependencies, behavior unchanged).
- **Results — 21/21 PASS:** long-press opens creation (PASS); sheet shows
  coordinates (PASS); label entry (PASS); note entry (PASS); save
  (PASS); second waypoint (PASS); management list (PASS); details
  (PASS); edit label (PASS); edit note (PASS); persistence (PASS);
  delete (PASS); deleted disappears (PASS); restart (PASS); undeleted
  survives restart (PASS); deleted does not resurrect (PASS); measure
  intact (PASS); location marker intact (PASS); compass/heading-up
  intact (PASS); selection moves/rotates nothing (PASS); no 0,0
  fallback (PASS).
- **Architectural checks intact:** tactical correction in place; zero new
  dependencies; no engine modifications; host-side persistence;
  deterministic IDs; explicit provenance; no resurrection; restart
  survival; corruption safety covered by automated tests; measure/
  location/heading unaffected; no camera motion from waypoint
  interaction; no fabricated coordinates; no go-to/track/GPX/share/
  geofence/terrain/MGRS/CARTO/background scope leaked.
- **Status: Slice 4B CLOSED** — implementation, automated verification,
  and physical-device verification complete.

## Slice 4C closure — Moto G 2025 physical smoke (2026-09-11)

- **Device:** Moto G 2025 physical hardware. Implementation baseline
  `506137f` (ephemeral `GoToState`, detail-sheet activation, camera
  jump with zoom preserved, nav card, valid-only nav; 0-line engine
  diff; zero new deps; 171/171 + analyze clean).
- **Results — 20/20 PASS** (operator-reported, recorded verbatim scope):
  activation, target indication, camera move to target, zoom unchanged,
  no rotation, live distance, live bearing, nav updates on location
  change, pan independence (no forced recenter), compass intact,
  heading-up intact, clear, presentation removal, waypoint persisted,
  restart survival, post-restart inactivity, measure intact, location
  marker intact, no 0,0, no unintended rotation, no waypoint deletion.
- **Status: Slice 4C CLOSED** — implementation, automated verification,
  and physical-device verification complete. No code changed by this
  closure.
- **Deferred, explicitly NOT implemented:** My Location zoom-to-15
  refinement. Current Slice 1 behavior (center + preserve zoom) stands;
  DEC-021 explicitly rejected a forced recenter zoom, so this needs its
  own dedicated camera/UX polish slice with regression tests (center on
  fix, zoom exactly 15.0, go-to/measure/heading unchanged, no 0,0) —
  never a drive-by edit to a closed slice.

## Slice 4D closure — Moto G 2025 physical smoke (2026-09-11)

- **Device:** Moto G 2025 physical hardware. Implementation baseline
  `80ab42f` (foreground-only event-driven recorder, journal `StoredTrack`
  codec, TracksPage, cyan polyline + REC badge; 0-line engine diff;
  tactical path dep with justification; 208/208 + analyze clean).
- **Results — smoke accepted, no defect demonstrated.** Stationary drift
  observed (expected under the raw-event contract — distinct valid fixes
  append; NOT a defect, and no filtering/smoothing/decimation authorized
  on that observation alone). Resume transient observed: count advanced
  by exactly 2 with no duration-proportional background accumulation.
- **+2 characterization (tightened per review — non-defect, provenance
  unverified):** the +2 is contract-compliant, and the source contains a
  credible structural explanation for exactly two events — one
  `LocationListener` receives callbacks from two registered providers
  (GPS + NETWORK, `LocationChannel.kt:150-166`), with no filtering, no
  cross-provider dedup, no resume hook, no reseeding, and no UI counter
  anywhere in the path. However, the exact runtime provenance of those
  two points (e.g. one-per-provider vs. other two-event combinations)
  is NOT observable from repository source and is therefore recorded as
  UNVERIFIED, not as a proven mechanism. Words like "proves" are
  avoided: the absence of duration-proportional growth STRONGLY
  INDICATES (does not prove) that no events flow while backgrounded.
- **Explicitly rejected:** any "discard N points after resume" workaround
  (would invent behavior to erase an unexplained observation and could
  silently drop legitimate fixes).
- **Optional future instrumentation (NOT a blocker, NOT authorized):**
  if exact provenance is ever wanted, capture each point's Android
  `fix.at` and provider immediately before backgrounding and immediately
  after resuming.
- **Status: Slice 4D CLOSED** — implementation, automated verification,
  and physical-device verification complete. No code changed by this
  closure.

## Standing environment facts

- **No builds here unless explicitly asked** (RULES.md §1.6). Live gate:
  `:app:testDebugUnitTest` (172/172 green). `androidTest/` runs only on
  explicit ask (needs a device). Human builds + smoke-tests in Android
  Studio; debug from pasted output.
- Toolchain: Android SDK `C:\android` / JDK 21 via Gradle / AGP 8.13.2 /
  Kotlin 2.1.0 / MapLibre 13.3.1.
- Never pipe `adb pull`/binaries through PowerShell pipes; never edit
  sources via PS text pipelines (RULES.md §3).
- `adb kill-server` fixes most wedges. No device attached here.
- Physical device history: Moto G 2025 — structured Flutter-era slice
  smokes recorded below (lineage context only; the Flutter host is
  deleted).

## Verification posture

- **Live:** native `:app:testDebugUnitTest` 184/184 green. Everything
  below in this section is Flutter-era history (retired host).
- Host: 240/240 app tests (208 Slice-4D baseline + 10 camera-policy
  unit + 8 layer-stack unit + 14 camera/layers widget, fake/temp-dir
  sourced) + engine 453/413/0/8/32 untouched + analyze clean. Engine
  source untouched (zero `packages/` diff for the camera/layers
  sprint). Two in-repo path deps added with DEC-023 justification
  (`atlas_map`, `atlas_layers`); no hosted/platform dependency in any
  slice.
- Device (emulator, prior sessions): picker, acquisition, render
  (4 tiles / 8 serves / 56 attempts), blocked-transport failure,
  forced-timeout mapping — all green when run.
- Slices 1–3 device status: CLOSED on Moto G 2025 physical smoke (see
  closure section). No DEVICE-00X emulator runs claimed or needed.
  Dimming NOT OBSERVED; sensor-less path N/A on hardware.
- Slice 4A device status: CLOSED on Moto G 2025 physical smoke (GPS
  path; fallback automated-only). Post-smoke dark-UI polish:
  automated-gates only, user device smoke PENDING.
- Slice 4B device status: CLOSED on Moto G 2025 physical smoke —
  21/21 PASS (creation, editing, persistence, restart survival,
  no-resurrection, measure/location/compass intact, camera immobile,
  no 0,0). See closure section.
- Slice 4C device status: CLOSED on Moto G 2025 physical smoke —
  20/20 PASS (activation, camera jump with zoom preserved, live nav,
  pan independence, clear, restart inactivity, coexistence, no 0,0,
  no rotation, no deletion). See closure section.
- Slice 4D device status: CLOSED on Moto G 2025 physical smoke —
  drift accepted as contract-expected; resume +2 recorded as known
  physical observation / non-defect with unverified exact provenance
  (see closure section). No workaround authorized.
- Full MGRS blocked (MGRS-001). DEC-001..019 + MGRS-001 live outside
  this tree; in-repo decisions: ADR-001..005, DEC-020..022.
- Open threads: timeout engine-side scope (out — DEC-020 is app-only by
  decision); flutter_map's implicit 168h HTTP cache coexisting with
  engine-indexed packs (flagged for productization, no action).

## Next actions

1. Single build/install/debug round on operator go (code-complete,
   184/184 green, no APK since the 69.8 MB probe build).
2. Phase 13 productization/release — never executed.
3. Terrain-aware LOS + DEM source decision (RADIO-002 reserves it;
   no invented elevation).
4. Compass-accuracy investigation ONLY if ever wanted, as its own
   evidence pass — never as drive-by tuning.
5. No push unless told (35 local commits ahead of origin).
