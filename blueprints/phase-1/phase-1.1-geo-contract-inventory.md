# Phase 1.1-A — Geo Contract Inventory (Expansion Scoping)

- **Status:** Inventory only. Additions below are the MINIMUM set; each carries a
  classification. Anything marked PROPOSED ships (if at all) as PROVISIONAL code
  with ownership recorded; OPEN-DECISION items stay blocked or unimplemented.
- **Predecessor:** `blueprints/phase-1/phase-1.0-core-api-inventory.md` (atlas_geo section).

## Existing (Phase 0/1.0, stable — do not rework without contract reason)

| API | Standing |
|---|---|
| `AtlasCoordinate` + `AtlasCoordinates.validate/checked` | ATLAS-NORMATIVE (strict; DEC-001/003/004 open around it) |
| `haversineKm` / `initialBearingDeg` / formatters | ATLAS-NORMATIVE method; convention PROPOSED; coincident PROVISIONAL |
| `AtlasRings.validateRing/validatePath` | ATLAS-NORMATIVE (precedent-generalized) |
| `AtlasFlowSegment` (+ zero-length PROVISIONAL) | ATLAS-NORMATIVE shape; zero-length PROVISIONAL (DEC-007) |
| `AtlasCollectionScreening` (+ empty-accept PROVISIONAL) | ATLAS-NORMATIVE policy; empty PROVISIONAL (DEC-007) |
| `AtlasRangeRings` (+ fractions PROVISIONAL) | Steps/counts/empty ATLAS-NORMATIVE; fractions PROVISIONAL |

## Proposed additions for 1.1 (classified)

| Addition | Class | Ownership | Rationale |
|---|---|---|---|
| `AtlasAngles.normalizeBearingDeg` ([0,360)) | ATLAS-NORMATIVE (extraction of inline SOURCE-VERIFIED behavior, zero semantic change) | ATLAS-GEO-BRG-001 | Directive §9: name the operation; identical math |
| `AtlasAngles.normalizeSignedDeg` ((−180,180]) | PROPOSED → ship PROVISIONAL | contract-owned (angular ops); no DEC assigned | Directive §9: distinct signed-angle domain op |
| `AtlasAngles.normalizeLongitudeDeg` | PROPOSED → ship PROVISIONAL | DEC-004 (validation still rejects; op does not settle the policy) | Directive §5: normalization as a NAMED op, never hidden in validation |
| `AtlasAngles` NaN/Inf guard (throw NON_FINITE) | ATLAS-NORMATIVE (consistency with comparison policy) | ATLAS-VALID-001 | Mirrors `withinTolerance` non-finite refusal |
| `AtlasGeoMath.destinationPoint` | PROPOSED → ship PROVISIONAL | ATLAS-GEO-RING-001 area (generalizes ring helper) | Stabilizes existing private helper; tested |
| `AtlasPolyline` (structural validity + meaningfulness + length) | PROPOSED → ship PROVISIONAL | ATLAS-GEOM-001 area (no DEC assigned; dup-tolerance is contract text) | Directive §12; duplicates allowed, never silently dropped |
| `AtlasPolygon` (exterior + holes, validity, bounds) | PROPOSED → ship PROVISIONAL | ATLAS-GEOM-001 area; orientation recorded-not-enforced (DEC-007) | Directive §13 minimum; no topology, no repair |
| `AtlasBoundingBox` (S/W/N/E + crossing flag) | PROPOSED → ship PROVISIONAL | DEC-005 (antimeridian explicitly open) | Directive §14: crossing EXPLICIT (`west > east`), never accidental |
| Crossing-box `contains` | OPEN-DECISION DEPENDENT → NOT implemented (explicit rejection `UNRESOLVED_ANTIMERIDIAN`) | DEC-005 | Directive §14: document-don't-invent |
| Canonicalization operation | NOT introduced (open; needs DEC-002 precision first) | DEC-002/004 | Directive §15: validation ✓ / normalization ✓ / canonicalization recorded-open |
| Unit-safe value types | NOT introduced (naming convention suffices; no contract demands types) | contract-owned note | Directive §6: no aesthetic unit systems |
| Altitude/elevation | NOT introduced (gap recorded, DEC-003) | DEC-003 | Directive §4: record, don't invent vertical datum |
| MGRS conversion | BLOCKED (unchanged) | owning decision | Directive §21 |
| Serialization for new types | NOT introduced (not fixture-required) | — | Directive §17: no speculative serde |

## Unit convention (recorded, PROPOSED)

Public numeric suffixes carry units (`Km`, `Deg`, `M` in formatters, radius `Km`,
angles `Deg`). No unit-typed wrappers are introduced in 1.1.
