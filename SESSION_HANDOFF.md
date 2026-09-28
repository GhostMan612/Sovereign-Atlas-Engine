# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence docs stay in
> `blueprints/app-track/`; this file points at them, never duplicates them.

## Routing sprint CLOSED — audit only, no code (2026-09-27)

- **No code changed.** The "Offline Routing Engine (pipeline +
  domain)" sprint was audited and closed because its
  prescribed type names collide with shipping code and its
  premise (an unwired pipeline awaiting a stub) is false.
- **Blocking collisions (all verified with file:line):**
  `class RoutingEngine` already exists at
  `geo/routing/RoutingEngine.kt:19` in the same package the
  prompt asked for an `interface RoutingEngine` — a
  redeclaration, i.e. a compile error; `Route` would
  duplicate the richer existing `RoutingResult`
  (`geo/routing/RoutingModels.kt:24`, carries path, hours,
  distance, gain, descent); a new `tactical-route-layer`
  would double-render the existing `atlas-tactical-route` +
  `atlas-tactical-route-layer`
  (`map/AtlasLayerInstaller.kt:45-46`); the anchor is
  `atlas-waypoints-layer`
  (`map/AtlasLayerInstaller.kt:22`), created inside
  `installAtlasLayers` and called from `onStyleLoaded`
  (`map/AtlasMap.kt:363`) — this repo has NO
  `addOnStyleLoadedListener` at all; and
  `com.sovereignatlas.atlas.core.GeoPoint` does not exist
  (it is `geo.GeoPoint`, `geo/LineOfSight.kt:12`, 6-arg,
  non-null lat/lon).
- **Task 0.3 dependency answer:** GraphHopper is **ABSENT**
  from `apps/atlas-android/app/build.gradle` — grep for
  `graphhopper` returns zero hits, so there is no version to
  audit and nothing to remove. No dependency was added (per
  sprint instruction and RULES 1.3 / 2.4). If GraphHopper is
  wanted later it is a packaging decision (custom JAR, dex
  limits, ProGuard, offline licensing), not a code sprint.
- **Why the premise was false:** routing is already
  end-to-end. A terrain-aware graph A* engine exists
  (`LoadProfile`, `TrailType`, ascent/descent penalties),
  is exposed as `RoutingState.result`, is pushed to the glass
  by `pushRouteResult` (`map/AtlasMap.kt:1761`) via
  `refreshFootRoute` (`:1740`), and is covered by 11 passing
  tests (`geo/routing/RoutingEngineTest` 11/11 green). A
  Haversine stub would have tested a working pipeline while
  colliding with its type names.
- **Operator decision:** close with this audit record; a
  genuine interface extraction (fold `RoutingResult` and a
  future `Route` into one model, make the graph engine
  implement an interface) is deferred as its own ADR-backed
  refactor, since it would rewrite load-bearing code behind
  11 green tests.
- **Device verification unchanged:** route rendering from the
  existing `atlas-tactical-route` layer still needs a real
  routing graph loaded before it can be proven on device.

## HUD mutual exclusivity — drawing vs measurement (2026-09-27)

- **Commit (pushed):** `fix(android): enforce drawing and
  measurement HUD exclusivity` (1 file). Toolbar is gated on
  `!measureActive.value`; the MeasurePanel block is gated on
  `measureActive.value && drawingMode == DrawingMode.NONE`.
  Choosing a drawing mode calls `services.measure.clear()`;
  the Measure FAB resets `drawingModeFlow` to NONE and drops
  `inProgressPointsFlow`. Both directions verified against the
  real accessors, not placeholders.
- **Audit (Task 0 pinned before writing):** measure state is
  the Compose holder `measureActive: MutableState<Boolean>`
  (AtlasMap.kt:285), refreshed by a listener that reads
  `services.measure.isActive()`; the measurement UI is a
  PLAIN composable `MeasurePanel(snapshot, units,
  onUnitSelected, onClose, onClear, modifier)` rendered
  inline inside a BottomCenter `Surface` (not a sheet or
  dialog), so the gate was applied at the state-gated call
  site and its `onClose`/`onClear` semantics (both
  `services.measure.clear()`) are untouched. Note two
  `onMeasure` lambdas exist: the listener refresher (:578)
  and the FAB activator (:749) — the exclusivity write went
  into the activator, since only it changes mode.
