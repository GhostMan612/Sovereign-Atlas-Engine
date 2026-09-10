# GIS Phases — Engine Completion (PASS / CLOSED)

- **Status:** PASS / CLOSED 2026-09-10. Blueprint Phases 4, 5, 6, 7, 8,
  9, 10, 11, 12 engine side, in blueprint order, one controlled pass.
  UI/rendering/hardware remain app-track (2.4/3.5/10-UI).
- **Activation:** ADR-004 (terrain/analysis/history/plugins enter scope
  with fixed edges; sampler/transport injection instead of cross-edges).

## 1. What was built (production, per blueprint)

- **Phase 4 (geo toolkit):** DMS strict parse/format, exact unit
  conversions, projected/datum holders, Chamberlain–Duquette spherical
  area, area-weighted centroid, ray-casting PIP (boundary-inside, holes
  subtract), haversine perimeter, nearest-on-segment, zoom-stepped
  graticule (destination/bearing/distance pre-existed and are reused).
- **Phase 5 (data):** normalized feature model (full ADR-001 field set),
  structural validation, dataset descriptors, pure-map GeoJSON normalizer
  (unknown/null geometries refuse — no silent drops).
- **Phase 6 (live):** `AtlasLiveProviders.usgs-elevation` tile endpoint
  (3DEP, served by the existing fetch operation — elevation tiles exposed
  a kind-gate gap, fixed: tile-FORM addresses serve any kind) +
  `AtlasDatasetSource` (NOAA weather declared; fetching stays downstream).
- **Phase 8 (terrain):** explicit grids (voids explicit, OOB null),
  Horn slope/aspect (flat aspect null, never zero-filled), standard
  hillshade, void-propagating bilinear profiles.
- **Phase 9/11 (analysis):** exact-predicate segment intersection
  (touches hit), radial zones, Sutherland–Hodgman clip, slerp densify
  (linear rejected for contract dishonesty), Douglas–Peucker simplify,
  LOS + radial viewshed over injected samplers (voids transparent,
  curvature/refraction documented out).
- **Phase 7 (history):** immutable snapshots (source context mandatory),
  ordered timeline, instant lookup, provenance listing.
- **Phase 10 (tactical):** waypoints/tracks (shared-service length),
  radial+polygon geofences (shared PIP), FSPL link estimation (explicit
  terms only), tiered sharing policy, MGRS zone/band hook (full MGRS
  stays MGRS-001-blocked), reticle data model. Mesh transport untouched.
- **Location:** fix/heading/log pure models. **Phase 12 (plugins):**
  manifest (segment-wise version compat), registry (explicit
  register/enable/disable, grant queries), closed permission set.

## 2. Real bugs caught by fixtures (production fixed, never tests)

- Degenerate closing edge made PIP true for the whole plane (zero-length
  edge guard added).
- Fetch kind-gate refused elevation tiles (parse-first restructure).
- Densify lerped instead of slerping (contract implementation fixed).
- Single-earth unification: area now uses the source-verified 6371.0088
  radius (goldens recomputed).

## 3. Verification snapshot (at closure)

- Runner: total=453, pass=413, fail=0, blocked=8, notApplicable=32 —
  two-run byte-identical (pending final re-run below). All 348 prior kept.
- 65 new fixtures (GEO2/FEAT/LIVE/TERR/ANAL/HIST/TAC2/POS/PLUG +
  ADV-101..110), zero dup IDs.
- `dart analyze` (strict set, whole workspace): clean. Format: clean.
  Leakage green (no new arch-scan violations; plugins import core only).
- DEC-001..019 OPEN (MGRS-001 still blocked — hook only, as contracted).

## 4. App track (ADR-005, first host LANDED)

- Renderer decision (evidence-based): flutter_map 8.3.2 (pub-cache
  present + offline-resolvable; maplibre absent from cache).
- `apps/atlas`: flutter_map shell (engine-registry OSM template, readout
  bar, marker), `flutter analyze` clean, widget smoke test passes,
  `flutter build apk --debug` ✓ (APK produced, Gradle via local SDK).
- Install/launch on device NOT verified (no reachable device/emulator in
  this session — adb unresponsive); first-launch verification is the next
  app-track item, explicitly not claimed.
