# Phase 0.2 — Domain Model (Normative Specification)

- **Status:** Normative spec. Implementation-independent. No language types defined.
- **Evidence base:** `capability-evidence-matrix.md`, `capability-forensics.md` (F-01..F-11),
  `behavior-contracts.md` (Proposed). Vocabulary: VERIFIED / PROPOSED / INFERRED / PLANNED / DECISION REQUIRED.
- **Rule:** Anything without source evidence is marked PROPOSED or DECISION REQUIRED, never VERIFIED.

## Conventions

For each entity: Purpose / Identity / Required / Optional / Units / Ranges /
Relationships / Authority / Lifecycle / Failure semantics. Status per entity.
No programming-language constructs appear in this document.

## Core geometry and geodesy

### AtlasCoordinate — PROPOSED (partially VERIFIED wire shape)

- **Purpose:** A single Earth-referenced position.
- **Identity:** value object; equality by (crs, lat, lon) with documented normalization (DECISION REQUIRED: normalization rule).
- **Required:** `latitude` (degrees), `longitude` (degrees), `crs` (default WGS84 — PROPOSED; observed SRC-A uses WGS84 direct coords F-05 VERIFIED, SRC-B latlong2 VERIFIED).
- **Optional:** `altitude` vs `elevation` (DECISION REQUIRED — see `coordinate-contract.md`).
- **Units:** degrees for angular; meters for linear (PROPOSED).
- **Ranges:** lat [-90, 90], lon [-180, 180] with normalization TBD (camera guards VERIFIED F-03; general-coordinate rule PROPOSED).
- **Relationships:** contained by Bounds, Geometry, Fix, Waypoint, Tile requests.
- **Authority:** N/A (position, not claim).
- **Lifecycle:** immutable value (PROPOSED).
- **Failure:** invalid MUST NOT silently become valid (`coordinate-contract.md` invariant, PROPOSED; camera-guard behavior VERIFIED F-03).

### AtlasCRS — PROPOSED (DECISION REQUIRED on representation)

- **Purpose:** Names the reference system a coordinate/geometry is expressed in.
- **Required:** identifier (representation TBD: string code vs struct — DECISION REQUIRED DEC-001).
- **Observed:** WGS84 direct use VERIFIED (SRC-A F-05, SRC-B latlong2); no CRS transformation observed in sources.
- **Failure:** unknown CRS → reject with explicit error, never assume WGS84 silently (PROPOSED).

### AtlasBounds — PROPOSED

- **Purpose:** Rectangular geographic extent for queries, packs, prefetch.
- **Required:** southwest + northeast coordinates in a single CRS (PROPOSED).
- **Antimeridian:** representation DECISION REQUIRED (DEC-005); no observed behavior in sources.
- **Failure:** empty/inverted bounds → explicit invalid (PROPOSED).

### AtlasGeometry / AtlasPoint / AtlasLine / AtlasPolygon / AtlasFeature / AtlasFeatureCollection — PROPOSED (shapes VERIFIED)

- **Purpose:** Renderer-independent geometry + attributed features (observed: SRC-A ~15 GeoJsonSources F-07 VERIFIED; SRC-C tile payloads VERIFIED).
- **Required:** geometry type tag; coordinate arrays in declared CRS; Feature requires `id` (nullable TBD) + `properties` map; Collection requires member list (possibly empty).
- **Validity:** see `geometry-contract.md` (ring closure, winding DECISION REQUIRED, empty-geometry semantics PROPOSED).
- **Authority:** every Feature SHOULD carry provenance/authority class (see `provenance-contract.md`); surveyed > derived invariant NORMATIVE (VERIFIED pattern F-04).
- **Lifecycle:** immutable values (PROPOSED).
- **Failure:** corrupt members are skipped per-source (SRC-A blueprint skip VERIFIED F-05; pin CSV drop VERIFIED F-06) — Atlas generalization PROPOSED: parsers MUST report skips, MUST NOT abort the whole collection silently (DECISION REQUIRED on reporting channel).

## Measures

### AtlasDistance — PROPOSED (computation VERIFIED)

- **Purpose:** Ground distance between positions.
- **Schema:** `value` + `unit` + `method` (e.g. great-circle) + `provenance` (conceptual, not code).
- **Observed:** haversine R6371 VERIFIED (F-02); display M<1km else KM VERIFIED.
- **Units:** meters canonical internally (PROPOSED — DECISION REQUIRED DEC-002).
- **Failure:** identical points → zero, not error (PROPOSED); invalid inputs → reject (PROPOSED).

### AtlasBearing — PROPOSED (computation VERIFIED)

- **Purpose:** Initial bearing between positions.
- **Observed:** atan2 normalized 0-359 VERIFIED (F-02); display `BRG 042°` VERIFIED.
- **Semantics:** 0° = true north, clockwise, range [0, 360) — PROPOSED (consistent with observation; compass true-north behavior VERIFIED F-08).
- **Failure:** coincident points → bearing undefined (DECISION REQUIRED: error vs null-object).

### AtlasArea — PLANNED

- No area computation observed in sources. No VERIFIED semantics. Contract deferred; do not cite as existing.

## Map state

### AtlasCameraState — PROPOSED (wire + guards VERIFIED)