- **Store is authoritative:** `MeasureState.clear()` calls
  `notifyListeners()`, so writing the store (not the Compose
  boolean) keeps `measureActive` truthful — the listener
  refreshes it, and the panel clears on the next frame.
- **Deadlock analysis:** gating BOTH surfaces the other way
  would strand the operator with neither visible, so the two
  state writes are the invariant that prevents it (any
  activation of one clears the other). Recorded, not assumed.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **322/322 green each**. No new test —
  Compose UI state exclusivity only (per sprint note).
- **Device verification PENDING (human/Studio):** activate
  drawing → measure panel disappears; activate measure →
  toolbar disappears AND the draft is discarded; then confirm
  the bottom band no longer collides.

## Tactical drawing UI + click interception (2026-09-27)

- **Commit (pushed):** `feat(android): tactical drawing UI and
  click interception` (2 new + 1 modified + handoff). New pure
  `geo/graphics/DrawingMode`; new
  `ui/hud/TacticalDrawingToolbar` (LINE / MEDEVAC / RESTRICT +
  Pts + UNDO + COMMIT, Terminal Green accent, explicit
  imports per repo style); toolbar mounted BottomCenter in
  the map Box; `displayGraphics` = committed + transient
  preview drives the ops source; commit appends a real
  `OperationalGraphic` to the committed list.
- **CRITICAL correction to the prompt's premise:** this repo
  has NO adapter class and NO `addOnStyleLoadedListener`.
  The map click listener is registered exactly ONCE inside
  `getMapAsync` (AtlasMap.kt:435) and already carries three
  behaviors (LoS observer/target, measure point-B, waypoint
  hit-selection) with an explicit in-code warning that a
  second registration would REPLACE it. Registering a
  separate drawing listener — as the prompt's snippet would
  have — would have destroyed waypoint selection. The drawing
  intercept is therefore merged at the TOP of the existing
  single registration and returns early; all existing tap
  behavior is byte-identical below it.
- **Staleness fix carried over:** drawing state lives in
  `remember { MutableStateFlow(...) }` created BEFORE the
  `remember`ed MapView, so the long-lived listener closure
  reads current values (same reasoning as the mbtiles
  `activeMbtilesPacks` holder fix). No `mapAdapter.latestGraphics`
  field exists because `onStyleLoaded` now re-reads
  `displayGraphics` directly.
- **Preview is never cached:** `displayGraphics` is derived in
  a `remember`; only COMMIT mutates `opsGraphics`. Style
  reloads push `displayGraphics`, so an in-progress shape
  also survives a reload without corrupting the committed
  cache.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **322/322 green each** (no new pure-logic
  surface this sprint; DrawingMode is a 4-value enum).
- **Device verification PENDING (human/Studio):** tap-to-
  append, undo, commit persistence, live preview color per
  mode, waypoint selection intact while idle, pan/zoom/
  double-tap while a mode is active, and toolbar overlap
  against the BottomCenter MeasurePanel (both use that
  band — not guessed without a device).

## Operational graphics domain + layers (2026-09-27)

- **Commit (pushed):** `feat(android): operational graphics
  domain and layers` (2 new + 2 modified + handoff). New pure
  `geo/graphics` (`ZoneType` MEDEVAC/RESTRICTED/OBJECTIVE with
  hex+alpha, sealed `OperationalGraphic.TacticalLine` /
  `.TacticalZone`) over the existing `geo.AtlasCoordinate`.
  New `map/graphics/GraphicsGeoJsonMapper` (degenerate
  geometry dropped, rings closed by value comparison).
  `ops-graphics-source` + `ops-zone-layer` (FillLayer) +
  `ops-line-layer` (LineLayer) installed in
  `installAtlasLayers`, both `addLayerBelow(MESH_TRACK_LAYER)`
  with `geometryType()` filters, fully data-driven.
  `pushOpsGraphics` wired into `onStyleLoaded` and a
  `LaunchedEffect` over an empty placeholder list.
