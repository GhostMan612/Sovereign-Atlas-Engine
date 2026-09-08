# SOURCE-MATERIAL — Reference Implementations and Provenance

- **Status:** Phase 0 reference index. Behavioral summary only.
- **Master blueprint:** `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md` v1.0 Draft dated 2026-09-07.
- **Rule:** Do not fabricate source paths, line numbers, or citations.
  Details not verified against vendored source are marked `TBD`.

## 1. What is and is not in this repository

- No Sovereign Mantle source files are vendored in this repository.
- No Recovery for All source files are vendored in this repository.
- `integrations/sovereign_mantle/` and `integrations/recovery_for_all/`
  contain only directory placeholders.
- The master blueprint contains embedded citations of the form
  `filecite turnNfileM` (e.g. Executive Mission, Phase 0–2, Phase 8–10).
  Those citations reference a prior ChatGPT analysis session whose source
  files are **not present** in this repository and cannot be resolved here.
  Treat every such citation as **unverified** until the referenced file is
  vendored or re-exported into `integrations/` with a dated note.

## 2. Sovereign Mantle TacMap (primary behavioral reference)

- **Role:** Proven prototype whose capabilities Atlas Engine generalizes.
- **Known identifier from the blueprint:** a large Compose/MapLibre screen
  conventionally called `LandSectorView.kt`. Exact repository path: `TBD`
  (not vendored; do not cite a path).
- **Runtime characterization per the blueprint (unverified against source,
  preserve as requirements to confirm):**
  - MapLibre-based rendering with independently managed raster/vector
    sources.
  - Basemap/source families including satellite, topo, OSM-family, dark,
    USGS-family, and local imagery; per-provider native zoom ceilings with
    camera zoom to ~24 for deep vector overlays.
  - Fail-secure / offline-first boot from a bundled style with an offline
    graticule fallback; missing tiles must not crash the renderer.
  - Runtime vector layers: heritage/H3 overlays, surveyed parcel
    boundaries distinguished from H3-derived approximations (surveyed
    geometry has higher authority), blueprint structures with
    geometry-type dispatch (polygon/line/point) and level filtering,
    migration-flow segments.
  - Interaction: dropped pins, peer positions, SOS/emergency markers, live
    position, compass, MGRS readout, ruler (distance + bearing), radio-link
    estimation reporting smooth-earth radio horizon and Fresnel-zone
    information (terrain occlusion explicitly out of scope on-device per
    the blueprint), range rings with selectable radius steps and cardinal
    spokes.
  - Data/logistics: offline tile packing, Hub-first/fallback transport
    model, camera persistence (lat/lng/zoom/bearing/tilt serialization),
    attribution handling.
  - Tactical sharing: waypoint/position shares with explicit scope; mesh
    transport is host-app specific and becomes an Atlas transport adapter.
- **Confirmation status:** All of the above is `TBD (blueprint-reported,
  source not vendored)`. Do not convert any item into an Atlas contract
  without a `capability-inventory.md` entry marked `needs-source-confirm`.

## 3. Recovery for All map port (portability reference)

- **Role:** First Flutter portability experiment proving TacMap concepts
  can cross platforms.
- **Known identifiers from the blueprint:** files conventionally called
  `meeting_map_screen.dart` and `map_tile_cache.dart`, plus an extraction
  blueprint anticipating a standalone SDK direction. Exact paths: `TBD`
  (not vendored; do not cite a path).
- **Behavioral characterization per the blueprint (unverified, TBD):**
  `flutter_map` rendering, OSM/satellite/dark/light/topo templates,
  layer toggles, location display, meeting markers, weather overlay hooks,
  filesystem-backed tile cache with local byte validation and fetch
  fallback, bounded prefetch jobs.
- **Portability lesson recorded in the blueprint:** renderer-native code
  is the primary non-portable component; tile URL ladders, prefetch
  logic, layer toggles, and overlay concepts are portable. This is the
  basis for `blueprints/phase-0/portability-matrix.md`.
- **Confirmation status:** `TBD (blueprint-reported, source not vendored)`.

## 4. How to cite sources in this repo

- If the file is vendored under `integrations/`, `datasets/`, or
  `docs/`, cite `path:line`.
- If it is not vendored, cite this document plus the blueprint section,
  e.g. `SOURCE-MATERIAL.md §2; master blueprint Phase 0`, and mark the
  claim `TBD`.
- Never reproduce `filecite turnNfileM` strings as if they were repository
  citations. They are session-local artifacts, not source evidence.
- When source is added, update the corresponding
  `capability-inventory.md` entry from `needs-source-confirm` to
  `source-confirmed` with the vendored path and date.

## 5. What Phase 0 needs next

1. Vendor or re-export the minimum reference excerpts needed to confirm
   `capability-inventory.md` (preferred), or record a dated architect
   waiver for each unconfirmed capability.
2. Keep Mantle-specific semantics (ancestry, mesh, Hub) in
   `integrations/sovereign_mantle/` adapters, never in `packages/`.
3. Keep Recovery-specific semantics (meetings, recovery workflows) in
   `integrations/recovery_for_all/` adapters, never in `packages/`.
