# Phase 1.0-A — Core API Inventory (Phase 0.5 slice as built)

- **Status:** Inventory only. No refactor performed for this checkpoint.
- **Scope:** `packages/atlas_core`, `atlas_geo`, `atlas_layers`, `atlas_map`
  at `8fd6f1f` + `2750afa` (0.5A comments). Vehicle: Dart (provisional, DEC-014 open).
- **Classes:** PUBLIC (barrel-exported, fixture-covered) / INTERNAL (barrel-exported
 _helpers, no direct fixture) / TEST SUPPORT (test/ only) / PROVISIONAL (marked
  non-normative per 0.5A) / OPEN-DECISION DEPENDENT (blocked fixtures).

## atlas_core (`lib/atlas_core.dart` barrel; 4 files)

| API | Kind | Class | Contract / fixture |
|---|---|---|---|
| `AtlasRejection(category, message)` | PUBLIC | value object, open string category (DEC-006) | ATLAS-VALID-001; ADV-005/006/007/009/019/020 |
| `AtlasRejectionException(rejection)` | PUBLIC | throwing boundary wrapper | CAM-003/004, ADV-020 |
| `AtlasValidation.valid/invalid` | PUBLIC | explicit outcome, rejection iff invalid | ATLAS-VALID-001; all validation fixtures |
| `AtlasId(value)` + `AtlasIds.check` | PUBLIC | non-empty check (`INVALID_IDENTITY`); scope-uniqueness out of scope (DEC-013) | domain-model identity; map-state validation |
| `AtlasComparison.withinTolerance` | INTERNAL | tolerance compare; non-finite→false | determinism-policy; used by runner |

Cross-package imports: none (zero engine dependencies — VERIFIED by grep 0.5 gate).
No test-only APIs exposed. No serialization APIs (none required by fixtures).

## atlas_geo (`lib/atlas_geo.dart` barrel; 4 files)

| API | Kind | Class | Contract / fixture |
|---|---|---|---|
| `AtlasCoordinate(lat, lon, crs=WGS84)` + `wgs84` const | PUBLIC | 2D const value object | ATLAS-COORD-001; GEO-001..006 |
| `AtlasCoordinates.validate/check` | PUBLIC | finite + range + CRS guards; lon strict (DEC-004) | GEO-001..006, ADV-005/006/007/019 |
| `AtlasGeoMath.haversineKm` + `referenceRadiusKm` | PUBLIC | haversine, parameterized radius | ATLAS-GEO-DIST-001; DIST-001..006 |
| `AtlasGeoMath.initialBearingDeg` | PUBLIC | atan2 0–359; coincident THROWS | ATLAS-GEO-BRG-001; BRG-001..003 |
| `AtlasGeoMath.formatDistance/formatBearing` | PUBLIC | `850 M`/`111.20 KM`, `BRG 042°` | F-02 display; DIST-001/002 |
| `AtlasRings.validateRing/validatePath` | PUBLIC | ≥4 closed / ≥2-point rules | ATLAS-GEOM-001; GEOM-001/002, PARCEL-002 |
| `AtlasFlowSegment` + `validate` | PUBLIC | non-null endpoints; zero-length rejects | FLOW-001/002 |
| `AtlasCollectionScreening.screen` | PUBLIC | order-preserving skip + indices | ATLAS-VALID-001; GEOM-003, ADV-012/013 |
| `AtlasRingSet` + `AtlasRangeRings` | PUBLIC | steps/radii/spokes; null→empty | ATLAS-GEO-RING-001; RING-001/002 |
| Zero-length rejection | PROVISIONAL (0.5A Ruling 3a, DEC-007) | — | FLOW-003, ADV-022 |
| Empty-accept screening | PROVISIONAL (0.5A Ruling 3b, DEC-007) | — | ADV-013 |
| Ring fractions [0.25..1.0] | PROVISIONAL (0.5A Ruling 3c, contract text) | — | RING-001 (radii-within-1% only) |
| Coincident throw | PROVISIONAL (0.5A Ruling 3d/4, BRG-004) | — | BRG-004 BLOCKED |
| Unclosed-ring handling | OPEN-DECISION DEPENDENT (DEC-007) | invalid here; no auto-close | ADV-011 BLOCKED |
| Winding/empties/equality-tolerance | OPEN-DECISION DEPENDENT (DEC-007) | not implemented | ADV-010 BLOCKED |
| Longitude normalization | OPEN-DECISION DEPENDENT (DEC-004) | strict reject | GEO-007, ADV-008 BLOCKED |