- **Audit substitutions (no halt needed):** coordinate class
  is `com.sovereignatlas.atlas.geo.AtlasCoordinate` (non-null
  `latitude`/`longitude` Double — the same type the existing
  measure/position/ring mappers already use; `geo.GeoPoint` is
  the richer GPS type and was deliberately not used here). No
  new coordinate class invented, no null guards needed.
  Anchor is `mesh-track-layer` (the CoT track layer added
  last sprint), so drawings render below track icons and
  their callsign labels.
- **MapLibre API verified by javap BEFORE writing** (13.3.1
  AAR): `Expression.geometryType()`, `Expression.literal
  (String)`, and **`Expression.eq(Expression, Expression)` all
  exist** — so the preferred Expression/Expression form was
  used, not the String overload and never `.toString()` on an
  Expression. All four data-driven `PropertyFactory`
  overloads (`lineWidth`, `lineColor`, `fillColor`,
  `fillOpacity` taking Expression) plus `lineJoin`/`lineCap`
  exist, so no uniform-color fallback was needed.
  `addLayerBelow` with a not-yet-created anchor would fail, so
  the ops layers are added AFTER the mesh-track block in the
  same function.
- **Repo pattern used instead of the prompt's adapter class:**
  no `mapAdapter` exists; state is a composable-owned
  `mutableStateOf` holder read via `.value` at style-load time
  (the mbtiles staleness fix pattern), so no `latestGraphics`
  cache field is needed — style reloads re-read current state.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL
  (no placeholder identifiers remain; grep clean); Play +
  Enterprise **322/322 green each** (new
  `GraphicsGeoJsonMapperTest` 8/8: line properties, zone
  closing, no double-close, degenerate drop, per-zone
  palette, defaults, lon/lat, ring start); `geo/graphics` arch
  grep clean.
- **Not yet shippable as a user feature:** the graphic list is
  an empty placeholder until the drawing-tools UI sprint
  authors points. Device verification PENDING for the
  drawing UI itself.

## PLI blue-force vector hotfix (2026-09-27)

- **Commit (pushed):** `fix(android): load blue-force PLI marker
  as a Drawable` (1 file, +10/-4). `ensurePliMarker` now uses
  `ContextCompat.getDrawable(context, R.drawable.
  ic_blue_force_marker)` + `Style.addImage("blue-force-
  marker", drawable)` (Path A) with an explicit
  `Log.e("AtlasMap", ...)` on load failure, replacing the
  silent `BitmapFactory.decodeResource(...)?: return`. Image
  ID unchanged (`"blue-force-marker"`, consumed by
  `iconImage(...)` in the PLI SymbolLayer). Call site
  unchanged (`onStyleLoaded`), so the icon re-registers on
  every style reload.
- **Path A confirmed by javap** on the 13.3.1 AAR:
  `addImage(String, Drawable)` exists, so no manual
  rasterization was needed. `ensureGpsPuck` deliberately
  left on `BitmapFactory` — `ic_gps_puck_sdf` is a real PNG
  in `drawable-nodpi`, so that path is correct.
- **No new test** — this is a runtime asset-loading fix
  relying on minSdk >= 21, verified manually on emulator.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **314/314 green each**. The single
  intermediate failure was the already-recorded
  `AndroidKeyProviderTest.keySurvivesNewInstance` 10 s
  `TimeoutCancellationException` (second observed
  occurrence, Tink/IO timing, unrelated to this change):
  it passed on isolated re-run and on the full re-run.
  Still an unresolved flake, not proven stable.
- **Device verification PENDING (human/Studio):** blue-force
  PLI markers now actually appearing on the glass for remote
  peers — this is the confirming evidence for the fix.

## CoT mesh-track glass layer (2026-09-27)

