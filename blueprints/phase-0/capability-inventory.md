# Phase 0.3 — Capability Inventory

- **Status:** Draft (blueprint-reported; source not vendored — see `docs/architecture/SOURCE-MATERIAL.md`).
- **Rule:** Every entry preserves the blueprint's claim without inventing
  paths, values, or line numbers. Unverified fields are `TBD`.
- **Confirmation values:** `needs-source-confirm` (default) → `source-confirmed` (with vendored path + date).

## Disposition vocabulary (see `extraction-matrix.md`)

- `KEEP` — architecture already sound; preserve design.
- `EXTRACT` — portable logic belongs in Atlas.
- `REBUILD` — platform-specific implementation; carry the concept, rewrite the code.
- `REJECT / REPLACE` — valid for the original app, not appropriate for a general engine.

## Reference capabilities — Sovereign Mantle TacMap family

| ID | Name | Blueprint-reported behavior | Classification | Atlas destination | Disposition | Priority | Confirmation |
|---|---|---|---|---|---|---|---|
| CAP-001 | MapLibre rendering | MapLibre Android native host, Compose screen | Renderer | `atlas_map` renderer adapter | REBUILD | P0 | needs-source-confirm |
| CAP-002 | Camera pose persistence | lat/lng/zoom/bearing/tilt serialization | Map state | `atlas_map` camera / `atlas_core` state | EXTRACT | P0 | needs-source-confirm |
| CAP-003 | Fail-secure offline boot | Bundled style boot; offline graticule fallback; usable with no network | Offline boot | `atlas_map` + `atlas_offline` | KEEP (concept) / REBUILD (impl) | P0 | needs-source-confirm |
| CAP-004 | Independent raster layers | SAT, TOPO, OSM-family, DARK, USGS-family, local imagery as separately managed sources | Layer / provider | `atlas_layers` + `atlas_provider_api` + `atlas_tiles` | EXTRACT (model) / REBUILD (wiring) | P0 | needs-source-confirm |
| CAP-005 | Per-provider native zoom ceilings | Each raster provider declares its ceiling; camera zooms to ~24 for vectors | Zoom policy | `atlas_layers` policy + `atlas_provider_api` descriptor | EXTRACT | P0 | needs-source-confirm |
| CAP-006 | Blueprint overzoom / deep vector zoom | Vector detail extends beyond raster native resolution | Zoom policy | `atlas_layers` + `atlas_map` | KEEP (concept) | P0 | needs-source-confirm |
| CAP-007 | Parcel geometry (surveyed) | True surveyed parcel boundaries; higher authority than approximations | Data | `atlas_data` | EXTRACT | P0 | needs-source-confirm |
| CAP-008 | H3 heritage overlays | H3-derived approximations, explicitly lower authority than surveyed parcels | Data / grid | `atlas_data` + `atlas_geo` grids | EXTRACT | P1 | needs-source-confirm |
| CAP-009 | Blueprint structures | Geometry-type dispatch polygon/line/point; level filtering; WGS84 direct coords with parcel context; carries id/kind/level/label | Data | `atlas_data` | EXTRACT | P1 | needs-source-confirm |
| CAP-010 | Migration flows | Migration segment generation / flow polylines | Data / analysis | `atlas_data` (reserved: `atlas_analysis`) | EXTRACT | P2 | needs-source-confirm |
| CAP-011 | Tactical pins / waypoints | Dropped pins, named waypoints, encoding/decoding | Tactical | `atlas_tactical` waypoints | EXTRACT | P0 | needs-source-confirm |
| CAP-012 | Live peers | Peer position shares, stale-position semantics | Tactical | `atlas_tactical` peer map (transport = adapter) | EXTRACT (model) / REBUILD (transport) | P1 | needs-source-confirm |
| CAP-013 | SOS / emergency markers | SOS marker type distinct from routine pins | Tactical | `atlas_tactical` | EXTRACT | P1 | needs-source-confirm |
| CAP-014 | Live position + tracking | GPS position, tracking, recenter | Location | `atlas_location` | EXTRACT (model) / REBUILD (impl) | P0 | needs-source-confirm |
| CAP-015 | Compass / heading | Compass + heading readout | Location | `atlas_location` compass/heading | REBUILD | P1 | needs-source-confirm |
| CAP-016 | MGRS readout / grid | MGRS display, grid overlay, precision selector (TBD) | Tactical / geo | `atlas_tactical` mgrs + `atlas_geo` grids | EXTRACT | P0 | needs-source-confirm |
| CAP-017 | Ruler (distance + bearing) | Point-to-point distance, great-circle + bearing | Measurement | `atlas_geo` measurements | EXTRACT | P0 | needs-source-confirm |
| CAP-018 | Range rings | Selectable radius steps + cardinal spokes geometry | Measurement | `atlas_geo` measurements | EXTRACT | P1 | needs-source-confirm |
| CAP-019 | Radio-link estimation | Smooth-earth radio horizon + Fresnel-zone info on-device; terrain occlusion explicitly out of scope on-device | Tactical / analysis | `atlas_tactical` radio (reserved: `atlas_analysis`, `atlas_terrain`) | KEEP (scope boundary) | P1 | needs-source-confirm |
| CAP-020 | Tile packing / offline packs | Offline pack creation with manifests | Offline | `atlas_offline` + `atlas_tiles` | EXTRACT | P0 | needs-source-confirm |
| CAP-021 | Hub-first / fallback transport | Hub-first with fallback; hybrid transport model | Transport | `integrations/sovereign_mantle` adapter (not core) | REJECT from core / REBUILD as adapter | P1 | needs-source-confirm |
| CAP-022 | Attribution handling | Attribution generated from active layers/sources | Provider | `atlas_provider_api` + `atlas_layers` | EXTRACT | P0 | needs-source-confirm |
| CAP-023 | GeoJSON conversion | GeoJSON import/export abstractions | Data | `atlas_data` geojson | EXTRACT | P0 | needs-source-confirm |
| CAP-024 | Parcel/H3/blueprint conversions | Parcel geometry, H3 geometry, blueprint feature conversions | Data | `atlas_data` | EXTRACT | P1 | needs-source-confirm |
| CAP-025 | Measurement state transitions | Ruler/rings start/update/cancel states | Map interaction | `atlas_map` interaction + `atlas_geo` | EXTRACT | P1 | needs-source-confirm |
| CAP-026 | Zoom/coordinate validation | Coordinate + zoom validation rules | Geo | `atlas_geo` | EXTRACT | P0 | needs-source-confirm |
| CAP-027 | Layer ordering / visibility defaults | Conceptual layer order (raster → graticule → heritage/H3 → parcels → blueprints → flows → rings → markers → measurement); default visibility TBD | Layer | `atlas_layers` | EXTRACT (model); defaults TBD | P0 | needs-source-confirm |

