# ADR-001 — Atlas Package and Workspace Architecture

- **Status:** Accepted (Phase 0 foundation)
- **Date:** 2026-09-08
- **Context:** Scaffold audit 2026-09-08; `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md` v1.0 Draft §0.3
- **Deciders:** Atlas architect (ChatGPT) + execution audit (OpenCode / Muse Spark 1.3)
- **Scope:** Package names, responsibilities, reserved scope. No directory renames in this change.

## 1. Context

The master blueprint §0.3 contemplated 13 packages:

```text
atlas_core, atlas_geo, atlas_coordinates, atlas_layers, atlas_providers,
atlas_tiles, atlas_map, atlas_location, atlas_compass, atlas_tactical,
atlas_offline, atlas_data, atlas_security
```

The scaffold as committed (`dee1f7e`) contains 15 packages:

```text
atlas_core, atlas_map, atlas_tiles, atlas_layers, atlas_geo,
atlas_location, atlas_offline, atlas_analysis, atlas_terrain,
atlas_tactical, atlas_history, atlas_data, atlas_provider_api,
atlas_security, atlas_plugins
```

The audit flagged this as naming drift / possible scope creep. The architect
directive is to keep the scaffold's general direction and resolve the
discrepancy by decision, not by silent directory changes.

## 2. Decision

The scaffold package set is canonical. No packages are renamed or deleted
in Phase 0.

### 2.1 Phase 0 implementation packages (11)

| Package | Owns | Must not own |
|---|---|---|
| `atlas_core` | Domain primitives, value objects, map state machine, command/event envelopes, configuration, errors. No renderer types, no Flutter/Android imports in public models. | Rendering, providers, UI |
| `atlas_geo` | Geometry, coordinate systems (WGS84 lat/lon, decimal degrees, DMS, UTM, MGRS hooks, projected-coordinate object, datum metadata, unit conversion), spatial math (distance, bearing, destination, bbox, area, centroid, point-in-polygon, polyline length), grid abstractions. Replaces the contemplated standalone `atlas_coordinates` package. | Renderer, persistence |
| `atlas_layers` | Layer definitions, layer state, groups, composition / z-order, visibility / opacity / min-max zoom, ordering persistence model. | Tile bytes, provider auth |
| `atlas_provider_api` | Provider **contracts only**: raster, vector, GeoJSON/dataset, elevation, weather, historical, boundary, parcel, structure, local-dataset interfaces; capability, attribution, license, caching/prefetch-policy descriptors. Provider **implementations** must live outside this package (in dedicated provider packages, `integrations/`, or later SDK plugins). Replaces the contemplated `atlas_providers` name. | Any concrete tile URLs, keys, SDK clients |
| `atlas_tiles` | Tile addressing (key normalization, provider/layer namespace, z/x/y), cache contract, validation, atomic-write contract, expiration/LRU policy, provider-policy enforcement hooks, prefetch contract, pack-manifest model. | Renderer, concrete HTTP client |
| `atlas_map` | Map orchestration: initialization contract, camera state, camera movement, gesture state, layer/source registration abstraction, marker abstraction, hit-test abstraction, style lifecycle, render invalidation. Translates engine models for renderer adapters; contains no MapLibre / flutter_map imports. Replaces any monolithic controller. | Geospatial math, tile bytes |
| `atlas_location` | GPS, tracking, heading, compass/orientation abstractions. Replaces the contemplated standalone `atlas_compass` package (`compass/` and `heading/` remain subdirectories). | UI widgets, tactical semantics |
| `atlas_offline` | Offline packs, regional storage, downloads, sync contracts: manifest, integrity hash, resumable/cancellable semantics, progress, deletion, import/export policy hooks. | Provider rate-limit values (declared by providers, enforced by tiles/offline) |
| `atlas_data` | Normalized feature model (`id`, `geometry`, `properties`, `source`, `sourceVersion`, `retrievedAt`, `license`, `confidence`, `accuracy`, `sensitivity`), dataset descriptors, schemas, GeoJSON/MBTiles/raster/vector normalization contracts. | Live polling, rendering |
| `atlas_security` | Data classification, permissions, privacy, transport, integrity contracts. See `blueprints/phase-0/security-baseline.md`. | Secret storage implementation |
| `atlas_tactical` | Tactical/field domain: MGRS/grid tools, waypoints, tracks, radio-link estimation models, reticle, sharing-policy models, geofencing contracts. Consumes `atlas_geo` / `atlas_terrain` services; must not own a parallel geometry stack. | Mesh transport implementation (adapter in `integrations/`) |

### 2.2 Reserved forward-architecture packages (4, not Phase 0 scope)

| Package | Future phase | Note |
|---|---|---|
| `atlas_terrain` | Phase 8 | DEM/elevation, profiles, hillshade/contours/slope/aspect contracts. |
| `atlas_analysis` | Phases 9/11 | LOS/viewshed/buffer/intersect/union/spatial-query contracts. |
| `atlas_history` | Phase 7 | Historical datasets, timeline, provenance-through-time. |
| `atlas_plugins` | Phase 12 | Plugin manifest, discovery, lifecycle, registry, permissions. |

These directories remain placeholders. No implementation, no new
subdirectories, and no dependency edges to or from them are approved in
Phase 0. Moving any of them into implementation scope requires a new ADR.

### 2.3 Explicit non-decisions (TBD)

- Language/workspace harness: the `lib/src/` layout and `.gitignore`
  (`dart_tool/`, `pub-cache/`) imply Dart/Flutter, but no SDK constraint,
  `pubspec.yaml`, Melos config, lint set, or CI is approved in this ADR.
  See `blueprints/phase-0/acceptance-criteria.md`. TBD by a later
  workspace ADR (no code until then).
- `atlas_security/lib/src/secrets/`: unapproved placeholder. It may be a
  secret-**policy** contract or may be deleted. TBD by the security ADR.
  Do not build on it.
- Per-package `pubspec` boundaries and versioning: TBD.
- Concrete provider list and default basemap set: TBD (Phase 2).

## 3. Consequences

- `atlas_geo` absorbs `atlas_coordinates`; `atlas_location` absorbs
  `atlas_compass`; `atlas_provider_api` is contracts-only. Future docs that
  use the old §0.3 names must map them through this ADR.
- Phase 0 work is limited to the 11 implementation packages as
  **specifications and pure-logic extractions**; the 4 reserved packages
  stay empty.
- `dependency-map.md` enforces the allowed directions derived from this ADR.
- Any rename, deletion, merge, or new package requires a new ADR.

## 4. Alternatives considered

- **Revert scaffold to the §0.3 list.** Rejected: creates churn, loses the
  forward placeholders, and splits coordinates/compass artificially.
- **Approve all 15 packages for Phase 0 implementation.** Rejected:
  expands Phase 0 surface before contracts exist.
- **Merge `atlas_tiles` into `atlas_offline`.** Rejected: addressing/cache
  policy (tiles) and pack/dataset durability (offline) are separate
  concerns per the no-monolith principle.

## 5. References

- `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md` §§0.3, 0.6, Phase 0–1.
- `blueprints/phase-0/dependency-map.md`, `capability-inventory.md`,
  `extraction-matrix.md`, `portability-matrix.md`.
- `docs/architecture/SOURCE-MATERIAL.md` (source provenance).
- Scaffold audit 2026-09-08 (git-tracked scaffold, 112 files, no
  production code).