- **Commit (pushed):** `feat(android): CoT mesh-track glass
  layer` (4 drawables + 2 files + handoff). Operator supplied
  the four MIL-STD-2525-inspired vectors (ic_friendly cyan
  rectangle, ic_hostile red diamond, ic_neutral green
  square, ic_unknown yellow circle) after the halt, so Task
  3.2 was unblocked. `mesh-track-source` +
  `mesh-track-layer` (SymbolLayer) added in
  `installAtlasLayers` via `addLayerAbove(WAYPOINTS_LAYER)`
  so tracks sit above the static stack; data-driven
  `iconImage(Expression.get("affiliation"))` + `{callsign}`
  labels with halo; `ensureCotTrackIcons` + `pushMeshTracks`
  wired into `onStyleLoaded` and the existing
  `markerStream` collector (survives style reloads).
- **VECTOR BUG FOUND (would have broken the icons):** the
  prompt's `BitmapFactory.decodeResource` cannot decode a
  VectorDrawable — it returns null, and the prescribed
  `?: return`-style flow would have silently shipped with no
  icons. Used the verified `Style.addImage(String, Drawable)`
  overload with `ContextCompat.getDrawable(context, resId)`
  instead. javap-verified before use (also confirmed
  `addLayerAbove` and the PropertyFactory overloads).
- **PRE-EXISTING BUG REPORTED, NOT FIXED:** the sibling
  `ensurePliMarker` has the same defect — it calls
  `BitmapFactory.decodeResource` on
  `ic_blue_force_marker.xml`, which is a **vector**, so the
  PLI icon is most likely never added and blue-force PLI
  markers may not render on device today. Deliberately left
  untouched (unrequested behavior change); the fix is the
  same `addImage(String, Drawable)` path. This is the top
  device question for the next smoke.
- **Deliberate non-duplication:** `cot-marker-layer` (the
  data-driven CircleLayer from the tactical targeting sprint)
  is intentionally KEPT alongside; the new layer adds icon
  art plus visible callsign labels. Merging them was
  explicitly rejected by the operator.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **314/314 green each** (mapper 5/5 from
  the prior commit). MapLibre confined to `map/`; mapper is
  pure and JVM-tested.
- **Device verification PENDING (human/Studio):** all four
  icon shapes + colors, callsign labels, tracks above the
  static stack, and the PLI icon question above.

## CoT mapper (HALTED before icon injection) (2026-09-27)

- **Commit (pushed):** `feat(android): CoT geojson mapper`
  (2 new files + handoff). `map/cot/CotGeoJsonMapper` with
  `deriveAffiliation` (a-f/a-h/a-n else unknown) and
  `toFeatureCollection` (lon=X, lat=Y; uid/callsign/
  affiliation properties). No map/AtlasMap change.
- **HALTED per the prompt's own Task 3.2 gate:** none of the
  four tactical drawables exist. Full `res/drawable`
  inventory = `ic_splash_compass`,
  `ic_launcher_monochrome`, `ic_launcher_foreground`,
  `ic_launcher_background`, `ic_compass_overlay`,
  `ic_blue_force_marker`, plus `drawable-nodpi/
  ic_gps_puck_sdf.png`. The prompt forbids Android
  system-drawable fallbacks, so the SymbolLayer icon
  injection was NOT shipped and no icons were invented.
  Awaiting an operator asset decision.
- **Audit notes for whoever finishes it:** mesh flow is
  `services.markers.markerStream: StateFlow<Map<String,
  CotMarker>>` (a MAP, not `List<CotMarker>` — convert with
  `.values.toList()`); the composable already holds
  `context` (LocalContext) so no adapter ctor change is
  needed; existing markers are ALREADY rendered by
  `cot-marker-layer` (data-driven CircleLayer, colors via
  `markerColorHex`, callsign carried as a property but not
  drawn), so an icon SymbolLayer is additive — its real
  delta would be icon art plus visible callsign labels.
  `installAtlasLayers` currently `addLayerBelow(
  WAYPOINTS_LAYER)`; "on top" would be `addLayerAbove`
  (verify against the AAR before use).
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **314/314 green each** (new
  `CotGeoJsonMapperTest` 5/5: affiliation, unknown
  fallback, lon/lat ordering, properties, empty).

## TacNav-X compass HUD (2026-09-27)

