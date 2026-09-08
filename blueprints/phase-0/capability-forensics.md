# Phase 0.1 — Capability Forensics (Behavioral Traces)

Traces behavior end-to-end (user action → state → inputs → calculation →
geometry → result → failure). Evidence in `capability-evidence-matrix.md`;
sources in `source-trace-index.md`. All contracts derived here are
**proposed** (see `behavior-contracts.md`); nothing here is implemented.

## F-01 Radio-link tool (SRC-A) — the reference trace

```text
User taps LINK tool (toggleTool:916) with 2 measure points (measureCsv:430)
  → tap owns when armed (:537-540); 3rd tap restarts (addMeasurePoint:1516)
  → inputs: 2× LatLng, constants ANTENNA_M1.5 / WIFI_FREQ2.437GHz / K4.12 (:260-266)
  → calculation (linkReadout:1584): haversineKm (:1725, R6371) → horizon=4.12·(√1.5+√1.5)≈10.1km (:1588)
  → verdict ✓IN / ✗BEYOND (:1589); Fresnel FZ=8.657·√(km/2.437) (:1590)
  → rendered: shared MEASURE_LINE/PT amber dashed topmost (:2024-2043); readout string disclaims terrain: hub 3DEP (:1591)
  → failure: <2 pts → prompt; RULER↔LINK switch preserves segment (:913-915); offline works (client-side :246-248)
```

**Belongs in:** `atlas_geo` (haversine/bearing) + `atlas_tactical` radio model (smooth-earth half) with reserved terrain half in `atlas_terrain`/`atlas_analysis`. Never copy the string format blindly — contract it first (`ATLAS-GEO-LINK-001` proposed).

## F-02 Ruler + range rings (SRC-A)

```text
RULER: 2 taps → haversine + bearingDeg (:1733, atan2 0-359) → "RULER 850 M / 1.25 KM · BRG 042°" (:1574/:1595-1596, fmtDistKm:1595)
RINGS: press RING (:1040) cycles off→0.1→…→5→off (RING_STEPS:249-258, cycleRings:921-924)
  → center = ownFix else centerFix (:779-787); 4 concentric 65-pt circles, flat m/deg approx sub-pixel <10km (:1537-1541) + N/E/S/W spokes (:1555-1564)
  → layer beneath markers (:1937-1938); persists ringStepIdx (:431); null/negative → empty (:782)
```

Pure, golden-fixture-ready. Antimeridian/polar handling **Planned** (not observed).

## F-03 Camera persistence (SRC-A)

```text
getMapAsync (:486) → decodeCamera(cameraCsv:1620) else HOME 39.83/-98.58/z3.0 (:307-309)
  → move camera (:496-507); on camera-idle re-encode (:516-524, encodeCamera:1612)
  → guards: lat±90/lng±180/z0-24/tilt0-85, malformed→null→HOME (:1615-1630)
  → survives rotation/sector-switch (rememberSaveable:442); NOT disk persistence (onCreate(null):485)
```

Atlas must add versioned schema + disk store (Planned); serde shape itself is EXTRACT-ready.

## F-04 Parcels vs H3 authority (SRC-A)

```text
Surveyed (CAP-007): Hub export_parcel_geometry → ParcelGeometry.ringLngLat [lng,lat] → Polygon directly, no H3 decode (:2117-2122)
  → props parcelId/label/tier (:2128-2130); drawn ABOVE H3 (:1884-1885); empty when !online; runCatching→empty
Derived (CAP-008): SovereignGrid.cellToLatLng/cellBoundary/resolutionOf → GeoJSON (:2090/:2181)
  → patents vs sightings split by RESOLUTION_EMERGENCY (:696-704); acreage→true square (:2093-2107) else 0.02° inflated hex (ids untouched); polls 10s (:685-691); empty offline (:679-683)
```

Authority rule (surveyed > derived) is the KEEP-worthy invariant. `SovereignGrid` internals Inferred.

## F-05 Blueprints + migration (SRC-A)

```text
Blueprints: Hub export_blueprints (PLSS-anchored WGS84, never raw lat/lng/H3 :2136-2139) → dispatch polygon/line/point (:2147-2152), corrupt→skip (:2140-2141)
  → 1 source → Fill/Line/Circle trio (:1901-1923), minZoom 17 (:1908/1915/1922); LVL cycles sorted levels, stale→ALL (:927-929/:717-720); above parcels (:1901)
Flows: List<Pair<cellFrom,cellTo>> → center-to-center LineStrings (:2165-2172) → dashed LineLayer (:1925-1935)
```

## F-06 Pins / peers / SOS (SRC-A)

```text
Local pins: long-press → appendPin (:513/:1647, CSV lat|lng;… :1638) → PIN_SOURCE (:732-740) → phosphor diamonds (:1953-1990)
  → tap ≤56px lifts nearest via projection (:555/:1656, zoom/rotation-invariant :1652-1655); malformed CSV dropped (:1637-1645); rotation-only persist; works offline
Remote: loadPeerLocations (:377) poll → split SOS/waypoint/plain + fresh/stale 15min (:240/:745-756) → 4 sources → amber diamonds, stale 0.35 (:1978)
  → WPT cyan (named, :1953-1990), SOS red + halo 0.18/24px icon 56 (:1991-2006); tap → "id · dist·brg·age" (:1688-1722); share-pin fn (:836-847) + onShareWaypoint (:379)
```

