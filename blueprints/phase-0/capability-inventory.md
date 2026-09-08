# Phase 0.3 — Capability Inventory

- **Status:** Forensic pass 2026-09-08 applied. Sources inspected externally
  (not vendored — see `docs/architecture/SOURCE-MATERIAL.md` and
  `blueprints/phase-0/source-trace-index.md`).
- **Rule:** Every entry preserves the blueprint's claim without inventing
  paths, values, or line numbers. Unverified fields are `TBD`.
- **Confirmation values:** `needs-source-confirm` → `source-confirmed (external <SRC-ID>, <date>)`
  (external = on-disk source outside Atlas repo) → `source-confirmed (vendored)` (future).
- **Evidence detail:** `capability-evidence-matrix.md`. **Traces:** `capability-forensics.md`.
  **Proposed contracts:** `behavior-contracts.md` (none implemented).

## Disposition vocabulary (see `extraction-matrix.md`)

- `KEEP` — architecture already sound; preserve design.
- `EXTRACT` — portable logic belongs in Atlas.
- `REBUILD` — platform-specific implementation; carry the concept, rewrite the code.
- `REJECT / REPLACE` — valid for the original app, not appropriate for a general engine.

## Reference capabilities — Sovereign Mantle TacMap family

Evidence: **SRC-A** = `C:\sovereign_mantle\android_node\app\src\main\java\com\sovereign\mantle\LandSectorView.kt` (inspected 2026-09-08).