- **Commit (pushed):** `feat(android): TacNav-X compass HUD`
  (4 files + handoff). Pure `geo/TacticalMath`
  (degreesToMils, formatBearing); new `ui/hud/TacNavHud`
  Canvas (open-center reticle + top bearing + mils, no
  pointer input); mounted in the map Box directly after
  `AndroidView` (above map, below FAB rail).
- **Audit:** accent confirmed `#39FF14` (Terminal Green, used
  literally across `ui/` — not MaterialTheme). Bearing has NO
  flow: `services.heading.displayDeg(): Double?` is
  listener-driven, so the HUD follows the existing
  `headingTick` key (`remember(headingTick.value)`), same
  source as the compass dial (TRUE-preferred, MAG fallback).
- **Deviations (reported, not silent):** bearing is
  `Float?` — the ordered `mutableFloatStateOf(0f)` fallback
  would have rendered a permanent fake "000 / 0 mils"
  (no heading service in the DI graph as a flow), which
  violates unknown>invented; instead the reticle always draws
  and the readout appears only on a real sample. Added
  optional `frameLabel` (TRUE/MAG provenance) and
  `ensureStarted()` on mount (the sensor previously started
  only when head-up was toggled, so the readout would have
  been dead on launch).
- **DEFECT FOUND IN ORDERED SPEC + FIXED:** the spec's
  `degreesToMils` returns **6400** for inputs just under 360
  (e.g. 359.99 -> 6399.82 -> 6400), which is outside the NATO
  circle. Clamped to wrap to 0; all specified vectors
  (0/1600/3200/4800, 360->0, 359.9->6398) still hold.
- **Visual change:** the old `TacticalCrosshair` "+" call site
  was removed (same center, would double-draw); the
  composable itself is untouched in `ui/TacticalHud.kt` and is
  one line to restore. **Device risk to check:** the top
  bearing readout (32 dp) sits in the same top-center band as
  the MGRS/LoS strip (16 dp) — expect overlap; offset was NOT
  guessed without a device.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **309/309 green each** (new
  `TacticalMathTest` 7/7 incl. the range check that caught the
  6400 bug); `geo/` arch grep clean; `TacNavHud.kt` grep for
  `clickable|pointerInput` = zero hits (touch pass-through by
  construction).
- **Device verification PENDING (human/Studio):** reticle +
  live bearing on device, TRUE/MAG label, pan/zoom through the
  reticle, top-center overlap appearance.

## Encyclopedia layer manager (2026-09-27)

- **Commit (pushed):** `feat(android): encyclopedia layer
  manager` (9 files + handoff). New pure
  `offline/mbtiles/` (`MbtilesPack`, `MetadataReader`,
  `MbtilesScanner`, `DefaultMbtilesScanner` + pure
  `mbtilesSafeId`/`mbtilesSourceId`/`mbtilesLayerId`);
  new `android.map.mbtiles.AndroidMetadataReader` (read-only
  SQLite, closed in `finally`); `AppServices` owns the
  scanner, `AtlasServices` exposes `mbtilesScanner`.
  `SettingsRepository.activeMbtilesPacks` StateFlow persists
  the selection (`toSet()` on read AND write). The FAB is now
  a Layer Manager sheet: scan on open, per-pack Switch
  (name + description), dismiss preserves selection. Style
  sync mounts/unmounts per-pack raster layers by diffing
  `style.layers` against the active set.
- **Audit (no halt needed):** anchor is
  `atlas-waypoints-layer` (written as a verified constant,
  no placeholders); `historicalLayerEnabled` did NOT exist —
  the real prior-sprint name was `showMbtilesTest` (replaced,
  zero refs remain); `tileUrl` was ALREADY a function
  (`fun tileUrl(packId: String): String?`, kept nullable —
  null when the server is not bound, so no layer mounts);
  SettingsRepository field is `settingsRepository`; `offline/`
  is a source dir inside `:app` (coroutines available);
  `packsDir` is a `() -> File` lambda so it is passed as-is.
  `Style.getLayers()`/`Layer.getId()` javap-verified.
- **Self-caught defects (fixed before commit):** delegated
  `by` value would have been captured STALE by the
  `remember`ed MapView closure (packs toggled after first
  composition would not reinstall on style reload) — now a
  `mutableStateOf` holder read via `.value`, matching the
  file's existing idiom; two of my own new tests asserted
  wrong expectations (safeId also normalizes `.`; a valid
  pack *is* read) — tests corrected, implementation unchanged
  per RULES 4.5.