Hit-test (`liftPinNear`/`describeShareNear`) is Map-coupled → REBUILD behind projection interface; CSV helpers EXTRACT.

## F-07 Offline boot + layer order + attribution (SRC-A)

```text
Boot: asset://map_style_dark.json (:150) + transport install (:468-483) → rasters visibility NONE (:1753-1754) → grid 15° always (:1842-1843) → "layers simply never paint" offline (:132-133)
Order (add-order = draw-order, :1747-1750/:1752-2044): SAT→TOPO→IMAGERY→OSM→DARK→USGS → GRID → PATENT→SIGHTING → REAL_PARCEL(above H3 :1884) → BLUEPRINT(above parcel :1901) → MIG → RINGS(beneath markers :1937-1938) → PIN/PEER/STALE/WPT/SOS/SELF → MEASURE topmost (:2024)
Attribution: native off (:487) → manual 8sp/0.55α (:1125-1150) only for visible public rasters Esri/OTM/USGS (:1130-1136); IMAGERY excluded (private :1012-1013)
```

## F-08 Location / compass / MGRS (SRC-A)

```text
Location: [LOC]-armed LocationManager GPS+NETWORK 2s (:595-626/:341, no fused :595-596), last-known seed (:620-623), provider-enabled guard
  → SELF dot+halo (:2007-2022, push :760-767); CTR → z14 else HOME z3 (:810-812); killed on disarm (:601-603); works offline
Compass: CompassEngine lifecycle (:628-654) → CompassRose (:1427-1503) counter-rotated, %03d° (:1497), UNRELIABLE dims (:1431-1433); tap=north (:820)
MGRS: crosshair passthrough (:948-949) + centerFix refresh (:516-524) → Mgrs.format×2 (:980/983); Mgrs impl Inferred
```

All three REBUILD behind `atlas_location` / orientation contracts; MGRS readout contract EXTRACT.

## F-09 Tile packing + transport (SRC-A, internals Inferred)

```text
Pack: PACK (:1063) → packRegion (:850-874): zoom coerce 3-14 (:853), TOPO always + SAT if on (:858-861), excl OSM/DARK/USGS (:854-857) → TilePack.prefetch(bounds…,zoom,layers,progress) → "PACKING n/m → COMPLETE·N TILES" (:863-872); runCatching→0
Transport: installMapLibreTransport + setConnected (:472-483) → pinned OkHttp + HybridTileInterceptor: P1 link-local Hub (pinned self-signed QR+SPKI, cleartext banned :474-476), P2 keyless public; IMAGERY no P2 (:174-176)
```

## F-10 Recovery cache-first + prefetch (SRC-B/C)

```text
Delivery (SRC-C :148-179): existsSync (:154) → decode validate instantiateImageCodec (:158) → corrupt→delete+refetch (:160-162) → miss→fetch (:168-169) → fail→1×1 transparent (:163/:170/:177/:183-184)
  → fetchAndCache (:79-94): UA com.recoveryforall (:37/:86), 12s timeout (:87), 200+non-empty (:88); writeAsBytes flush:true (:89), NOT atomic; key <docs>/mapcache/<layer>/{z}_{x}_{y}.png (:59-68); no TTL/LRU; SynchronousFuture key (:135-137, stale-forever risk)
  → DEAD-CODE FINDING: CachedTileProvider never instantiated in SRC-B; live TileLayers (:1129) use raw urlTemplate (network-only)
Prefetch (SRC-C :206-269 ← SRC-B :872-949): center+radiusKm → slippy ranges (111.32 km/deg :219-220, clamp :226-229) → truncate maxTiles=800 (:244/:255-257)
  → sequential await + 12ms gap (:266); skip-exists (:98); cancel isCancelled (:261); caller defects: only first active layer (:924), zooms 11-15 (:930), progress never repaints
```

Most core-worthy Recovery behavior; harden (atomic/TTL/LRU/concurrency/ToS/multi-layer) before reuse.

## F-11 Recovery location + meetings + weather (SRC-B)

```text
Location cascade (:126-201): service-off → Twin Cities 44.9778/-93.2650 (:136); deny → same (:152); <5min last-known (:157-169); medium 20s timeout (:172-175); stale fallback (:185-195)
  → recenter move(…,14) (:320); resetNorth (:323-327); GPS works offline; meeting list fails → _loadError (:211)
Meetings: findNearbyMeetings(radiusKm,upcomingOnly) (:219-224), radius 2mi/50max (:61/:92) → _rebuildMarkers (:970-1028) clustered, colors live/online/<24h/default (:955-964); attendance ±30min (:390) + 150m + !isMocked (:413/:427); user pin separate ValueKey (:978)
Weather: Open-Meteo current=temperature_2m,weather_code…fahrenheit (:287) → emoji (:301-309) → chip (:1173-1174); map-view only (:215/:1056); silent no-op offline (:291/:298)
```

Locator interface EXTRACT; meetings/weather REJECT from core (adapters).

## What was NOT found (Planned, do not cite as existing)

SharedPreferences/DataStore disk camera store; TilePack/HybridInterceptor/SecureHttp/SovereignGrid/Mgrs/CompassEngine internals; USGS-3DEP terrain LOS; DEM/contours; POI search; routing; scale bar; TTL/LRU/eviction; meeting-service internals.