| ID | Name | Observed behavior (with evidence) | Classification | Atlas destination | Disposition | Priority | Confirmation |
|---|---|---|---|---|---|---|---|
| CAP-001 | MapLibre rendering | Hosts `MapView:470` in `AndroidView:946`; runtime layers over bundled style (`setStyle:564`, `installRuntimeLayers:1752`); imports `:92-113` | Renderer | `atlas_map` renderer adapter | REBUILD | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-002 | Camera pose persistence | `lat\|lng\|zoom\|bearing\|tilt` serde (`encodeCamera:1612`, `decodeCamera:1620`, `CameraFraming:1603`); HOME `39.83/-98.58/z3.0:307-309`; guards `lat±90,lng±180,z0-24,tilt0-85` | Map state | `atlas_map` camera / `atlas_core` state | EXTRACT | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-003 | Fail-secure offline boot | Bundled `asset://map_style_dark.json:150`; rasters `visibility NONE:1753-1754`; 15° graticule `GRID_SOURCE:1842-1843`; "simply never paint" `:132-133` | Offline boot | `atlas_map` + `atlas_offline` | KEEP (concept) / REBUILD (impl) | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-004 | Independent raster layers | 6 rasters via `hybrid-*://` (`:156-161`); consts `:163-172`; `IMAGERY:180-181`; sources `:1755-1840`; OSM default-on but empty offline `:392-395`; imagery Hub-only `:174-176` | Layer / provider | `atlas_layers` + `atlas_provider_api` + `atlas_tiles` | EXTRACT (model) / REBUILD (wiring) | P0 | source-confirmed (external SRC-A, 2026-09-08); interceptor internals Inferred |
| CAP-005 | Per-provider native zoom ceilings | `TOPO17/SAT19/OSM19/DARK20/USGS16:318-322`, `IMAGERY22:332`, `CAMERA_MAX24:336`; `TileSet.maxZoom` per source; overzoom-not-404 `:314-317` | Zoom policy | `atlas_layers` policy + `atlas_provider_api` descriptor | EXTRACT | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-006 | Blueprint overzoom / deep vector zoom | Vectors past raster ceilings; blueprint trio `minZoom 17:1908/1915/1922` | Zoom policy | `atlas_layers` + `atlas_map` | KEEP (concept) | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-007 | Parcel geometry (surveyed) | `ringLngLat`→`Polygon` direct, no H3 decode `:2117-2122`; above H3 `:1884-1885`; props `parcelId/label/tier:2128-2130`; empty when `!online` | Data | `atlas_data` | EXTRACT | P0 | source-confirmed (external SRC-A, 2026-09-08); producer Inferred |
| CAP-008 | H3 heritage overlays | `SovereignGrid`→GeoJSON (`:2090/:2181`); patents vs sightings split `:696-704`; acreage→square `:2093-2107` else `0.02°` floor `:351-355`; poll `10s`; empty offline `:679-683` | Data / grid | `atlas_data` + `atlas_geo` grids | EXTRACT | P1 | source-confirmed (external SRC-A, 2026-09-08); `SovereignGrid` impl Inferred |
| CAP-009 | Blueprint structures | WGS84 PLSS-anchored `:2136-2139`; polygon/line/point dispatch `:2147-2152`; corrupt→skip `:2140-2141`; level cycle wraps→ALL `:927-929` | Data | `atlas_data` | EXTRACT | P1 | source-confirmed (external SRC-A, 2026-09-08); resolver Inferred |
| CAP-010 | Migration flows | Cell pairs → center `LineString:2165-2172`; dashed layer `:1925-1935` | Data / analysis | `atlas_data` (reserved: `atlas_analysis`) | EXTRACT | P2 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-011 | Tactical pins / waypoints | Local phosphor diamonds; CSV `lat\|lng;…` (`:1638/:1647`); 56px projection lift `:1656` (zoom-invariant `:1652-1655`); remote named→cyan WPT; share `:836-847` | Tactical | `atlas_tactical` waypoints | EXTRACT (codec) / REBUILD (hit-test) | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-012 | Live peers | Split SOS/WPT/plain + fresh/stale 15min `:240/:745-756`; tap `id · dist·brg·age:1710-1722`; stale `0.35:1978` | Tactical | `atlas_tactical` peer map (transport = adapter) | EXTRACT (model) / REBUILD (transport) | P1 | source-confirmed (external SRC-A, 2026-09-08); service Inferred |
| CAP-013 | SOS / emergency markers | `emergency==true` → red + halo `0.18/24px` icon 56 (`:1991-2006`); tap `SOS <code>:1711` | Tactical | `atlas_tactical` | EXTRACT (model) / REBUILD (transport) | P1 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-014 | Live position + tracking | `LocationManager` GPS+NETWORK 2s, no fused `:595-596`; seed `:620-623`; dot+halo `:2007-2022`; CTR→z14 else HOME z3 `:810-812` | Location | `atlas_location` | EXTRACT (model) / REBUILD (impl) | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-015 | Compass / heading | `CompassEngine` lifecycle `:628-654`; `CompassRose:1427-1503`; `%03d°:1497`; UNRELIABLE dims `:1431-1433` | Location | `atlas_location` compass/heading | REBUILD | P1 | source-confirmed (external SRC-A, 2026-09-08); engine impl Inferred |
| CAP-016 | MGRS readout / grid | Crosshair passthrough `:948-949`; `Mgrs.format×2:980/983`; `mgrsOn:432` default on | Tactical / geo | `atlas_tactical` mgrs + `atlas_geo` grids | EXTRACT (readout contract) | P0 | source-confirmed (external SRC-A, 2026-09-08); `Mgrs` impl Inferred; precision steps still TBD |
| CAP-017 | Ruler (distance + bearing) | 2 taps → `haversineKm:1725` (R6371) + `bearingDeg:1733`; `RULER 850 M / 1.25 KM · BRG 042°:1574/1595-1596`; client-side `:246-248` | Measurement | `atlas_geo` measurements | EXTRACT | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-018 | Range rings | Steps `0.1-5.0`, 4/step `:249-258`; 65-pt circles, flat approx `:1537-1541`; spokes `:1555-1564`; fix-else-crosshair `:779-787` | Measurement | `atlas_geo` measurements | EXTRACT | P1 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-019 | Radio-link estimation | `K4.12`, 1.5m, 2.437GHz `:260-266`; horizon≈10.1km `:1588`; `✓IN/✗BEYOND:1589`; FZ `8.657·√(km/2.437):1590`; terrain disclaimer `:1591`, NOT modelled `:260-263` | Tactical / analysis | `atlas_tactical` radio (reserved: `atlas_analysis`, `atlas_terrain`) | KEEP (scope boundary) | P1 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-020 | Tile packing / offline packs | `packRegion:850-874` (zoom 3-14, TOPO always + SAT-if-on, excl others); `TilePack.prefetch` → `PACKING n/m → COMPLETE·N TILES` | Offline | `atlas_offline` + `atlas_tiles` | EXTRACT (semantics) / REBUILD (impl) | P0 | source-confirmed call site (external SRC-A, 2026-09-08); `TilePack` internals Inferred |
| CAP-021 | Hub-first / fallback transport | Pinned OkHttp + interceptor `:472-483`; P1 Hub (QR+SPKI, no cleartext `:474-476`), P2 public; IMAGERY no P2 | Transport | `integrations/sovereign_mantle` adapter (not core) | REJECT from core / REBUILD as adapter | P1 | source-confirmed wiring (external SRC-A, 2026-09-08); interceptor internals Inferred |
| CAP-022 | Attribution handling | Native off `:487`; manual `8sp/0.55α:1125-1150` for visible public rasters `:1130-1136`; IMAGERY excluded `:1012-1013` | Provider | `atlas_provider_api` + `atlas_layers` | EXTRACT (rule KEEP) | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-023 | GeoJSON conversion | ~15 `GeoJsonSource` (`:1853/1869/1886/1903/1927/1939/1955…`); pushes `:694-787`; bitmap icons, no glyph fetch `:1951-1952` | Data | `atlas_data` geojson | EXTRACT (model) / REBUILD (wiring) | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-024 | Parcel/H3/blueprint conversions | Acres→m²→deg centered `:2087-2088`; M/KM format `:1595`; haversine R6371; bearing atan2 0-359 | Data | `atlas_data` | EXTRACT | P1 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-025 | Measurement state transitions | Armed ordinal + ≤2-pt CSV `:426-430`; 3rd tap restarts `:1516`; RULER↔LINK preserves `:913-915` | Map interaction | `atlas_map` interaction + `atlas_geo` | EXTRACT | P1 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-026 | Zoom/coordinate validation | Camera guards `:1615-1630`; pins drop malformed `:1637-1645`; blueprint skip `:2140-2141`; level→ALL `:717-720` | Geo | `atlas_geo` | EXTRACT | P0 | source-confirmed (external SRC-A, 2026-09-08) |
| CAP-027 | Layer ordering / visibility defaults | Fixed stack `:1747-1750/:1752-2044` (rasters < grid < heritage < real-parcel < blueprint < flows < rings < markers < measure); `setLayerVisibility:2077` | Layer | `atlas_layers` | EXTRACT (model); defaults per-toggle observed `:396-407` | P0 | source-confirmed (external SRC-A, 2026-09-08) |