- **Known limits (not invented away):** scanner regex hides
  packs with spaces/odd chars from the manager (they cannot
  be requested safely over the URL path — rename them);
  `AndroidKeyProviderTest.keySurvivesNewInstance` failed
  ONCE with a 10 s timeout under parallel-flavor load, then
  passed isolated and on two full re-runs — recorded as
  observed flake, not proven stable, not "fixed".
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **302/302 green each** (new
  `MbtilesScannerTest` 10/10: mapping, fallbacks, regex
  exclusion, ordering, missing dir, cancellation guard,
  charset + id helpers); `offline/` arch grep clean.
- **Device verification PENDING (human/Studio):** multi-pack
  mount/unmount, tiles rendering from a real pack, selection
  surviving restart, sheet empty-state.

## MapLibre glass integration (2026-09-27)

- **Commit (pushed):** `feat(android): MBTiles test layer
  toggle on map glass` (5 files + handoff). Loopback
  cleartext extended to `localhost` (127.0.0.1 already
  present; manifest ref pre-existing). Right-rail MBTiles
  FAB toggles `mbtiles-test-source/layer`
  (`RasterSource(TileSet("2.2.0", url))` below
  `atlas-waypoints-layer`); URL comes live from
  `tiles.tileUrl("test.mbtiles")`; split lifecycle =
  `onStyleLoaded` reinstall + toggle `LaunchedEffect`
  (add/remove with null-guards).
- **Audit substitutions (both defaults differed, gate
  satisfied):** anchor is `atlas-waypoints-layer` (not
  `waypoints-layer`); no `listeningPort`/`pack/` — port
  via `tiles.port()`, route via `tiles.tileUrl(packId)`;
  no adapter class — composable-owned state (established
  pattern); style hook is central `onStyleLoaded`, not a
  style-loaded listener. `TileSet(String, String...)` +
  `RasterSource(String, TileSet)` verified via javap on
  the 13.3.1 AAR. One caught-then-fixed compile: toggle
  state must be declared above `onStyleLoaded`.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **292/292 green each** (new
  `tileUrlPinsTestMbtilesRoute`); MapLibre confined to
  `map/` + `ui/`.
- **Device verification PENDING (human/Studio):** needs a
  real `test.mbtiles` in the packs dir — toggle ON mounts
  and tiles render (else silent 404s); toggle OFF unmounts
  source + layer; `dumpsys` net-config check on failure.

## Offline MBTiles tile server (2026-09-27)

- **Commit (local, unpushed):** `feat(android): serve
  MBTiles packs from PackTileServer` (5 files + handoff).
  New pure `offline/MbtilesTileSource` iface (+ `xyzToTmsY`)
  and `android.map.mbtiles.MbtilesCache` (SQLite, canonical
  traversal guard, double-checked handles, metadata MIME,
  TMS flip, shutdown). Server intercepts `.mbtiles`
  packIds (strict `.png` + digit + `0..28` validation),
  200 + `baseHits` on hit, 404 on miss/parse/store-null;
  quota via the existing head-check (BASEMAP + global →
  404, same status as /dem/ exhaustion); `stop()` shuts
  the store down. `AppServices` attaches the cache over
  the packs dir.
- **Lawful adaptation (recorded):** prompt placed SQLite
  inside `offline/` + assumed NanoHTTPD — actual stack is
  raw ServerSocket in pure-logic `offline/`, so the cache
  lives in `android/` behind the iface (mirrors the
  `DemTileStore` precedent); new `android.map.mbtiles`
  package per directive, no separate ADR. One caught-then-
  fixed test: server passes XYZ through, flip lives in the
  cache — extracted pure `xyzToTmsY`, covered directly.
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL;
  Play + Enterprise **291/291 green each** (7 new fake-
  store tests); `offline/` arch grep clean (zero
  android/androidx/maplibre imports).
- **Device verification PENDING (human/Studio):** real
  `.mbtiles` pack render via `tileUrl`, MIME per format,
  miss/empty-pack 404s.

