# Phase 0.6 — Portability Matrix

Records the Recovery-port lesson: renderer-native code is the primary
non-portable component; tile ladders, prefetch, layer toggles, and overlay
concepts are portable. Authority: master blueprint §§0.2, 0.4, Phase 0–2
(blueprint-reported; sources not vendored — see `docs/architecture/SOURCE-MATERIAL.md`).

## Portable (extract into `packages/`)

| Concern | Target | Evidence / note |
|---|---|---|
| Tile URL ladders / scheme templates | `atlas_provider_api` descriptors + `atlas_tiles` addressing | Blueprint-reported portable; concrete URLs stay out of core (descriptors only). |
| Cache-first delivery, byte validation, corrupt-entry recovery | `atlas_tiles` cache contracts | Recovery cache behavior (TBD source-confirm). |
| Bounded prefetch | `atlas_tiles` prefetch contracts | Recovery prefetch jobs (TBD). |
| Layer toggles, stacking, z-order, opacity, min/max zoom, group visibility | `atlas_layers` | Both Mantle and Recovery share the concept. |
| Camera serde (lat/lng/zoom/bearing/tilt) | `atlas_core` state / `atlas_map` camera | Pure data; renderer applies it. |
| Distance, bearing, destination, rings, area, bbox, validation | `atlas_geo` | Deterministic pure math; golden fixtures required. |
| Pin/waypoint codec, measurement transitions | `atlas_tactical`, `atlas_geo` | Wire/state logic, not widgets. |
| GeoJSON/parcel/H3/blueprint/migration conversions (models) | `atlas_data` | Preserve surveyed > derived authority. |
| Attribution/provenance/license carriage | `atlas_provider_api` + `atlas_data` + `security-baseline.md` | Explicit data, not UI afterthought. |
| Offline-pack manifests, progress/cancel/resume/delete semantics | `atlas_offline` | Contract first, transport later. |

## Non-portable (rebuild behind adapters)

| Concern | Adapter home | Note |
|---|---|---|
| MapLibre native setup, style lifecycle, source/layer calls | `atlas_map` renderer adapter (Mantle side) | Engine never imports MapLibre types. |
| Compose screen, lifecycle, permissions wiring | `integrations/sovereign_mantle` + host UI | Concept (permission-aware location) is portable; wiring is not. |
| `flutter_map` widgets, layer widgets, gestures | `atlas_map` renderer adapter (Recovery/Atlas-app side) | Engine exposes camera/layer state; adapter renders it. |
| Android location/compass APIs | `atlas_location` service implementations behind contracts | Contracts in `packages/`; platform code in adapters. |
| Mesh/Hub transports | `integrations/sovereign_mantle` transport adapters | Atlas understands a position-share packet model; never a specific mesh SDK. |
| Meeting workflows, ancestry/genealogy semantics | `integrations/*` | App features, not engine. |

## Boundary rule

A renderer migration must not require rewriting the geo engine. If a change
forces `atlas_geo` / `atlas_core` edits to swap MapLibre for flutter_map
(or reverse), the abstraction is wrong — fix the adapter, not the core.

## Layer-graph direction (from architect directive)

```text
Atlas Layer Graph
        │
        ├── Raster
        ├── Vector
        ├── Terrain (reserved)
        ├── Historical (reserved)
        ├── Indigenous Geography
        ├── Parcel
        ├── Structure
        ├── Tactical
        ├── Live (deferred to Phase 6)
        └── User
```

With an explicit composition/order system in `atlas_layers`. Behavior
preserved; architecture improved. No monolithic renderer.
