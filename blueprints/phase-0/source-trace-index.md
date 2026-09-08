# Phase 0.1 — Source Trace Index

- **Status:** Forensic inspection completed 2026-09-08. Read-only; no source files copied into Atlas.
- **Rule:** All three sources are **external** to this repository (not vendored, not git-tracked in Atlas). Evidence cites absolute local path + line. Do not treat external line numbers as stable API.

## 1. Sources inspected

| ID | Path | Size | Lines | Method |
|---|---|---|---|---|
| SRC-A | `C:\sovereign_mantle\android_node\app\src\main\java\com\sovereign\mantle\LandSectorView.kt` | 108,237 B | ~2097 (Measure-Object) / 2220 (Get-Content count; CRLF discrepancy noted, not material) | Full forensic scan: imports, 6 `@Composable`, 30+ top-level helpers, all `rememberSaveable`/`remember` state, all `installRuntimeLayers` branches, grep for MapLibre/Style/Raster/GeoJson/camera/MGRS/haversine/ring/radio/H3/parcel/blueprint/migration/pin/peer/SOS/pack/Hub/attribution. |
| SRC-B | `C:\Recovery for All\lib\screens\meeting_map_screen.dart` | 52,818 B | 1440 | Full read + grep for flutter_map/TileLayer/urlTemplate/cache/prefetch/Geolocator/meeting/weather/attribution/zoom. |
| SRC-C | `C:\Recovery for All\lib\services\map_tile_cache.dart` | 8,959 B | 270 | Full read + grep for http/File/path_provider/TTL/LRU. |

## 2. Coverage

- SRC-A: renderer imports (`:92-113`), constants (`:121-266`), state (`:396-470`), map lifecycle (`:470-530`), location (`:595-626`), compass lifecycle (`:628-654`), data polls (`:656-730`), layer pushes (`:732-787`), visibility toggles (`:792-805`), actions (`:808-934`), UI stack (`:936-1259`), helpers (`:1516-1733`), layer install (`:1752-2044`), converters (`:2090-2193`). All CAP-001..027 traced to at least one symbol.
- SRC-B: widget/state (`:25-69`), location cascade (`:126-201`), filters/fetch (`:218-275`), weather (`:281-309`), recenter/north (`:315-338`), attendance (`:404-457`), prefetch caller (`:872-949`), markers (`:955-1028`), map stack (`:1116-1169`). All CAP-R01..R05 traced.
- SRC-C: static cache (`:32-101`), `CachedTileProvider` (`:104-118`), image provider (`:120-198`), `TilePrefetch` (`:202-270`). CAP-R06..R07 traced.

## 3. Explicit gaps (not in inspected files — Planned/TBD)

- SRC-A imports but does not implement: `TilePack`, `HybridTileScheme`/`HybridTileInterceptor`, `SecureHttpClientProvider`, `SovereignGrid`, `Mgrs`, `CompassEngine`, `ParcelGeometry`/`BlueprintFeature` producers, USGS-3DEP terrain. Their internals are **Inferred** at best from call sites.
- No `SharedPreferences`/`DataStore`/disk camera store in SRC-A (`onCreate(null):485`; only `rememberSaveable` strings).
- No TTL/LRU/eviction/size-cap in SRC-B/SRC-C (grep zero hits for TTL/LRU/expiration).
- No POI search, routing/nav, DEM/contour rendering, scale bar in any of the three files.
- `meeting_finder_service.dart` (meeting cache) and Mantle node/Hub services are out of scope for this pass — cited as TBD where behavior crosses the file boundary.

## 4. Confidence vocabulary (used in all Phase 0.1 docs)

- **Verified** — directly demonstrated by a cited line in SRC-A/B/C.
- **Inferred** — strong architectural inference from a call site whose implementation lives outside the three files.
- **Planned** — desired Atlas capability with no implementation in the three files. Must not be cited as historical fact.

## 5. How to cite

- External evidence: `SRC-A LandSectorView.kt:1612` (or full path on first use per document).
- Atlas docs: `capability-evidence-matrix.md CAP-002` / `behavior-contracts.md ATLAS-GEO-RING-001 (proposed)`.
- Never copy source code into Atlas docs beyond short identifier names. No license-bearing blocks reproduced.