## FGS field readiness audit (2026-09-27)

- **Commit (local, unpushed):** `fix(android): gate FGS cascade
  on location grant` (MainActivity + handoff). Audit found
  Manifest fully compliant (location|connectedDevice,
  stopWithTask=false, exported=false, all 5 permissions) and
  service healthy (ServiceCompat bitwise start, tactical
  icon, DI names current, STICKY + alarm restart, live ally
  count) — zero repairs there. One real wart repaired:
  FGS started before location was granted (API 34+
  throws on location-type start → silent stopSelf, mesh
  dead until restart). Cascade is now location →
  notifications → battery → FGS; grant continuation resumes
  via the existing permission-result path; denial blocks FGS
  (location-type cannot legally run).
- **Gates here:** `:app:assemblePlayDebug` BUILD SUCCESSFUL
  (explicitly ordered; only pre-existing deprecation
  warning); Play + Enterprise **284/284 green each**.
- **Device verification PENDING (human/Studio):** fresh-
  install order (location dialog → notification → battery →
  service), denial path, task-swipe restart.

## Tech debt & engine consolidation (2026-09-27)

- **Commit (pushed):** `refactor(android): batched LoS sampling
  plus marker TTL pruning` (8 files + handoff).
  `ElevationProvider` is now batch-only (`getElevations`,
  singular dropped — its only consumers were the los engine,
  the DI anon object, and the los test fake, all updated);
  `geo.los` engine pre-computes 101 coords, single batch
  fetch, size-mismatch guard, endpoint altitude fallbacks
  preserved; DI maps to `DemSession.engine?.
  getElevationsBatch` (null engine → all-null → existing
  endpoint/path errors). `MarkerStore(scope)` gains a
  60 s prune ticker (24 h TTL) + `shutdown()`; `AppServices`
  owns `markerScope`, listener takes required `markerStore`;
  tests use `TestScope()` + new ticker-prune and
  batch-mismatch tests.
- **Task 1 SKIPPED with cause:** `geo/LineOfSightEngine.kt`
  is load-bearing, not deprecated — `runLosCalculation`
  (LoS tool observer/target flow, ray + block layers, HUD)
  and 4 `LineOfSightTest` cases depend on it. Deleting the
  file alone would break compile and gut the LoS tool.
  Consolidation needs a follow-up directive covering the
  tool flow (rewire tool to `geo.los` or remove tool).
- **Gates here:** Play + Enterprise **284/284 green each**
  (los 7/7, markers 4/4); `geo/los` arch grep clean.
  No assemble per build boundary.

## LoS analysis in targeting sheet (2026-09-27)

- **Commit (pushed):** `feat(android): line-of-sight analysis in
  targeting sheet` (2 modified + 4 new). New `geo/los/`
  package (per architect directive, no separate ADR):
  `TerrainProfile`/`ProfilePoint`/`ElevationProvider` +
  `LineOfSightEngine.calculateProfile` (100-sample raycast,
  4/3 refraction, great-circle interpolation, sequential DEM
  sampling per accepted risk). New `ui/los/
  TerrainProfileChart` Canvas component (green/red terrain +
  dashed sightline, CLEAR/BLOCKED banner). `AtlasServices.
  lineOfSightEngine` wired to `DemSession.engine`; targeting
  sheet gains Analyze action above the CoT buttons, profile
  cleared on each new long-press, GPS-gated with waiting text.
- **Audit adaptations (reported, not silent):**
  `interpolateGreatCircle` arity/types/order identical
  (param names `fraction`/`totalMeters` vs `f`/`d` — not a
  signature difference); `core.GeoPoint` nonexistent →
  `geo.GeoPoint`; no `demEngine` in DI → `DemSession.engine`
  (nullable session); no adapter object → composable-owned
  `activeTerrainProfile` StateFlow (established pattern);
  calculation dispatched on `Dispatchers.IO` (provider does
  blocking SQLite reads). Pre-existing `geo/
  LineOfSightEngine` (LoS tool ray layer) untouched — the two
  engines coexist pending architect consolidation call.
