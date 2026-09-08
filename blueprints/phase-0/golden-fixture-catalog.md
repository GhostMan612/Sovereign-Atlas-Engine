# Phase 0.4 — Golden Fixture Catalog (Normative Index)

- **Status:** PROPOSED. Files live in `test/golden/<category>/`. Counts verified at commit time.
- **Reference math:** haversine `R=6371.0088`, spherical-approximation bearings; tolerance absorbs
  the SRC-A `R6371` delta. Display rules (`M<1km else KM`, `BRG 042°`) SOURCE-VERIFIED F-02.
- **MGRS stance:** no production library is selected to mint vectors (directive). MGRS fixtures
  are schema/round-trip-shape only, marked DECISION REQUIRED, with no invented expected strings.

## A. GEO coordinates (ATLAS-COORD-001) — `test/golden/geo/`

| Fixture | Content | Status |
|---|---|---|
| GEO-001_valid-home | 39.83/-98.58 valid WGS84 (Mantle HOME) | ATLAS-NORMATIVE shape; values SOURCE-VERIFIED |
| GEO-002_equator-prime | 0/0 valid | PROPOSED |
| GEO-003_north-west | 44.9778/-93.2650 valid (Recovery fallback coords as DATA, not as engine default) | SOURCE-VERIFIED as source fact |
| GEO-004_antimeridian | 0/179.999 valid; crossing semantics DEC-005 | PROPOSED + DECISION REQUIRED |
| GEO-005-high-lat | 70/0 valid | PROPOSED |
| GEO-006_lat-boundary | ±90 edge acceptance | PROPOSED |
| GEO-007_normalization | 190° longitude case; rule DEC-004 | DECISION REQUIRED |
| GEO-008_missing/invalid | covered in adversarial catalog (ADV-001..008) | ATLAS-NORMATIVE rejection |

## B. DISTANCE / BEARING (ATLAS-GEO-DIST/BRG-001) — `test/golden/distance|bearing/`

| Fixture | Inputs → expected (km / deg) | Tolerance |
|---|---|---|
| DIST-001_zero | same point → 0.0 | exact 0 |
| DIST-002_equator-1deg | (0,0)→(0,1) → 111.195080 | 0.01 km |
| DIST-003_meridian-1deg | (0,0)→(1,0) → 111.195080 | 0.01 km |
| DIST-004_antimeridian | (0,179)→(0,-179) → 222.390160 | 0.02 km |
| DIST-005_high-lat | (70,0)→(70,1) → 38.030531 | 0.01 km |
| DIST-006_long | HOME→Paris → 7479.448540 | 1.0 km |
| BRG-001_north/south/east/west | → 0/180/90/270 | 0.5° |
| BRG-002_diagonal | (0,0)→(1,1) → 44.996 | 0.5° |
| BRG-003_high-lat | (70,0)→(70,1) → 89.530 | 0.5° |
| BRG-004_coincident | same point → undefined (rejection, DECISION REQUIRED on shape) | — |

## C. RANGE RINGS (ATLAS-GEO-RING-001) — `test/golden/tactical/`

Steps SOURCE-VERIFIED (`0.1/0.25/0.5/1.0/2.0/5.0`, 4 rings/step, 65-pt circles, N/E/S/W spokes).
Fixtures assert: step table exact; ring count = 4 per active step; spoke count = 4; null/negative → empty
(SOURCE-VERIFIED `:782`); vertex geometry within tolerance (method recorded; exact floats are
method-dependent — tolerance 1% of radius, PROPOSED).

## D. CAMERA (ATLAS-CORE-CAM-001) — `test/golden/camera/`

CAM-001_home (39.83/-98.58/z3.0-bearing/tilt round-trip, SOURCE-VERIFIED values as DATA);
CAM-002_serde (pipe-format round-trip incl. bearing/tilt); CAM-003_malformed (rejection, SOURCE-VERIFIED
guards); CAM-004_missing-fields (rejection); CAM-005_zoom-bounds (0-24 accept, outside reject);
CAM-006_ceiling-distinction (camera-max 24 vs per-provider natives TOPO17/SAT19/OSM19/DARK20/USGS16/IMAGERY22
as DATA — ATLAS-NORMATIVE distinction, SOURCE-VERIFIED numbers).

## E. LAYER ORDER (ATLAS-MAP-ORDER-001) — `test/golden/layers/`

