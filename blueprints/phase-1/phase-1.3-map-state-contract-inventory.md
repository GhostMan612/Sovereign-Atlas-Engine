# Phase 1.3-A — Map-State Contract Inventory

- **Status:** Inventory only. Every behavior traced; nothing inferred from renderers.
- **Scope:** `packages/atlas_map` at Phase 1.2-close (`AtlasCameraState`, `AtlasMapState`).

## Existing surface

| Type / member | Contract basis | Class |
|---|---|---|
| `AtlasCameraState{cursor, zoom, bearing, pitch}` | ATLAS-CORE-CAM-001 | ATLAS-NORMATIVE shape |
| `maxZoom 24 / minZoom 0 / maxPitch 85` | SOURCE-VERIFIED guard bounds (F-03) carried as model DATA | SOURCE-VERIFIED data; ATLAS-NORMATIVE shape |
| `home()` 39.83/-98.58/z3.0 | SOURCE-VERIFIED Mantle fallback as DATA (DEC-008 open) | SOURCE-VERIFIED, not global default |
| `validate()` (center/zoom/bearing-finite/pitch) | ATLAS-CORE-CAM-001 guards | ATLAS-NORMATIVE |
| Bearing: any-finite accepted (no range guard observed) | Absence of evidence, preserved as-is | SOURCE-VERIFIED absence; DEC-008 wrap open |
| `parse/serialize` pipe wire (5-field, MALFORMED surfacing) | ATLAS-CORE-CAM-001 forensic wire | ATLAS-NORMATIVE mechanics; versioning DEFERRED (unversioned as observed) |
| `AtlasMapState{camera, layers}` (empty stack valid) | ATLAS-CORE-CAM-001 + MAP-ORDER-001 + offline invariant | ATLAS-NORMATIVE |
| `validate()` (camera + id/opacity/zoom sanity; conformance not validity) | Layer/map contracts + 0.5A Ruling 2 | ATLAS-NORMATIVE |
| `==`/hashCode (structural, title-excluded via layers) | Equality via composition | ATLAS-NORMATIVE |

## Per-field semantic table (1.3-B summary; detail in architecture doc)

| Field | Meaning | Domain | Normalization | Validation | Equality | Serde | Persistence |
|---|---|---|---|---|---|---|---|
| center | viewed position (WGS84) | lat ±90, lng ±180 | none (DEC-004) | reject, no clamp | structural | piped | none (deferred) |
| zoom | Atlas semantic capability | [0, 24] model DATA | none | reject outside | structural | piped | none |
| bearing | orientation, 0=true-north PROPOSED | any finite | none in camera (geo normalizer separate) | finite-only | structural | piped | none |
| pitch | guarded scalar; 3D meaning DEFERRED | [0, 85] guard DATA | none | reject outside | structural | piped | none |
| layers | ordered composition (authoritative: atlas_layers) | any explicit order | n/a (order significant) | ids/opacity sanity | structural | none | none |

## Serialization vs persistence (1.3-J/K)

- Serialization EXISTS (pipe wire) on forensic-contract basis (CAM-002 round-trip).
  Canonical: fixed field order, Dart double rendering (deterministic per value),
  invalid → MALFORMED, unversioned (versioning DEFERRED, owned by contract text).
- Persistence: ABSENT. No storage of any kind. Persistable-state meaning itself
  DEFERRED (no contract defines it).

## Antimeridian (1.3-C)

Camera centers are always validated positions in range; crossing the antimeridian
is representational (successive centers jump +179→−179) with no wrap logic anywhere.
Policy status: DEFERRED as contract (no behavior to execute; nothing invented).

## Transitions (1.3-H)

No transition API exists. Authorized addition: pure `copyWith` constructors on
camera + map state (deterministic recomposition, no events/callbacks/animation/
gestures/async). Validation stays separate by design (construction ≠ validation,
as established).