- **Gates here:** Play + Enterprise **282/282 green each**
  (new `geo.los.LineOfSightEngineTest` 6/6: flat clear,
  coincident, endpoint/mid-path gaps, altitude fallback,
  ridge block); `geo/los` arch grep clean; Compose stays in
  `ui/`. No assemble per build boundary.
- **Device verification PENDING (human/Studio):** sheet
  button with GPS fix, chart render + banner on real DEM,
  profile reset on re-long-press.

## Comms strategy router — hybrid mesh (2026-09-27)

- **Commit (pushed):** `feat(android): comms strategy router
  (hybrid mesh)` (6 files + handoff). `NetworkProfile`
  (RADIO_SILENCE/MESH_ONLY/CLOUD_ONLY/HYBRID_BRIDGE) replaces
  the `isMeshActive` boolean; legacy pref migrates via
  `runCatching` (prior active→MESH_ONLY, prior offline→
  RADIO_SILENCE; key removed on first profile write).
  Broadcaster: PLI + markers mesh-only (silent return / throw
  `Markers require an active mesh profile`); chat master-gated
  on EMCON, UDP iff MESH_ONLY/HYBRID, Firestore iff
  CLOUD_ONLY/HYBRID. Chat UI shows EMCON/CLOUD_ONLY
  placeholders, send disabled only under EMCON. Settings shows
  a profile dropdown (mesh switch removed). Marker sheet
  surfaces the new marker error string inline via existing
  `e.message` path — no edit needed there.
- **Gates here:** Play + Enterprise **276/276 green each**;
  `isMeshActive` field/method/consumers at zero (only the two
  legacy pref-key strings inside the migration itself remain,
  as prescribed); all edits in `android/` + `ui/`, pure-logic
  boundary untouched. No assemble per build boundary.
- **Device verification PENDING (human/Studio):** migration
  default, per-profile UDP/Firestore routing (sniffer +
  console), EMCON chat + marker errors.

## Tactical targeting & CoT markers (2026-09-27)

- **Commit (local, unpushed):** `feat(android): tactical targeting
  and CoT markers` (15 files). Scope: `geo/cot/CotMarker.kt` +
  `android/comms/MarkerStore.kt` (uid-keyed StateFlow) +
  `ParsedCot.Marker`; `generateMarkerProto` (24h stale,
  `h-g-i-g-o`); parser branch `a-h-/a-n-/a-u-/b-m-`
  (`a-f-G` untouched, still PLI); `sendMarker` mesh-gated
  (`Mesh Transceiver is Offline` throw); listener routes
  `Marker -> markerStore` (default-param ctor, no test breakage);
  data-driven `CircleLayer` (`cot-marker-layer`,
  `toColor(get("color"))` — verified present in the 13.3.1 AAR
  via `javap`, no fallback needed) inside
  `installAtlasLayers`/style-reload path + `pushMarkers` +
  `markersToFeatures` (red/green/yellow/white); long-press now
  sets adapter-owned `targetDropPoint` and opens the targeting
  sheet (mesh-offline error surfaces inline red); Waypoint
  additionally saves the local journal waypoint, preserving the
  prior long-press creation path. `GeoPoint` used from
  `geo/` (prompt's `core.GeoPoint` does not exist); zero
  in-code comments per Directives.
- **Gates here:** `:app:testPlayDebugUnitTest` +
  `:app:testEnterpriseDebugUnitTest` **276/276 green each**
  (incl. new `MarkerStoreTest` 3/3, `CotProtobufTest` +1 marker,
  `AtakPayloadParserTest` +3 marker/waypoint/friendly-still-PLI);
  `geo/core/track/offline/tactical` arch grep clean (no
  android/androidx/maplibre). No assemble per build boundary.
- **Device verification PENDING (human/Studio):** mesh-off
  inline error on Hostile select; mesh-on red dot at touch
  coordinate; marker sync across two devices.
- **Accepted risks (in commit message):** no MarkerStore TTL;
  `android/comms/` placement vs `geo/cot/`; `toColor` verified.

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
5. Pushed 2026-09-21 (`176e382..133acbb`); tree in sync with origin.