ORDER-001_canonical: the 9-rank F-07 sequence as an ordered ID list (order significant);
ORDER-002_toggle-mapping: adapter toggle set → ordered core list (CAP-R03 correction, ATLAS-NORMATIVE);
toggle widgets themselves are NOT fixtured (adapter concern).

## F. LAYER DESCRIPTORS (ATLAS-LAYER-001) — `test/golden/layers/`

One descriptor fixture per category (raster/vector/geojson/elevation/historical/boundary/parcel/
structure/local): identity fields present, no URLs/keys in core shapes (ATLAS-NORMATIVE ban).

## G. TILE IDENTITY (ATLAS-TILE-ID-001) — `test/golden/tiles/`

TILE-001_same-identity-different-provider (identities differ); TILE-002_same-provider-different-layer;
TILE-003_same-xy-different-zoom; TILE-004_key-determinism (same input → same
`<layer>/{z}_{x}_{y}` key shape, SOURCE-VERIFIED shape); TILE-005_no-silent-fallback
(`osm`-missing→dark defect MUST NOT recur: unknown layer is an error, ATLAS-NORMATIVE).

## H. CACHE BEHAVIOR (ATLAS-TILE-RET/CACHE-001) — `test/golden/cache/`

HIT/MISS/INVALID-payload→refetch/MISSING→fetch-ordering fixtures (flow SOURCE-VERIFIED F-10);
ATOMIC-REQUIREMENT and BOUNDED-REQUIREMENT fixtures assert the negative findings as requirements
(non-atomic REJECTED, unbounded REJECTED — ATLAS-NORMATIVE); metadata-shape fixture (PROPOSED).

## I. OFFLINE RESOLUTION (ATLAS-OFF-001/002/003) — `test/golden/offline/`

RES-001_order (local → pack → cache → provider, deterministic, ATLAS-NORMATIVE);
RES-002_no-global-fallback-coordinate (engine returns unavailable; adapters choose UX — ATLAS-NORMATIVE
correction of Recovery behavior); pack-scope fixtures (zoom/layer scoping SOURCE-VERIFIED; manifest
schema PROPOSED/DEC-011).

## J. PROVENANCE (ATLAS-DATA/PROV-001) — `test/golden/provenance/`

Seven-question answerability fixtures (what/where-from/who/when/transformation/authority/verifiability);
authority-class labeling per fixture (AUTHORITATIVE→UNKNOWN vocabulary PROPOSED).

## K–O. GEOMETRY FAMILIES — `test/golden/geometry|parcels|h3|structures|migration/`

Paired surveyed-vs-derived fixtures (K): same nominal object in both forms; precedence assertion
(ATLAS-AUTH-001, ATLAS-NORMATIVE). Parcel validity classes (L): valid polygon/multipolygon/closed-ring
accept; malformed-ring/insufficient-vertices reject (SOURCE-VERIFIED skip precedent). H3 (M): derived
labeling mandatory; authoritative-claim rejection. Structures (N): footprint acceptance + deep-zoom
representation concept (provider ceilings NOT extended to rasters). Migration (O): origin/destination/
segments deterministic; malformed-endpoint rejection; zero-length rejection (PROPOSED).

## P. MGRS / GRID — `test/golden/tactical/` (schema only)

MGRS-001_shape: declares input/output fields + round-trip property WITHOUT expected conversion strings
(no library selected; DECISION REQUIRED). No invented vectors. Grid-overlay density rules PLANNED.

## Q. RADIO / LOS SCOPE — `test/golden/radio/`

RADIO-001_smooth-earth (observer/target elevation → horizon ≈10.09 km @1.5m/1.5m per `4.12·(√h1+√h2)`;
Fresnel ≈17.54 m @10km/2.437GHz per `8.657·√(d/f)` — SOURCE-VERIFIED formulas/constants);
RADIO-002_scope-negative (`terrain-aware LOS NOT implemented by this contract`, model tag
`SMOOTH-EARTH-NO-TERRAIN` required — ATLAS-NORMATIVE). NO fake terrain-aware vectors exist by design.

## R. ATTRIBUTION — `test/golden/layers/`

ATTR-001_visible-set (only visible public rasters attributed; private IMAGERY excluded — SOURCE-VERIFIED
rule, ATLAS-NORMATIVE requirement); ATTR-002_no-defect-import (Esri/OTM unattributed gap MUST NOT recur).
