# Phase 1.4-A — Provider/Tile Contract Inventory (Expansion Scoping)

- **Status:** Inventory only. Scope: `atlas_provider_api` production types +
  fixtures/runner. `atlas_tiles` (acquisition/cache) stays empty.
- **Predecessor:** Phase 0.2 provider/tile contracts; Phase 0.4 DESC + TILE
  fixtures (currently NOT_APPLICABLE — this phase makes them executable).

## Placement ruling (no package-structure change)

The tile identity/request/key/entry/payload vocabulary lives in
`atlas_provider_api` (requests/ + responses/), NOT in `atlas_tiles`:
identity and request-meaning are provider-contract concerns; acquisition,
transport, caching, and packs stay deferred with `atlas_tiles`/`atlas_offline`
empty. No dependency-map change (provider_api → core only).

## Additions (classified)

| Addition | Home | Class | Ownership |
|---|---|---|---|
| `AtlasDataKind` (9 values: rasterTiles, vectorTiles, geojson, elevation, historical, boundary, parcel, structure, localDataset) | provider/ | PROPOSED → PROVISIONAL | ATLAS-PROV-DESC-001 taxonomy (mirrors DESC fixture families) |
| `AtlasProviderCapability` (tileServing, offlinePacks, queryable, liveRefresh) | capabilities/ | PROPOSED → PROVISIONAL, advertised-only (§9 analog) | Contract-owned; distinct from layer claims (different consumer) |
| `AtlasProviderDescriptor` (id/title/kinds/capabilities/nativeZoom/attribution/license/sensitivity; NO url/endpoint/key fields) | provider/ | PROPOSED → PROVISIONAL shapes; no-URL rule ATLAS-NORMATIVE | ATLAS-PROV-DESC-001 |
| Geographic coverage model | DEFERRED (needs geo dependency → dependency-map ADR; zoom range suffices) | DECISION REQUIRED (placement, not semantics) | — |
| `AtlasTileCoordinate{z,x,y}` + validation (non-negative ints; 0 ≤ x,y < 2^z) | requests/ | ATLAS-NORMATIVE (slippy-grid math; TILE fixtures) | ATLAS-TILE-ID-001 |
| `AtlasTileScheme{xyz,tms}` + TMS y-flip | requests/ | ATLAS-NORMATIVE (standard definitions; Esri-vs-OSM order observed F-10) | ATLAS-TILE-ID-001 |
| `AtlasTileIdentity{provider,layer,coordinate,scheme}` (structural ==) | requests/ | ATLAS-NORMATIVE (TILE-001/002/003) | ATLAS-TILE-ID-001 |
| `AtlasTileRequest{identity,params}` + pure template substitution | requests/ | ATLAS-NORMATIVE mechanics (caller-supplied template + params; no policy invented; subdomains via explicit params, no rotation rule) | ATLAS-TILE-RET-001 area |
| `AtlasTileKey` layer-scoped `<layer>/{z}_{x}_{y}` + round-trip | requests/ | SOURCE-VERIFIED shape (TILE-004); namespace extension DEFERRED | ATLAS-TILE-ID-001 |
| `AtlasTilePayload{id,byteLength}` + `AtlasTileEntry{identity,key,payloadId}` | responses/ | PROPOSED → PROVISIONAL pure-data shapes (no cache behavior) | ATLAS-TILE-CACHE-001 area (shapes only) |
| Non-`AtlasProvider` modeling: NO fetch interface, NO client, NO HTTP, NO credentials | — | Correctly absent (acquisition deferred) | Directive boundary |

## Explicit non-goals

HTTP/network, tile downloads, cache engine (+eviction/TTL), offline packs,
credentials/keys, live acquisition, renderer wiring, TMS-beyond-flip schemes
(QUADKEY etc. unrequested), coverage polygons, provider registry/discovery.
