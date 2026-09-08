# ATLAS-PROV-DESC-001 et al. — Provider Contract (Normative)

- **Status:** PROPOSED (descriptor fields + ceilings + scheme partially VERIFIED; taxonomy PROPOSED).
- **Trace:** CAP-004/005 SRC-A `:156-181/:318-336/:859-860/:174-176`;
  CAP-R02 SRC-B `:41-83` + SRC-C `:43-51/:70-76`; CAP-021 SRC-A `:472-483`;
  CAP-022 attribution rule; blueprint §0.5/Phase 2.
- **Normative keywords:** MUST / MUST NOT as written.

## 1. Descriptor (conceptual fields — PROPOSED; asterisks = observed precedent VERIFIED)

- `identity`* (unique; observed: per-source setup `:1755-1840`), `version` (PROPOSED),
  `capabilities`* (raster/vector/dataset; observed per-source branches),
  `coverage`* (observed: per-provider ceilings + IMAGERY Hub-only rule),
  `attribution`* (observed manual rule), `license/terms`* (observed obligation-driven strings),
  `nativeZoomRange`* (observed ceilings), `crsList` (PROPOSED; only WGS84 observed),
  `transport/auth`* (observed: pinned Hub P1 + keyless public P2; IMAGERY no-P2),
  `cachingPolicy` + `prefetchPolicy`* (observed: exclusions `:854-857`, `maxTiles`, politeness; formal policy PROPOSED),
  `update/version metadata` (PROPOSED).
- **Hard rule (NORMATIVE):** concrete tile URLs, keys, and SDK clients MUST NOT live in
  `atlas_provider_api` or any core package. Recovery URL tables are descriptor *data* for
  implementations/adapters, not core constants (extraction-matrix forbidden move).

## 2. Contract categories (PROPOSED — taxonomy, not classes)

`RasterTileProvider`, `VectorTileProvider`, `GeoJSONProvider`, `ElevationProvider`,
`HistoricalMapProvider`, `BoundaryProvider`, `ParcelProvider`, `StructureProvider`,
`WeatherProvider`, `LocalDatasetProvider`. Justification: mirrors blueprint §0.5/Phase 2 and
observed families (rasters, GeoJSON vectors, parcels, blueprints, weather chip).
No implementation implied; elevation/historical are PLANNED slots.

## 3. Request / response (PROPOSED; Source: none beyond tile flow)

- `ATLAS-PROV-REQ-001`: request carries identity + bounds/zoom/layer selection + CRS + version pin (PROPOSED).
- `ATLAS-PROV-RESP-001`: response carries payload + `retrievedAt` + `sourceDate` + version + provenance (PROPOSED; provenance carriage required by security baseline).
- **Origin:** architectural proposal. No observed wire protocol is enshrined.

## 4. Failure states (partially justified)

```text
UNAVAILABLE ............ justified (layers "never paint"; transparent-fail VERIFIED)
NETWORK_REQUIRED ....... justified (offline-empty behavior VERIFIED)
OFFLINE_UNAVAILABLE .... justified (IMAGERY offline-cleared; meetings _loadError VERIFIED)
INVALID_RESPONSE ....... justified (corrupt-tile delete+refetch VERIFIED; corrupt-feature skip VERIFIED)
AUTH_REQUIRED .......... PROPOSED (Hub pairing observed as transport fact; Atlas state text PROPOSED)
RATE_LIMITED ........... PROPOSED (politeness gap + maxTiles observed; formal rate state PROPOSED)
OUT_OF_COVERAGE ........ PROPOSED (coverage concept observed via ceilings; state text PROPOSED)
UNSUPPORTED ............ PROPOSED (Source: none. Architectural proposal.)
```

Only the justified four are normative in Phase 0.2; the remaining four are PROPOSED state names
pending the provider-failure spec (DEC-010).

## 5. Capability discovery (PLANNED)

`ATLAS-PROV-DISC-001`: registry/query mechanics deferred. No normative text in Phase 0.2.
