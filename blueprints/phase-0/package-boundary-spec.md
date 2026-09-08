# Phase 0.3 — Package Boundary Spec (Scaffold Preserved, No Renames)

- **Status:** PROPOSED specification of the committed scaffold. Zero renames, zero moves.
- **Authority:** ADR-001 (canonical map) + `blueprints/phase-0/dependency-map.md` (allowed directions).
- **Phase of introduction:** 11 packages = Phase 0 contract scope; 4 = reserved (Phases 7/8/9/11/12).

## Canonical set (15, unchanged)

| Package | Responsibility (ADR-001) | Allowed deps (from dependency-map) | Forbidden deps | Public API boundary (PROPOSED) | Platform coupling | Phase |
|---|---|---|---|---|---|---|
| `atlas_core` | Primitives, state machine, command/event envelopes, config, errors | none | everything engine-external in public models (Flutter, Android, map SDKs, HTTP, GPS, fs, credentials) | value objects + state + errors | none | 0 |
| `atlas_geo` | Geometry, CRS, spatial math, grids, measurements | `atlas_core` | UI, renderer, I/O | pure functions + value types | none | 0 |
| `atlas_layers` | Layer definitions, state, groups, composition/z-order | `core, geo, provider_api, data` | tile bytes, provider auth, renderer types | layer graph + policy | none | 0 |
| `atlas_provider_api` | Provider contracts ONLY (descriptors, capability/attribution/license/policy shapes) | `atlas_core` | any implementation (URLs, keys, SDK clients) | interfaces + descriptor data shapes | none | 0 |
| `atlas_tiles` | Addressing, cache/validation, prefetch, pack-manifest model, policy hooks | `core, geo, provider_api` | renderer, concrete HTTP client | key/policy/cache/prefetch contracts | none in contracts | 0 |
| `atlas_map` | Orchestration: init/camera/gestures/layer-source registration/markers/hit-test/style/invalidation | `core, geo, layers, provider_api, tiles, offline, data, location` | renderer SDK imports | renderer-neutral orchestration contracts | adapter-side only | 0 |
| `atlas_location` | GPS/tracking/heading/compass service contracts (incl. compass+heading subdirs) | `core, geo` | UI widgets, tactical semantics | service interfaces + fix types | adapter-side only | 0 |
| `atlas_offline` | Packs, regions, downloads, sync, manifest/integrity/progress contracts | `core, tiles, provider_api, data` | provider rate values (declared by providers, enforced here) | pack/region/manifest contracts | none in contracts | 0 |
| `atlas_data` | Normalized feature model, descriptors, schemas, GeoJSON/MBTiles/raster/vector normalization contracts | `core, geo, provider_api` | live polling, rendering | feature/dataset contracts | none | 0 |
| `atlas_security` | Classification, permissions, privacy, transport, integrity contracts | `core, data` (metadata only) | secret storage implementation; `secrets/` subdir unapproved | policy types | none in contracts | 0 |
| `atlas_tactical` | MGRS/grid tools, waypoints/tracks, radio-link models, reticle, sharing-policy, geofencing contracts | `core, geo, data` | mesh transport implementation; parallel geometry stack | tactical domain contracts | none in contracts | 0 |
| `atlas_analysis` | RESERVED (LOS/viewshed/buffer/overlay/query) | no approved edges in Phase 0 | all (until ADR opens) | none | — | 9/11 |
| `atlas_terrain` | RESERVED (DEM/elevation/profiles/hillshade) | no approved edges in Phase 0 | all | none | — | 8 |
| `atlas_history` | RESERVED (historical datasets/timeline) | no approved edges in Phase 0 | all | none | — | 7 |
| `atlas_plugins` | RESERVED (manifest/discovery/lifecycle/registry) | no approved edges in Phase 0 | all | none | — | 12 |

## Preserved Phase 0.2 decisions (ATLAS-NORMATIVE)

- `atlas_provider_api` = contracts; implementations live outside it.
- `atlas_geo` = coordinate systems + geospatial primitives (no separate `atlas_coordinates`).
- `atlas_location` = location + compass + heading (no separate `atlas_compass`).

## Verification

- Until a language harness exists: every change names affected packages and cites
  `dependency-map.md` (AGENTS.md §5). Post-harness: import-lint enforcing the table above (PLANNED).