- **Purpose:** What the map is looking at, independent of renderer.
- **Required:** `center` (AtlasCoordinate), `zoom` (float), `bearing` (degrees), `pitch/tilt` (degrees) — conceptual fields.
- **Observed:** `lat|lng|zoom|bearing|tilt` serde VERIFIED; HOME 39.83/-98.58/z3.0 VERIFIED; guards lat±90/lng±180/z0-24/tilt0-85 VERIFIED (F-03); rotation-only persistence VERIFIED (disk persistence PLANNED).
- **Camera-max vs provider-native distinction:** NORMATIVE (see `camera-contract.md`).
- **Failure:** malformed → safe default (HOME or last-known-good; default-selection DECISION REQUIRED for Atlas core vs adapter default).

### AtlasLayer / AtlasLayerState / AtlasLayerStack — PROPOSED (ordering VERIFIED)

- **Purpose:** Declarative layer composition consumed by renderer adapters.
- **Observed order VERIFIED (F-07):** rasters < graticule < H3 heritage < surveyed parcels < blueprints < flows < rings < markers < measurement-topmost.
- **Layer fields (conceptual):** identity, type, source ref, visibility, opacity, min/max zoom, attribution, availability/failure state (see `layer-contract.md`).
- **Normative correction (CAP-R03):** UI toggles are adapter concerns; core consumes ordered composition. VERIFIED as correction, PROPOSED as contract text.
- **Failure:** per-layer failure states (see `provider-contract.md`); one layer's failure MUST NOT fail the stack (observed: layers "simply never paint" VERIFIED).

## Providers, tiles, offline

### AtlasProvider / AtlasProviderDescriptor — PROPOSED (fields partially VERIFIED)

- **Purpose:** Describes a data source without containing its transport.
- **Observed:** per-provider ceilings VERIFIED; virtual `hybrid-*://` scheme VERIFIED; imagery-no-fallback rule VERIFIED; Recovery template table VERIFIED (with defects).
- **Descriptor fields (conceptual):** identity, version, capabilities, coverage, attribution, license/terms, native zoom range, CRS list, transport/auth needs (see `provider-contract.md`). Concrete URLs/keys NEVER in core (NORMATIVE, from extraction-matrix forbidden moves).

### AtlasTile / AtlasTileRequest / AtlasTileResponse — PROPOSED (flow VERIFIED)

- **Purpose:** Addressable raster/vector payload units.
- **Observed:** z/x/y identity VERIFIED (both ecosystems); key `<layer>/{z}_{x}_{y}.png` VERIFIED (SRC-C); cache-first lookup→validate→fetch→transparent-fail VERIFIED (F-10); non-atomic write + no-TTTL/LRU defects VERIFIED (must not be enshrined).
- **Distinctions (NORMATIVE):** tile identity ≠ URL ≠ storage key ≠ cache entry ≠ payload (see `tile-contract.md`).

### AtlasOfflinePack / AtlasOfflineRegion — PROPOSED (semantics partially VERIFIED)

- **Purpose:** Durable, bounded, inspectable offline dataset.
- **Observed:** zoom coercion (3-14 SRC-A; 11-15 SRC-B), layer selection rules, `maxTiles=800`, skip-exists resume, cancel flag VERIFIED (F-09/F-10); manifests/progress semantics PROPOSED (current status strings only).
- **Failure/progress/integrity:** see `offline-contract.md`. Manifest schema DECISION REQUIRED.

## Position and field domain

### AtlasLocationFix — PROPOSED (cascade VERIFIED)

- **Purpose:** A positioned device fix with quality metadata.
- **Observed:** GPS+NETWORK 2s, no-fused, provider guard (SRC-A); service-off/deny→fallback coords, <5min last-known, 20s medium timeout, stale fallback (SRC-B) — VERIFIED as source behavior.
- **Atlas correction:** adapter fallback coordinates (e.g. Twin Cities) MUST NOT become engine-global defaults (NORMATIVE correction; see `location-contract.md`).
- **Fields (conceptual):** coordinate, accuracy, timestamp, freshness class, source, mock flag (`!isMocked` gate VERIFIED for attendance).

### AtlasWaypoint / AtlasMeasurement — PROPOSED (codec + state VERIFIED)

- **Waypoint:** id, coordinate, label, source (local vs remote), sharing policy; CSV codec VERIFIED; remote-cyan vs local distinction VERIFIED (F-06).
- **Measurement:** armed-tool ordinal + ≤2-pt segment, RULER↔LINK preserves segment VERIFIED (F-02/F-06); radio smooth-earth half VERIFIED with terrain disclaimer (F-01); terrain-aware half PLANNED.
- **Radio boundary:** geometric RF in scope; terrain-aware RF reserved to terrain/DEM engine (NORMATIVE, architect-confirmed).

## Data and trust

### AtlasDataSource / AtlasProvenance — PROPOSED (carriage VERIFIED as requirement)

- **Purpose:** Answer where/when/version/authority/transformation for every significant object.
- **Observed:** surveyed-vs-derived authority VERIFIED (F-04); manual attribution rule VERIFIED (F-07); transformation-history carriage required by security baseline (PROPOSED schema).
- **Source classes:** AUTHORITATIVE / DERIVED / APPROXIMATE / USER_CREATED / HISTORICAL / LIVE / UNKNOWN — all PROPOSED (see `provenance-contract.md`).
- **Invariant (NORMATIVE):** approximation MUST NOT silently supersede authoritative geometry (architect-confirmed Phase 0.1).
