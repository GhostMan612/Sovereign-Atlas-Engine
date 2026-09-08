# ATLAS-GEOM-001 — Geometry Contract (Normative)

- **Status:** PROPOSED (shape family VERIFIED; strict semantics PROPOSED).
- **Trace:** CAP-007/008/009/010/F-04/F-05 SRC-A `:2090-2193`; CAP-023/F-07 SRC-A `:1853-2044`;
  CAP-017/018/F-02 SRC-A `:1526-1564`; CAP-026 SRC-A `:2140-2141`.
- **Normative keywords:** MUST / MUST NOT / SHOULD as written.

## 1. Types (conceptual)

`Point`, `MultiPoint`, `LineString`, `MultiLineString`, `Polygon`, `MultiPolygon`,
`GeometryCollection`, `Feature` (`id` + `geometry` + `properties`), `FeatureCollection`
(members, possibly empty). Observed in use VERIFIED (GeoJSON sources per concern F-07);
strict membership rules PROPOSED.

## 2. Validity (normative, PROPOSED except where noted)

- `Point`: single valid coordinate (ATLAS-COORD-001).
- `LineString`: ≥2 valid coordinates (observed: blueprint corrupt→skip `<3/<2 pts` VERIFIED F-05).
- `Polygon`: closed rings (first == last); closure enforcement PROPOSED (closure handling not observed — DECISION REQUIRED DEC-007 whether writers auto-close or readers reject).
- Winding (CW/CCW) semantics: DECISION REQUIRED (DEC-007). No observed rule; MUST NOT invent one.
- Empty geometry: representation + legality DECISION REQUIRED (DEC-007). Observed code skips corrupt features rather than representing empties.
- Dimensionality: 2D normative for Phase 0; 3D PLANNED.
- Coordinate precision: inherits DEC-002; no per-geometry override in Phase 0.
- Bounding boxes: derivable (PROPOSED); antimeridian-crossing box behavior DECISION REQUIRED (DEC-005).
- Equality: exact-value equality PROPOSED; tolerance-based equality DECISION REQUIRED (DEC-007, blocks fixtures).

## 3. Authority (ATLAS-NORMATIVE rule derived from SOURCE-VERIFIED pattern)

- **Evidence (SOURCE-VERIFIED):** real parcels drawn above H3, converted without H3 decode (F-04 SRC-A `:1884-1885/:2117-2122`).
- **Requirement (ATLAS-NORMATIVE):**

> **Authoritative surveyed geometry outranks derived/approximated geometry when both
> describe the same spatial object.**

- Precedent VERIFIED: real parcels drawn above H3, converted without H3 decode (F-04 SRC-A `:1884-1885/:2117-2122`).
- H3-derived geometry MUST be treated as derived unless provenance explicitly establishes otherwise (NORMATIVE).
- Every Feature SHOULD carry authority class (see `provenance-contract.md`); missing authority → treat as `UNKNOWN`, never as authoritative (PROPOSED).

## 4. Simplification and approximation (PROPOSED)

- Any simplification/densification/inflation (e.g. observed `0.02°` H3 floor, 65-pt ring approx, acreage squares) MUST record method + parameters in provenance (PROPOSED; observed code does not record — this is a new Atlas requirement, not a claim about sources).
- Approximation MUST NOT silently supersede authoritative geometry (restatement of §3, NORMATIVE).

## 5. Parsing robustness (normative precedent VERIFIED, generalization PROPOSED)

- Observed: corrupt blueprint features skipped, malformed pins dropped, stale level → ALL (F-05/F-06/CAP-026). Atlas rule PROPOSED: parsers MUST NOT abort a whole collection on one bad member; MUST report skips on a defined channel (channel DECISION REQUIRED DEC-006).