Depends on: atlas_core only (VERIFIED; `rings.dart` needs no core symbols — no unused import).

## atlas_layers (`lib/atlas_layers.dart` barrel; 1 file)

| API | Kind | Class | Contract / fixture |
|---|---|---|---|
| `AtlasLayerKind` (9 values) | PUBLIC | PROPOSED taxonomy enum | ATLAS-LAYER-001; DESC shapes (provider_api scope) |
| `AtlasLayerDefinition` (no URL field) | PUBLIC | identity/kind/provider/zoom/attribution/isPrivate | ORDER/ATTR/DESC families |
| `AtlasLayerState` (visible, opacity) | PUBLIC | adapter-written, core-consumed | ORDER-002 (CAP-R03) |
| `AtlasLayerStack` + `orderedVisible` | PUBLIC | order-significant List semantics | ATLAS-MAP-ORDER-001; ORDER-001 |
| `conformsToBaseline` flag | PUBLIC | flag-not-veto (0.5A Ruling 2) | ADV-016 BLOCKED (needs contract clarification) |
| `AtlasBaselineRanks.ranks/rankOf` | PUBLIC | F-07 9-rank sequence; unknown→null (ignored) | ORDER-001 |
| `AtlasAttribution.forVisible` | PUBLIC | set semantics; private excluded | ATLAS-ATTR-001; ATTR-001/002 |

Depends on: atlas_core only (VERIFIED; geo edge allowed but unexercised — no import
declared until geometry is genuinely needed).

## atlas_map (`lib/atlas_map.dart` barrel; 2 files)

| API | Kind | Class | Contract / fixture |
|---|---|---|---|
| `AtlasCameraState` + `home()` | PUBLIC | center/zoom/bearing/pitch; HOME = Mantle DATA (DEC-008) | ATLAS-CORE-CAM-001; CAM-001 |
| `maxZoom 24 / minZoom 0 / maxPitch 85` | PUBLIC | SOURCE-VERIFIED guard bounds as DATA | CAM-005/006 |
| `parse/serialize` pipe wire | PUBLIC | 5-field; MALFORMED surfacing; range re-check | CAM-002/003/004, ADV-020 |
| Bearing: any-finite accepted | PUBLIC | no observed guard; no invention (DEC-008) | — |
| `AtlasMapState(camera, layers)` | PUBLIC | empty stack VALID (fail-secure boot) | offline invariant |
| `validate` (camera + id/opacity/zoom sanity) | PUBLIC | baseline conformance NOT a veto (0.5A Ruling 2) | ADV-016 posture |
| Opacity [0,1], finite zoom bounds | PROVISIONAL | ranges PROPOSED, disclosed in messages | map-state validation |

Depends on: atlas_core + atlas_geo (camera) + atlas_layers (map state) — one-way,
no cycles (VERIFIED).

## Test support (not production)

- `test/phase05_runner.dart`: fixture discovery/execution/reporting; dart:io +
  dart:convert confined here (never in production). Classification: TEST SUPPORT.
- Envelope-rejection mechanism (ADV-001..004): guaranteed by typed APIs + grep
  assertion (`_noDynamicConstructors`); no production `fromJson`/`fromDynamic`.

## Gaps (no fixture coverage by design — reserved scope)

TILE/CACHE/OFFLINE/PROV/H3/STRUCT/RADIO/LOC/PIN/MGRS families; DESC-001..009
(provider_api); PAIRED-001/H3/ADV-015/018/023 (atlas_data authority model);
ADV-017/021 (tiles/cache); ADV-014 (DEC-013 duplicates).