## Reference capabilities — Recovery for All port family

| ID | Name | Blueprint-reported behavior | Classification | Atlas destination | Disposition | Priority | Confirmation |
|---|---|---|---|---|---|---|---|
| CAP-R01 | flutter_map host | Flutter renderer host | Renderer | `atlas_map` renderer adapter | REBUILD | P0 | needs-source-confirm |
| CAP-R02 | Basemap templates (OSM/sat/dark/light/topo) | Template set with cache-backed delivery | Provider | `atlas_provider_api` + `atlas_tiles` | EXTRACT (templates as data TBD) | P0 | needs-source-confirm |
| CAP-R03 | Layer toggles | Layer on/off concepts | Layer | `atlas_layers` | EXTRACT | P0 | needs-source-confirm |
| CAP-R04 | Location + meeting markers | Location display; meeting markers (app-specific) | Location / app | `atlas_location` (location); meetings → `integrations/recovery_for_all` adapter | EXTRACT / REJECT from core (meetings) | P1 | needs-source-confirm |
| CAP-R05 | Weather overlay hooks | Weather product hooks | Live provider | Reserved live-provider slot (Phase 6) | REJECT from Phase 0 core | P2 | needs-source-confirm |
| CAP-R06 | Filesystem tile cache + validation | Cached-byte validation, fetch fallback | Tiles | `atlas_tiles` cache | KEEP (behavior) / REBUILD (impl) | P0 | needs-source-confirm |
| CAP-R07 | Bounded prefetch jobs | Prefetch with bounds | Tiles | `atlas_tiles` prefetch | EXTRACT | P0 | needs-source-confirm |

## Explicitly TBD (do not implement without confirmation)

- Default layer visibility values.
- Native zoom ceilings per provider (numeric).
- MGRS precision steps, ring radius steps, ruler unit defaults.
- Pin/waypoint encoding wire format.
- Camera persistence schema version.
- Hub/mesh packet formats (stay in adapters regardless).
- Any numeric golden values (see `acceptance-criteria.md`; fixtures are TBD).

## Maintenance rule

Adding a capability requires a new row with source citation. Removing or
reclassifying one requires an ADR. Silent deletion is forbidden by `AGENTS.md`.
