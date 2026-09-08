# Phase 0.4 — Extraction Matrix

Maps each `capability-inventory.md` entry to its Atlas disposition.
See ADR-001 for package responsibilities. Evidence: `capability-evidence-matrix.md`;
traces: `capability-forensics.md`; proposed contracts: `behavior-contracts.md`
(all Proposed, none implemented).

## Disposition definitions

| Disposition | Meaning | Test expectation |
|---|---|---|
| `KEEP` | Design already satisfies Atlas principles. Preserve behavior and architecture; rehost behind contracts. | Regression test pins current behavior before any move. |
| `EXTRACT` | Portable domain logic belongs in Atlas packages. Move it, generalize app-specific bits into adapters. | Golden/unit tests on the pure logic; no renderer/UI imports. |
| `REBUILD` | Concept is valid but the implementation is platform-bound (Compose/MapLibre, Android APIs, flutter_map widgets). Reimplement against `atlas_map` / provider / location contracts. | Contract tests + adapter tests; core stays renderer-free. |
| `REJECT / REPLACE` | Correct for the original app, wrong for a general engine (app workflows, single-vendor wiring, host transports). Stays in `integrations/` or is replaced by a provider/plugin slot. | No core code; adapter or deferred-phase slot only. |

## Matrix (evidence-refined 2026-09-08; authority is the inventory)

| IDs | Disposition | Destination | Notes |
|---|---|---|---|
| CAP-003 (offline boot concept), CAP-006 (deep-vector concept), CAP-019 scope boundary (smooth-earth only on-device), CAP-022 attribution rule, CAP-R06 validate-recover behavior | KEEP | `atlas_offline`, `atlas_layers`, `atlas_tactical` contracts | Preserve the fail-secure invariant: no network → Atlas still starts. Do not copy Recovery defects (dead cache wiring, missing OSM template, attribution gaps) or Mantle rotation-only persistence as if durable. |
| CAP-002, CAP-005, CAP-007, CAP-008, CAP-009, CAP-010, CAP-011-codec, CAP-012-model, CAP-013-model, CAP-014-model, CAP-016-readout, CAP-017, CAP-018, CAP-020-semantics, CAP-023-model, CAP-024, CAP-025, CAP-026, CAP-027-model, CAP-R02-table, CAP-R04-locator, CAP-R06-cache, CAP-R07-math | EXTRACT | `atlas_core`, `atlas_geo`, `atlas_layers`, `atlas_tiles`, `atlas_offline`, `atlas_data`, `atlas_tactical`, `atlas_location`, `atlas_provider_api` per inventory | Forensic order: haversine/bearing/rings/camera-serde/pin-codec first (Verified pure); then converters (parcel/H3/blueprint/migration); then policy models (descriptors, tile key, manifest). Externals (`SovereignGrid`/`Mgrs`/`TilePack`/interceptor) stay Inferred until their own files are traced. |
| CAP-001, CAP-003-impl, CAP-004-wiring, CAP-006-impl, CAP-011-hittest, CAP-012-transport, CAP-013-transport, CAP-014-impl, CAP-015, CAP-020-impl, CAP-021-shape, CAP-023-wiring, CAP-027-impl, CAP-R01, CAP-R06-wiring, CAP-R07-runner | REBUILD | `atlas_map` renderer adapters, `atlas_location` services, `integrations/*` transports | Renderer migration must not rewrite the geo engine. Recovery runner needs concurrency/ToS/multi-layer/progress rebuild; Mantle hit-test needs projection-interface rebuild. |
| CAP-004-model split (imagery-no-fallback stays adapter policy), CAP-021 core residency, CAP-R03 core residency (KEEP in adapter instead), CAP-R04-meetings, CAP-R05 (Phase 0), app workflows, Hub/mesh specifics | REJECT from core / REPLACE with slot | `integrations/*` adapters; live slots deferred to Phase 6 | A feature does not enter the core merely because it appears on the map. CAP-R03 corrected this pass: toggles are adapter UI; core consumes the ordered list. |

## Extraction order (smallest coherent units)

1. Pure geospatial math with no I/O (`atlas_geo`): distance, bearing, destination, bbox, validation. (Contracts `ATLAS-GEO-DIST/BRG/RING-001` proposed.)
2. State serde with no renderer (`atlas_core` / `atlas_map` camera, pin codec, measurement transitions). (Contracts `ATLAS-CORE-CAM-001`, `ATLAS-TAC-PIN-001` proposed.)
3. Conversion models with no SDK (`atlas_data` GeoJSON/parcel/H3/blueprint shapes).
4. Policy models with no network (`atlas_provider_api` descriptors `ATLAS-PROV-DESC-001`, `atlas_tiles` key/policy `ATLAS-TILE-CACHE-001`, `atlas_offline` manifest + `ATLAS-TILE-PREFETCH-001`).
5. Contracts (`atlas_map` `ATLAS-MAP-ORDER-001`, `atlas_location` `ATLAS-LOC-RES-001`, `atlas_tactical` `ATLAS-GEO-LINK-001` smooth-earth half).
6. Adapters last (`integrations/*`, renderer implementations).

## Forbidden moves

- No extraction may hard-code a single provider's URLs, keys, or SDK types into `packages/` (Recovery URL table is data for descriptors, not core constants).
- No extraction may move Mantle mesh/Hub or Recovery meeting semantics into `packages/`.
- No extraction may drop surveyed-vs-derived authority (CAP-007 > CAP-008), provenance, or attribution (incl. IMAGERY exclusion and OSM/Esri/OTM/USGS attribution repair).
- No defect may be enshrined: non-atomic cache writes, missing TTL/LRU, dead `CachedTileProvider` wiring, single-layer/indeterminate prefetch, rotation-only persistence presented as durable.
