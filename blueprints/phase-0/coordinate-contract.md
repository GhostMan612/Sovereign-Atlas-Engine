# ATLAS-COORD-001 — Coordinate Contract (Normative)

- **Status:** PROPOSED (wire shape + guards VERIFIED; semantics PROPOSED).
- **Trace:** CAP-002/F-03 SRC-A `LandSectorView.kt:1603/1612/1620/1615-1630/307-309`;
  CAP-016/F-08 SRC-A `:77/:948-983`; CAP-R04/F-11 SRC-B `:126-201`.
- **Normative keywords:** MUST / MUST NOT / SHOULD as written.

## 1. Representation (conceptual, not code)

- A coordinate carries: `latitude` (degrees), `longitude` (degrees), `crs` identifier.
- Default CRS is WGS84 (PROPOSED — consistent with SRC-A WGS84-direct F-05 VERIFIED and SRC-B latlong2 VERIFIED; formal default still PROPOSED until DEC-001).
- CRS representation (string code vs struct): DECISION REQUIRED (DEC-001).
- Numeric precision required: DECISION REQUIRED (DEC-002). No precision guarantee was observed in sources; MUST NOT claim one.
- Altitude vs elevation vs ellipsoidal vs orthometric height: DECISION REQUIRED (DEC-003). Sources use 2D positions; 3D semantics are PLANNED.

## 2. Validity (normative)

- Valid latitude: [-90, 90]. Valid longitude: [-180, 180] prior to normalization (normalization rule DECISION REQUIRED DEC-004).
- VERIFIED precedent: camera guards reject outside `lat±90/lng±180` and fall back to HOME (F-03). Generalize as ATLAS-NORMATIVE rule: **invalid coordinates MUST NOT silently become valid coordinates.**
  (Evidence: SOURCE-VERIFIED for camera guards; Requirement: ATLAS-NORMATIVE for general coordinates.)
- NaN/infinite inputs MUST be rejected (PROPOSED; no observed handling — DECISION REQUIRED on error channel DEC-006).
- Unknown CRS MUST be rejected, never silently treated as WGS84 (PROPOSED).

## 3. Normalization and edge cases

- Longitude normalization (e.g. 190° → -170°): DECISION REQUIRED (DEC-004). No observed behavior.
- Antimeridian representation for positions and bounds: DECISION REQUIRED (DEC-005). No observed behavior.
- Poles: behavior DECISION REQUIRED (DEC-005). No observed behavior; MUST NOT extrapolate from haversine/bearing code.

## 4. Distinctions (normative vocabulary, PROPOSED)

- `latitude/longitude`: angular position on the reference ellipsoid.
- `altitude`: height of an object above a reference (datum TBD).
- `terrain elevation`: height of the ground surface (requires elevation provider; PLANNED).
- `ellipsoidal height` vs `orthometric height`: datum-dependent split (PROPOSED vocabulary; values TBD with terrain phase).

## 5. Failure semantics

- Parsing/validation failure → typed invalid + reason (PROPOSED); camera-level precedent (fallback to HOME) is an application-level policy, NOT a core-coordinate policy (the core MUST surface the error; the adapter chooses the fallback).
