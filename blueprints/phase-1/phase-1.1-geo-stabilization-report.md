# Phase 1.1 — Geo Stabilization Report (Closure)

- **Status:** Closure record. Checkpoints 1.1-A..I executed in order; none skipped.
- **Predecessor:** Phase 1.0 CLOSED. Vehicle: Dart (provisional, DEC-014 open).

## Scope

`atlas_geo` expansion only (+ runner + fixtures + phase-1 docs). `atlas_core`,
`atlas_layers`, `atlas_map` untouched. No other package touched.

## Files

- Added: `blueprints/phase-1/phase-1.1-geo-contract-inventory.md` (1.1-A),
  `blueprints/phase-1/phase-1.1-geo-stabilization-report.md` (this file, 1.1-I).
- Added production: `packages/atlas_geo/lib/src/normalization/angles.dart`
  (`AtlasAngles`), `packages/atlas_geo/lib/src/geometry/polyline.dart`
  (`AtlasPolyline`), `packages/atlas_geo/lib/src/geometry/polygon_types.dart`
  (`AtlasPolygon`, `AtlasBoundingBox`); barrel extended.
- Modified production: `distance.dart` (bearing delegates to the named normalizer
  — identical math, verified by unchanged BRG fixtures; plus PROVISIONAL
  `destinationPoint`).
- Added fixtures (21): `angles/NORM-001..004`, `boxes/BOX-001..004`,
  `geometry/POLY-001..003 + GON-001/002`, `distance/DIST-007/008`,
  `bearing/DEST-001 + BRG-005`, `adversarial/ADV-024..027`.
- Modified: `test/phase05_runner.dart` (new dispatch branches only).

## APIs added / modified / unchanged

- Added: `AtlasAngles` (3 named ops + guard), `AtlasGeoMath.destinationPoint`
  (PROVISIONAL), `AtlasPolyline` (PROVISIONAL), `AtlasPolygon`/`AtlasBoundingBox`
  (PROVISIONAL, crossing explicit).
- Modified: `initialBearingDeg` internals only (delegation; zero behavior change).
- Unchanged: coordinate model/validation, haversine, formatters, rings, ring
  validators, segments, screening, layers, map, core. No altitude (DEC-003 gap
  recorded). No serialization for new types (not fixture-required).

## Contracts affected

ATLAS-GEO-BRG-001 (normalizer extraction), ATLAS-GEO-DIST-001 (antipodal/short
stability vectors), ATLAS-GEOM-001 (polyline/polygon/bbox minimum),
ATLAS-GEO-RING-001 (destination generalization), ATLAS-VALID-001 (angle guards).
No contract text rewritten; new behavior is PROVISIONAL with ownership recorded
(1.1-A inventory: DEC-004/005/007 or contract-owned).

## Fixtures

121 files (100 inherited + 21 new), all JSON-valid. New-twin policy: every new
golden with a sharp edge has an adversarial twin (BOX-003/ADV-025, GON-002/ADV-027).

## Blocked cases (unchanged + none newly forced)

GEO-007, BRG-004, MGRS-001, ADV-008/010/011/014/016 (all pre-existing).
BOX-003/ADV-025 join as DEC-005-OPEN (new but honestly blocked, not failed).

## Provisional behaviors (full list)

Zero-length (DEC-007), empty-accept (DEC-007), ring fractions (contract-owned),
coincident throw (BRG-004) — all pre-existing — plus: signed/longitude
normalizers (contract-owned/DEC-004), `destinationPoint` (contract-owned),
polyline/polygon/bbox types (contract-owned, DEC-005/007 edges open).
All marked PROVISIONAL — NOT ATLAS-NORMATIVE in code.

## Dependencies / analysis / format / architecture / determinism

Zero production dependencies (dart:core/dart:math only). `dart analyze`: no
issues. `dart format --check`: clean (normalization applied, semantics
re-verified identical). Ban-list grep: no code-level hits. Two-run output
byte-identical. Final: total=122, pass=69, fail=0, blocked=8, notApplicable=45.

## Open decisions

DEC-001..019 ALL STILL OPEN (no closure, no new DECs — sub-details owned by
existing opens per the 0.5A no-parallel-system ruling).

## Gate

PASS (all 31 Phase 1.1 gates hold).