## Reference capabilities — Recovery for All port family

Evidence: **SRC-B** = `C:\Recovery for All\lib\screens\meeting_map_screen.dart`,
**SRC-C** = `C:\Recovery for All\lib\services\map_tile_cache.dart` (inspected 2026-09-08).

| ID | Name | Observed behavior (with evidence) | Classification | Atlas destination | Disposition | Priority | Confirmation |
|---|---|---|---|---|---|---|---|
| CAP-R01 | flutter_map host | `MapController:51`, `FlutterMap:1120-1121`, `MapOptions:1122-1125` (z12 fix / z9 fallback); stack `:1127-1136`; `MarkerLayer:1137-1139`; cluster `:1140-1164` | Renderer | `atlas_map` renderer adapter | REBUILD (stack/camera model EXTRACT) | P0 | source-confirmed (external SRC-B, 2026-09-08) |
| CAP-R02 | Basemap templates (OSM/sat/dark/light/topo) | 5 UI layers (`_availableLayers:71-83`) vs 4 cache templates (`_templates:43-51` SRC-C; `osm` missing → falls back to `dark:71`); Esri `{z}/{y}/{x}`, OSM/OTM `{z}/{x}/{y}.png`; attribution OSM-only (Esri/OTM gap `:1165-1169`) | Provider | `atlas_provider_api` + `atlas_tiles` | EXTRACT (table; fix gaps, do not copy defects) | P0 | source-confirmed (external SRC-B/C, 2026-09-08) |
| CAP-R03 | Layer toggles | Multi-select stackable (`_activeLayers:69`, chips `:740-762`); first = base; cannot deselect last (`:756`); `keepBuffer` 2/1 `:1135` (overdraw) | Layer | `atlas_layers` (adapter UI) | KEEP in adapter; core consumes ordered list | P0 | source-confirmed (external SRC-B, 2026-09-08) |
| CAP-R04 | Location + meeting markers | GPS cascade → Twin Cities `(44.9778,-93.2650):136` on service-off/deny; <5min last-known; `medium` 20s timeout; recenter `move(…,14):320`; meetings clustered w/ live/online/<24h colors; attendance ±30min + 150m + `!isMocked` | Location / app | `atlas_location` (locator); meetings → `integrations/recovery_for_all` adapter | EXTRACT (locator iface) / REJECT meetings from core | P1 | source-confirmed (external SRC-B, 2026-09-08); service internals TBD |
| CAP-R05 | Weather overlay hooks | Keyless Open-Meteo `temperature_2m,weather_code…fahrenheit:287` → emoji chip `:1173-1174`; silent no-op offline `:291/:298` | Live provider | Reserved live-provider slot (Phase 6) | REJECT from Phase 0 core | P2 | source-confirmed (external SRC-B, 2026-09-08) |
| CAP-R06 | Filesystem tile cache + validation | Lookup `existsSync:154` → decode-validate `:158` → corrupt→delete+refetch `:160-162` → miss→fetch `:168-169` → fail→transparent `:183-184`; UA `com.recoveryforall`, 12s timeout, 200/non-empty gate; write NOT atomic; no TTL/LRU; `CachedTileProvider` never instantiated in SRC-B (dead code) | Tiles | `atlas_tiles` cache | EXTRACT + harden (atomic/TTL/LRU/wiring) | P0 | source-confirmed (external SRC-C, 2026-09-08) |
| CAP-R07 | Bounded prefetch jobs | Slippy ranges (`111.32 km/deg`, clamp); `maxTiles=800`; sequential `await` + 12ms gap `:266`; skip-exists; cancel flag; caller: zooms 11-15, first-layer-only, progress never repaints | Tiles | `atlas_tiles` prefetch | EXTRACT (math) / REBUILD (runner) | P0 | source-confirmed (external SRC-B/C, 2026-09-08) |

## Resolved TBDs (this pass)

- Zoom ceilings numeric (CAP-005), ring steps (CAP-018), HOME coords (CAP-002), radio constants (CAP-019),
  layer z-order (CAP-027), attribution rule incl. IMAGERY exclusion (CAP-022), cache key scheme
  `<docs>/mapcache/<layer>/{z}_{x}_{y}.png` (CAP-R06), prefetch bounds `maxTiles=800`, zooms, sequentiality (CAP-R07).

## Still TBD (do not implement without a fixture/spec ADR)

- `SovereignGrid` / `Mgrs` / `CompassEngine` / `TilePack` / `HybridTileScheme` internals.
- Meeting-service internals; Hub/mesh packet formats (adapters regardless).
- MGRS precision steps; datum/projection edge cases; antimeridian/polar behavior.
- All numeric golden vectors (fixture format ADR pending).
- Workspace/CI harness (intentionally deferred).

## Maintenance rule

Adding a capability requires a new row with source citation. Removing or
reclassifying one requires an ADR. Silent deletion is forbidden by `AGENTS.md`.
Evidence states use Verified / Inferred / Planned (`source-trace-index.md` §4).
