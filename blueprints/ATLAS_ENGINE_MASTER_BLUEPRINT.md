# ATLAS ENGINE — MASTER BLUEPRINT & PHASED ROADMAP

**Document:** Atlas Engine Master Blueprint
**Version:** 1.0 Draft
**Date:** 2026-09-07
**Foundation:** Sovereign Mantle `LandSectorView.kt` + Recovery for All TacMap port
**Primary objective:** Extract the proven TacMap capabilities into a reusable, modular, portable geospatial platform and build outward into a professional GIS + Historical Atlas + Tactical/Field mapping system.

---

# 0. EXECUTIVE MISSION

Atlas Engine is not a replacement map screen. It is the shared geospatial substrate beneath multiple applications.

```text
                 ATLAS ENGINE
                      │
         ┌────────────┼────────────┐
         │            │            │
      EXPLORE       ANALYZE      FIELD
         │            │            │
      Atlas App   GIS/Terrain   Tactical
         │            │            │
         └────────────┼────────────┘
                      │
          ┌───────────┼───────────┐
          │           │           │
    Sovereign      Recovery     Future
     Mantle         for All       Apps
```

The existing Sovereign Mantle map already demonstrates a substantial feature set: MapLibre rendering, offline/fail-secure boot, multiple raster sources, runtime vector layers, H3 heritage overlays, PLSS parcel geometry, blueprint overzoom, migration flows, pins, peer positions, SOS, live position, compass, MGRS, ruler, radio-link estimation, range rings, offline packing, hybrid transport, camera persistence and attribution handling. fileciteturn0file0L92-L113 fileciteturn10file1L640-L647

The Recovery for All project demonstrates the first Flutter extraction: `flutter_map`, layer toggles, location, meeting markers, weather, cache-first tile delivery and offline prefetch. fileciteturn7file0L1-L2 fileciteturn6file0L1-L2

The architecture below treats those implementations as the proven baseline, then deliberately separates core geospatial capabilities from application-specific logic.

---

# 0.1 NON-NEGOTIABLE ENGINE PRINCIPLES

- [ ] Renderer-independent geospatial core.
- [ ] Replaceable data providers.
- [ ] Declarative layer registry.
- [ ] Offline-first behavior by contract.
- [ ] No mandatory network dependency for base initialization.
- [ ] Application/domain logic stays outside the core engine.
- [ ] Provider licensing and attribution are explicit data, not UI afterthoughts.
- [ ] Provenance follows spatial data through transformations.
- [ ] Sensitive/private/local datasets require explicit sharing policy.
- [ ] Expensive operations support progress, cancellation and bounded resource use.
- [ ] Every major capability has deterministic unit tests around its pure logic.
- [ ] Casual, advanced and expert workflows use the same engine.
- [ ] A renderer migration must not require rewriting the geo engine.
- [ ] Failure modes are designed before network/API features are called complete.

---

# 0.2 MASTER PHASE MAP

| Phase | Name | Primary Outcome |
|---|---|---|
| 0 | Foundation & Extraction | Clean architectural seams and proven interfaces |
| 1 | Atlas Core | Common domain model, state, events and configuration |
| 2 | Basemap Matrix | Provider-driven raster/vector basemaps |
| 3 | Offline Atlas | Durable cache, packs, dataset manifests and offline mode |
| 4 | Geospatial Toolkit | Coordinates, grids, geometry, measurements and spatial math |
| 5 | Atlas Data | Boundaries, parcels, roads, buildings, hydrography and land datasets |
| 6 | Live Data | NOAA/USGS/environmental and event providers |
| 7 | Historical Atlas | Time-aware maps, boundaries, places and historical comparison |
| 8 | Terrain & 3D | DEM/elevation, terrain rendering and 3D groundwork |
| 9 | Visibility & RF | LOS, viewshed, terrain-aware radio analysis |
| 10 | Tactical / Field | Waypoints, tracks, routes, sharing, field workflows |
| 11 | Spatial Analysis | GIS operations, search, overlays, spatial queries |
| 12 | Plugin & Developer SDK | External providers, datasets and tools |
| 13 | Productization | Standalone Atlas, integrations, hardening and release |

---

# PHASE 0 — FOUNDATION, EXTRACTION & ARCHITECTURE

## Objective

Create a stable foundation before adding more feature surface. Preserve behavior while moving logic out of monolithic screens and into reusable services.

## Proven baseline

`LandSectorView.kt` is a large Compose/MapLibre screen that combines UI, lifecycle, permissions, map setup, layer management, geometry generation, tactical interaction and data loading. Its runtime stack is already structured around independently managed raster/vector sources. fileciteturn0file0L358-L380

The Recovery extraction blueprint explicitly identifies renderer-native code as the primary non-portable component and identifies the tile URL ladders, prefetch logic, layer toggles and overlay concepts as portable. fileciteturn4file0L1-L7

## Step 0.1 — Freeze the reference behavior

- [ ] Record every current TacMap capability.
- [ ] Record every visible interaction.
- [ ] Record every layer and source.
- [ ] Record default layer visibility.
- [ ] Record current native zoom ceilings.
- [ ] Record all offline/failure behavior.
- [ ] Record attribution requirements.
- [ ] Record current data contracts entering `LandSectorView`.
- [ ] Record current Recovery map behavior separately.
- [ ] Mark each capability `preserve`, `improve`, `replace`, or `future`.

## Step 0.2 — Identify pure logic

Extract and test first:

- [ ] Haversine distance.
- [ ] Bearing calculation.
- [ ] Range-ring geometry.
- [ ] Camera serialization/deserialization.
- [ ] Pin encoding/decoding.
- [ ] GeoJSON conversion.
- [ ] Blueprint feature conversion.
- [ ] Parcel geometry conversion.
- [ ] H3 geometry conversion.
- [ ] Migration segment generation.
- [ ] Measurement state transitions.
- [ ] Zoom and coordinate validation.

The existing implementation already treats many of these as pure helpers; for example, distance/bearing and camera state are separated from UI state enough to become straightforward extraction targets. fileciteturn10file0L454-L510

## Step 0.3 — Define architecture boundaries

Create these initial boundaries:

```text
atlas_core
atlas_geo
atlas_coordinates
atlas_layers
atlas_providers
atlas_tiles
atlas_map
atlas_location
atlas_compass
atlas_tactical
atlas_offline
atlas_data
atlas_security
```

- [ ] No core package imports application screens.
- [ ] Geo packages import no UI packages.
- [ ] Provider implementations depend on provider interfaces, not the other way around.
- [ ] Renderer adapters translate engine models into MapLibre/flutter_map/etc. models.
- [ ] Sovereign Mantle and Recovery code access Atlas through adapters.

## Step 0.4 — Define the renderer interface

Create an abstraction around:

- [ ] Map initialization.
- [ ] Camera state.
- [ ] Camera movement.
- [ ] Gesture state.
- [ ] Layer registration.
- [ ] Raster source registration.
- [ ] Vector source registration.
- [ ] Marker registration.
- [ ] Feature hit-testing.
- [ ] Style lifecycle.
- [ ] Render invalidation.

## Step 0.5 — Define the provider model

Create provider interfaces for:

- [ ] Raster tiles.
- [ ] Vector tiles.
- [ ] GeoJSON/vector datasets.
- [ ] Elevation.
- [ ] Weather.
- [ ] Historical maps.
- [ ] Administrative boundaries.
- [ ] Parcels/cadastre.
- [ ] Buildings/structures.
- [ ] User/local datasets.

## Step 0.6 — Define offline contract

- [ ] Offline boot works.
- [ ] Missing tiles do not crash the renderer.
- [ ] Cache corruption self-recovers.
- [ ] Provider failure does not erase existing cache.
- [ ] Offline packs have manifests.
- [ ] Provider-specific bulk-download rules are enforced.

The Recovery tile cache already validates locally cached bytes and falls back to fetching when the cache entry is invalid, which is a useful seed behavior. fileciteturn6file0L1-L2

## Phase 0 acceptance checklist

- [ ] Reference behavior captured.
- [ ] Core interfaces defined.
- [ ] Pure calculations extracted/tested.
- [ ] Layer registry design exists.
- [ ] Provider registry design exists.
- [ ] Renderer adapter exists.
- [ ] Offline contract is documented.
- [ ] Security/data-sensitivity model is defined.
- [ ] Atlas reference application can boot with no provider response.

---

# PHASE 1 — ATLAS CORE

## Objective

Build the domain model and state/event architecture that everything else depends on.

## 1.1 Core domain objects

Define:

```text
AtlasCoordinate
AtlasBounds
AtlasPoint
AtlasLine
AtlasPolygon
AtlasFeature
AtlasFeatureCollection
AtlasLayer
AtlasLayerGroup
AtlasMapState
AtlasCameraState
AtlasViewport
AtlasProviderDescriptor
AtlasDatasetDescriptor
AtlasProvenance
AtlasConfidence
AtlasSensitivity
```

Checklist:

- [ ] Immutable value objects where practical.
- [ ] Explicit units.
- [ ] Nullability rules defined.
- [ ] Serialization format defined.
- [ ] Version fields included where persistence is expected.
- [ ] No renderer-specific types leak into the objects.

## 1.2 Map state machine

Define states:

```text
UNINITIALIZED
BOOTING
READY_OFFLINE
READY_ONLINE
DEGRADED
ERROR_RECOVERABLE
DISPOSED
```

Checklist:

- [ ] State transitions are deterministic.
- [ ] Network loss does not destroy the map session.
- [ ] Provider failure transitions to degraded state rather than fatal state.
- [ ] UI can display state without inspecting provider internals.

## 1.3 Event bus / command model

Commands:

- [ ] `SetCamera`.
- [ ] `ToggleLayer`.
- [ ] `SelectFeature`.
- [ ] `DropWaypoint`.
- [ ] `StartMeasurement`.
- [ ] `CancelMeasurement`.
- [ ] `RequestLocation`.
- [ ] `DownloadPack`.
- [ ] `CancelPack`.
- [ ] `RefreshProvider`.

Events:

- [ ] `CameraChanged`.
- [ ] `LayerChanged`.
- [ ] `FeatureSelected`.
- [ ] `LocationUpdated`.
- [ ] `ProviderStateChanged`.
- [ ] `CacheUpdated`.
- [ ] `OfflinePackProgress`.

## 1.4 Configuration

Centralize:

- [ ] Default camera.
- [ ] Default layers.
- [ ] Zoom policies.
- [ ] Tile policies.
- [ ] Cache limits.
- [ ] User-agent/application identity.
- [ ] Attribution rules.
- [ ] Feature flags.

## Phase 1 exit checklist

- [ ] Core data model compiles independently of UI.
- [ ] State transitions tested.
- [ ] Event/command model tested.
- [ ] Configuration can construct a map session.
- [ ] Serialization round trips pass tests.

---

# PHASE 2 — BASEMAP MATRIX

## Objective

Turn basemaps from hard-coded branches into provider-defined, stackable layers.

The existing TacMap already has SAT, TOPO, OSM, DARK, USGS and local imagery source definitions, and it deliberately controls their native zoom ceilings. fileciteturn0file0L152-L160 fileciteturn0file0L314-L335

## 2.1 Provider registration

Create:

```text
RasterTileProvider
RasterStyleProvider
VectorTileProvider
LocalMbtilesProvider
```

Checklist:

- [ ] Provider has unique ID.
- [ ] Provider declares tile scheme.
- [ ] Provider declares supported zoom range.
- [ ] Provider declares attribution.
- [ ] Provider declares license metadata.
- [ ] Provider declares caching policy.
- [ ] Provider declares prefetch policy.
- [ ] Provider declares authentication requirements.
- [ ] Provider declares update/version metadata.

## 2.2 Basemap implementations

Start with the proven set:

- [ ] OpenStreetMap.
- [ ] Esri World Imagery.
- [ ] Esri light/gray.
- [ ] Esri dark/gray.
- [ ] OpenTopoMap.
- [ ] USGS source.
- [ ] Local MBTiles/orthophoto provider.

The Recovery port already implements OSM, satellite, dark/light and topographic templates and uses cache-backed delivery. fileciteturn7file0L1-L2

## 2.3 Layer stacking

Support:

```text
Base
 ↓
Raster overlays
 ↓
Hillshade
 ↓
Vector roads/buildings
 ↓
Boundaries
 ↓
User data
 ↓
Tactical overlays
 ↓
Selection/highlight
```

Checklist:

- [ ] Explicit z-order.
- [ ] Visibility independent of source state.
- [ ] Opacity independent of visibility.
- [ ] Min/max zoom per layer.
- [ ] Group visibility.
- [ ] Layer ordering persisted.
- [ ] Renderer rebuild does not mutate core state.

## 2.4 Basemap selector UX

Casual:

- [ ] Standard.
- [ ] Satellite.
- [ ] Topographic.
- [ ] Dark.

Advanced:

- [ ] Provider picker.
- [ ] opacity.
- [ ] layer stack.
- [ ] zoom constraints.
- [ ] attribution panel.

Expert:

- [ ] raw provider metadata.
- [ ] cache diagnostics.
- [ ] source URL diagnostics where allowed.
- [ ] tile status.

## Phase 2 exit checklist

- [ ] At least six provider/layer types function through the registry.
- [ ] Providers can be added without editing core map UI.
- [ ] Multiple layers stack correctly.
- [ ] Attribution is generated from active layers.
- [ ] Failure of one provider leaves other layers operational.

---

# PHASE 3 — OFFLINE ATLAS

## Objective

Turn the existing cache/prefetch behavior into a first-class offline data platform.

The Recovery implementation already uses filesystem caching and bounded prefetch jobs, while the original TacMap has a Hub-first/fallback model and a bundled offline grid. fileciteturn6file0L1-L2 fileciteturn0file0L121-L142

## 3.1 Tile cache engine

Implement:

- [ ] Tile key normalization.
- [ ] Provider namespace.
- [ ] Layer namespace.
- [ ] Zoom/x/y addressing.
- [ ] Cache lookup.
- [ ] Cache validation.
- [ ] Atomic writes.
- [ ] Expiration policy.
- [ ] LRU/size management.
- [ ] Corrupt-entry deletion.
- [ ] Disk statistics.

## 3.2 Tile pack format

Define:

```text
AtlasPack
├── manifest.json
├── provider metadata
├── layer metadata
├── bounds
├── zoom range
├── creation time
├── source versions
├── attribution
└── tile/data payloads
```

Checklist:

- [ ] Pack has unique ID.
- [ ] Pack has integrity hash.
- [ ] Pack is resumable.
- [ ] Pack download is cancellable.
- [ ] Pack reports progress.
- [ ] Pack can be deleted cleanly.
- [ ] Pack can be imported/exported where licensed.

## 3.3 Offline dataset packs

Go beyond raster tiles.

Support optional bundles containing:

- [ ] vector features.
- [ ] boundaries.
- [ ] elevation tiles.
- [ ] historical map tiles.
- [ ] POI indexes.
- [ ] local waypoints.
- [ ] search indexes.

## 3.4 Provider policy engine

Every provider declares:

```text
onlineAllowed
cacheAllowed
prefetchAllowed
maxTiles
requestRate
requiresKey
license
attribution
```

Checklist:

- [ ] OSM bulk-download guard is enforced.
- [ ] Provider rate limits are centralized.
- [ ] Users see estimated pack size.
- [ ] Provider restrictions are surfaced before download.

## 3.5 Offline UX

Create:

```text
Offline
Downloads
Saved Areas
Manage Storage
Provider Status
```

Checklist:

- [ ] Show what is available offline.
- [ ] Show stale data age.
- [ ] Show pack size.
- [ ] Show remaining storage.
- [ ] Allow individual pack deletion.
- [ ] Allow clearing provider cache without clearing user data.

## Phase 3 exit checklist

- [ ] Map boots offline.
- [ ] Cached tiles render offline.
- [ ] Corrupt tiles self-repair.
- [ ] Packs download with progress.
- [ ] Packs survive application restarts.
- [ ] Packs can be deleted.
- [ ] Provider policy is enforced.
- [ ] Offline diagnostics exist.

---

# PHASE 4 — GEOSPATIAL TOOLKIT

## Objective

Build the mathematical and coordinate foundation needed by every subsequent phase.

The existing TacMap already has great-circle distance, bearing, ring geometry, MGRS display, H3 geometry conversion and coordinate serialization patterns. fileciteturn10file0L423-L510 fileciteturn11file0L60-L73

## 4.1 Coordinate systems

Implement/abstract:

- [ ] WGS84 latitude/longitude.
- [ ] Decimal degrees.
- [ ] DMS.
- [ ] UTM.
- [ ] MGRS.
- [ ] Generic projected coordinate object.
- [ ] Datum metadata.
- [ ] Unit conversions.

## 4.2 Core geometry

- [ ] Great-circle distance.
- [ ] Rhumb-line distance where needed.
- [ ] Initial bearing.
- [ ] Destination point.
- [ ] Bounding box.
- [ ] Polygon area.
- [ ] Centroid.
- [ ] Point-in-polygon.
- [ ] Polyline length.
- [ ] Nearest point on line.
- [ ] Geometry validation.

## 4.3 Spatial operations

Plan interfaces for:

- [ ] Buffer.
- [ ] Intersect.
- [ ] Union.
- [ ] Difference.
- [ ] Clip.
- [ ] Simplify.
- [ ] Densify.
- [ ] Snap.
- [ ] Spatial join.

## 4.4 Measurement framework

Generalize current ruler/ring behavior:

- [ ] Point-to-point distance.
- [ ] Bearing.
- [ ] Area measurement.
- [ ] Perimeter.
- [ ] Radius ring.
- [ ] Coordinate readout.
- [ ] Elevation readout placeholder.

## 4.5 Grid engine

- [ ] Geographic graticule.
- [ ] UTM grid.
- [ ] MGRS grid.
- [ ] Custom grid.
- [ ] H3 index visualization.
- [ ] Grid density by zoom.

## 4.6 Numerical correctness

Checklist:

- [ ] Known-value tests.
- [ ] Antimeridian tests.
- [ ] Polar/near-polar edge tests.
- [ ] Negative coordinates.
- [ ] High-zoom precision tests.
- [ ] Unit conversion tests.
- [ ] Invalid coordinate rejection.
- [ ] Large-distance tests.

## Phase 4 exit checklist

- [ ] Coordinate engine is renderer-independent.
- [ ] MGRS and other coordinate conversions have tests.
- [ ] Measurement tools use shared geometry services.
- [ ] Spatial operations have a stable API, even where advanced implementations remain future work.

---

# PHASE 5 — ATLAS DATA LAYERS

## Objective

Build a normalized spatial-data catalog so Atlas can show much more than basemaps.

## 5.1 Administrative geography

Planned layer families:

- [ ] Countries.
- [ ] States/provinces.
- [ ] Counties.
- [ ] Municipalities.
- [ ] Townships.
- [ ] Census geography.
- [ ] School districts where useful.
- [ ] Special districts where useful.

## 5.2 Indigenous geography

Create an explicit dataset category for:

- [ ] Tribal boundaries.
- [ ] Reservations.
- [ ] Trust lands where suitable/public.
- [ ] Treaty areas.
- [ ] Historic territories where sourced.
- [ ] Indigenous place names.
- [ ] Historic Indigenous communities.
- [ ] Cultural/geographic reference layers where publication is appropriate.

Sensitive cultural data must carry a stronger access/sensitivity policy rather than being automatically public.

## 5.3 Cadastral / PLSS

Build:

- [ ] PLSS townships.
- [ ] Sections.
- [ ] Subsections where available.
- [ ] Survey corners.
- [ ] Parcel geometry.
- [ ] Parcel identifiers.
- [ ] Acreage.
- [ ] Source/provenance.
- [ ] Geometry confidence.

The current TacMap already distinguishes true surveyed parcel boundaries from H3-derived approximations, explicitly giving the surveyed geometry higher authority. fileciteturn10file1L781-L820

## 5.4 Transportation

- [ ] Streets.
- [ ] Highways.
- [ ] Trails.
- [ ] Railroads.
- [ ] Bridges.
- [ ] Airports.
- [ ] Transit where supported.

## 5.5 Hydrography

- [ ] Rivers.
- [ ] Streams.
- [ ] Lakes.
- [ ] Wetlands.
- [ ] Watersheds.
- [ ] Dams.
- [ ] Coastlines.

## 5.6 Structures

- [ ] Building footprints.
- [ ] Address points where available.
- [ ] Building type when sourced.
- [ ] Height/floor data where available.
- [ ] Structure identifiers.
- [ ] Local/private blueprints only when the user has valid data access.

The existing blueprint layer already follows geometry-type dispatch for polygon/line/point structural features and supports level filtering. fileciteturn11file0L19-L44

## 5.7 Land/environment

- [ ] Land cover.
- [ ] Land use.
- [ ] Protected areas.
- [ ] Parks.
- [ ] Forests.
- [ ] Geological units.
- [ ] Soil data.

## 5.8 Feature metadata

Every normalized feature should support:

```text
id
geometry
properties
source
sourceVersion
retrievedAt
license
confidence
accuracy
sensitivity
```

## Phase 5 exit checklist

- [ ] Common feature model supports heterogeneous datasets.
- [ ] Administrative, cadastral, transportation and hydrography layers can coexist.
- [ ] Provenance is visible in the Inspector.
- [ ] Layer data can be cached/offlined where licensing permits.
- [ ] Spatial queries can identify the administrative context of any point.

---

# PHASE 6 — LIVE DATA: NOAA / USGS / ENVIRONMENTAL

## Objective

Introduce live/periodically refreshed data through provider adapters instead of hard-coding APIs into map widgets.

## 6.1 Live provider architecture

```text
LiveProvider
├── authenticate()
├── capabilities()
├── query(bounds)
├── stream()/poll()
├── normalize()
├── attribution()
└── freshness()
```

## 6.2 NOAA family

Candidate categories to support through appropriate official data services:

- [ ] Weather conditions.
- [ ] Forecasts.
- [ ] Radar products.
- [ ] Alerts/warnings.
- [ ] Severe weather events.
- [ ] Storm tracks.
- [ ] Marine/weather products where useful.

## 6.3 USGS family

Candidate categories:

- [ ] Elevation datasets.
- [ ] Topographic products.
- [ ] Earthquake data.
- [ ] Water gauges.
- [ ] Streamflow/hydrology.
- [ ] Geographic reference datasets.

## 6.4 Other environmental/event layers

Design provider slots for:

- [ ] Wildfires.
- [ ] Air quality.
- [ ] Flood alerts.
- [ ] Lightning.
- [ ] Drought.
- [ ] Snow/ice.
- [ ] Hazard zones.

## 6.5 Freshness model

Every live layer should expose:

```text
LIVE
RECENT
STALE
OFFLINE-CACHED
UNAVAILABLE
```

Checklist:

- [ ] Timestamp every response.
- [ ] Display freshness threshold.
- [ ] Retain last-known data where appropriate.
- [ ] Never imply current data when it is stale.

## 6.6 Alert overlay UX

Casual:

- [ ] small status indicator.

Advanced:

- [ ] active alerts list.
- [ ] map extents.
- [ ] severity filters.

Expert:

- [ ] provider metadata.
- [ ] update timestamps.
- [ ] raw source reference.

## Phase 6 exit checklist

- [ ] Live providers are adapters.
- [ ] A provider can fail without taking down the map.
- [ ] Cached/stale status is explicit.
- [ ] Attribution and source timestamp are accessible.
- [ ] Refresh cadence is provider-aware.

---

# PHASE 7 — HISTORICAL ATLAS

## Objective

Turn time into a first-class map dimension.

## 7.1 Historical layer model

Define:

```text
HistoricalDataset
├── validFrom
├── validTo
├── sourceDate
├── mapDate
├── source
├── geographicExtent
├── confidence
└── provenance
```

## 7.2 Historical basemap catalog

Support eventual families such as:

- [ ] Historic topographic maps.
- [ ] Historic county maps.
- [ ] Historic city maps.
- [ ] Historic plat maps.
- [ ] Historic aerial imagery.
- [ ] Historic survey sheets.
- [ ] Historic transportation maps.

## 7.3 Timeline engine

Create:

```text
| 1800 | 1850 | 1900 | 1950 | 2000 | 2026 |
                         ▲
                      selected
```

Checklist:

- [ ] Timeline supports discrete dates.
- [ ] Timeline supports date ranges.
- [ ] Layers can define valid ranges.
- [ ] Map can blend historical and modern layers.
- [ ] Compare mode supports before/after.

## 7.4 Historical boundary analysis

- [ ] Historical municipality boundaries.
- [ ] County boundary changes.
- [ ] State/territorial changes.
- [ ] Reservation/boundary history where sourced.
- [ ] Former roads and railways.

## 7.5 Indigenous historical geography

- [ ] Indigenous place names through time.
- [ ] Historical settlement locations where sourced.
- [ ] Treaty geography.
- [ ] Historic territory references.
- [ ] Date-sensitive interpretation.
- [ ] Strong provenance display.

## 7.6 Historic-to-modern alignment

Provide tools:

- [ ] opacity slider.
- [ ] swipe comparison.
- [ ] blink comparison.
- [ ] registration/offset controls for imperfect historic scans.
- [ ] georeferencing metadata display.

## Phase 7 exit checklist

- [ ] Historical maps are a provider class.
- [ ] Timeline changes the active dataset state.
- [ ] Historical and current layers can coexist.
- [ ] Provenance/confidence is visible.
- [ ] Historical content does not masquerade as current geography.

---

# PHASE 8 — TERRAIN, ELEVATION & 3D

## Objective

Build a terrain engine that can power both visualization and later analytical capabilities.

## 8.1 Elevation provider

Define:

```text
ElevationProvider
├── elevation(point)
├── profile(line)
├── raster(bounds)
├── resolution()
├── verticalDatum()
└── provenance()
```

## 8.2 Terrain products

- [ ] Elevation raster.
- [ ] Hillshade.
- [ ] Contours.
- [ ] Slope.
- [ ] Aspect.
- [ ] Elevation profile.
- [ ] Terrain classification.

## 8.3 2.5D map

First implementation:

- [ ] Elevation-aware styling.
- [ ] Hillshade overlay.
- [ ] Contours.
- [ ] 3D-like terrain shading.
- [ ] Elevation inspector.

## 8.4 Full 3D groundwork

Define renderer-neutral objects for:

- [ ] Terrain mesh.
- [ ] Tile-based terrain chunks.
- [ ] Camera pitch.
- [ ] Vertical scale.
- [ ] 3D markers.
- [ ] 3D routes.
- [ ] 3D footprints.
- [ ] Occlusion.

## 8.5 Building visualization

Support when data exists:

- [ ] footprint extrusion.
- [ ] height metadata.
- [ ] floor count.
- [ ] structural overlays.
- [ ] indoor/blueprint transition.

## 8.6 Deep zoom strategy

Preserve the existing idea that vector detail can extend beyond raster native resolution. The current TacMap permits camera zoom through 24 for deep vector overlays while individual raster providers declare lower native ceilings. fileciteturn0file0L314-L336

Checklist:

- [ ] Native raster ceiling stored per provider.
- [ ] Vector deep-zoom ceiling independent of raster ceiling.
- [ ] Overzoom warning available.
- [ ] Data-resolution indicators available.

## Phase 8 exit checklist

- [ ] Elevation provider works.
- [ ] Hillshade/contours work.
- [ ] Elevation profile works.
- [ ] Terrain state can be consumed by analysis engine.
- [ ] 3D renderer boundary is defined.
- [ ] Prototype terrain view exists.

---

# PHASE 9 — VISIBILITY, LOS & RADIO ANALYSIS

## Objective

Turn the current radio-link estimate into a real terrain-aware analysis subsystem.

The current TacMap deliberately reports only smooth-earth radio horizon and Fresnel-zone information on-device and explicitly leaves terrain occlusion to the Hub/3DEP path. fileciteturn10file0L463-L477

## 9.1 Analysis inputs

Define:

```text
ObserverPoint
TargetPoint
ObserverHeight
TargetHeight
Frequency
AntennaParameters
AtmosphereModel
TerrainModel
```

## 9.2 Straight line of sight

- [ ] Sample terrain along path.
- [ ] Compare ray elevation to terrain elevation.
- [ ] Detect first obstruction.
- [ ] Calculate obstruction distance.
- [ ] Return confidence/resolution metadata.

## 9.3 Viewshed

- [ ] Observer point.
- [ ] radius.
- [ ] angular resolution.
- [ ] terrain source.
- [ ] visible/blocked classification.
- [ ] output raster/vector layer.

## 9.4 Radio model

Start with current concepts:

- [ ] smooth-earth horizon.
- [ ] Fresnel-zone radius.

Expand later:

- [ ] terrain clearance.
- [ ] antenna height.
- [ ] frequency.
- [ ] curvature/refraction model.
- [ ] diffraction/obstruction indicators.
- [ ] optional link-budget model.

## 9.5 Result semantics

Never return only `true/false`.

Return:

```text
CLEAR
OBSTRUCTED
BEYOND_HORIZON
INSUFFICIENT_DATA
LOW_RESOLUTION
OFFLINE_UNAVAILABLE
```

Include:

- [ ] distance.
- [ ] obstruction point.
- [ ] terrain source.
- [ ] sample resolution.
- [ ] model used.
- [ ] timestamp.

## 9.6 Visualization

- [ ] LOS line.
- [ ] terrain profile.
- [ ] obstruction marker.
- [ ] Fresnel visualization.
- [ ] viewshed heatmap.
- [ ] 3D ray/path.

## Phase 9 exit checklist

- [ ] Terrain-aware LOS works on known test terrain.
- [ ] Radio analysis distinguishes model assumptions.
- [ ] Missing terrain data does not produce false certainty.
- [ ] Results are visualized and inspectable.
- [ ] Analysis is callable by Sovereign Mantle without embedding the calculation in the map UI.

---

# PHASE 10 — TACTICAL / FIELD ENGINE

## Objective

Promote the proven TacMap tactical capabilities into a general field-mapping subsystem without requiring Sovereign Mantle to be installed.

Current capabilities include dropped pins, peer shares, SOS markers, location tracking, waypoint sharing, range rings, MGRS, ruler and radio-link tools. fileciteturn0file0L222-L267

## 10.1 Waypoints

Create:

```text
Waypoint
├── id
├── coordinate
├── label
├── icon
├── createdAt
├── modifiedAt
├── notes
├── attachments
├── source
└── sharingPolicy
```

Checklist:

- [ ] Create.
- [ ] Edit.
- [ ] Move.
- [ ] Delete.
- [ ] Duplicate.
- [ ] Export.
- [ ] Import.
- [ ] Share explicitly.

## 10.2 Tracks

- [ ] Start/stop recording.
- [ ] Pause/resume.
- [ ] Track smoothing.
- [ ] Distance.
- [ ] Time.
- [ ] Elevation profile when available.
- [ ] GPX import/export.
- [ ] Privacy controls.

## 10.3 Routes

- [ ] Manual route drawing.
- [ ] Waypoint sequencing.
- [ ] Route distance.
- [ ] Bearing.
- [ ] Terrain profile.
- [ ] Optional routing provider later.

## 10.4 MGRS / field grid

- [ ] MGRS readout.
- [ ] Grid overlay.
- [ ] Precision selector.
- [ ] Coordinate copy/share.
- [ ] Crosshair.

## 10.5 Field measurement

- [ ] Ruler.
- [ ] Area.
- [ ] Range rings.
- [ ] Bearing.
- [ ] Elevation.
- [ ] Profile.

The current range-ring engine uses selectable radius steps and cardinal spokes; that behavior becomes the reference for a generalized measurement API. fileciteturn10file0L423-L451

## 10.6 Location sharing

Separate:

```text
Local position
vs.
Position share
vs.
Persistent tracking
```

Checklist:

- [ ] Explicit opt-in.
- [ ] Sharing state visible.
- [ ] Expiration/TTL.
- [ ] Recipient scope.
- [ ] Stop-sharing control.
- [ ] Local audit record where appropriate.

## 10.7 Peer map

Genericize the existing concept of:

- [ ] peer position.
- [ ] named waypoint.
- [ ] stale position.
- [ ] SOS/emergency marker.

Peer transport remains an adapter.

## 10.8 Mesh integration boundary

Sovereign Mantle-specific mesh functionality becomes:

```text
AtlasTransportAdapter
        │
        ├── Internet
        ├── Local network
        ├── Mesh
        ├── Hub
        └── Future transports
```

Atlas should understand a position-share packet, but it should not require a specific mesh implementation.

## Phase 10 exit checklist

- [ ] Waypoints persist.
- [ ] Tracks persist.
- [ ] Routes persist.
- [ ] MGRS works.
- [ ] Measurements work.
- [ ] Sharing is explicit and controllable.
- [ ] Tactical calculations are outside UI code.
- [ ] Sovereign Mantle can supply its mesh transport through an adapter.

---

# PHASE 11 — SPATIAL ANALYSIS WORKSPACE

## Objective

Transform Atlas from a viewing tool into a true analysis platform.

## 11.1 Analysis workspace

Create a mode with:

```text
SELECT
MEASURE
QUERY
BUFFER
COMPARE
OVERLAY
PROFILE
INTERSECT
EXPORT
```

## 11.2 Spatial query

Support questions such as:

- [ ] What municipality contains this point?
- [ ] Which county contains this parcel?
- [ ] Which waterways are within X distance?
- [ ] Which parcels intersect this polygon?
- [ ] Which historical layers cover this area?
- [ ] Which features are visible from this point?

## 11.3 Buffer analysis

- [ ] Buffer point.
- [ ] Buffer line.
- [ ] Buffer polygon.
- [ ] configurable units.
- [ ] intersect buffer with chosen layers.

## 11.4 Overlay analysis

- [ ] Layer intersection.
- [ ] Difference.
- [ ] union.
- [ ] spatial join.
- [ ] nearest-feature lookup.

## 11.5 Heatmaps / density

- [ ] point density.
- [ ] kernel density where appropriate.
- [ ] time-filtered density.
- [ ] category filtering.

## 11.6 Temporal analysis

Combine Phase 7 with spatial analysis:

- [ ] Show feature changes through time.
- [ ] Compare boundaries.
- [ ] Compare road networks.
- [ ] Compare settlement patterns.
- [ ] Compare historical land records where legally/data-wise appropriate.

## 11.7 Inspector

Every selected feature should expose:

```text
IDENTITY
GEOMETRY
COORDINATES
SOURCE
PROVENANCE
CONFIDENCE
ATTRIBUTION
HISTORY
RELATED DATA
```

The Inspector becomes the bridge between map visualization and evidence/data context.

## 11.8 Export

Plan:

- [ ] GeoJSON.
- [ ] KML/KMZ where supported.
- [ ] GPX for tracks/routes.
- [ ] CSV for tabular points.
- [ ] PNG/JPEG map snapshot.
- [ ] PDF/report later.

## Phase 11 exit checklist

- [ ] Spatial queries work.
- [ ] Buffers work.
- [ ] Feature intersection works.
- [ ] Inspector explains provenance.
- [ ] Analysis outputs can become map layers.
- [ ] Results can be exported.

---

# PHASE 12 — PLUGIN & DEVELOPER SDK

## Objective

Make the engine extensible without modifying core code for every new provider or capability.

## 12.1 Plugin categories

```text
Provider Plugin
Layer Plugin
Analysis Plugin
Renderer Plugin
Dataset Plugin
Tool Plugin
Inspector Plugin
Export Plugin
```

## 12.2 Plugin manifest

Define fields:

```text
id
name
version
engineVersion
capabilities
permissions
license
attribution
networkAccess
storageAccess
locationAccess
sharingAccess
```

## 12.3 Permissions model

A plugin should declare whether it can:

- [ ] read network.
- [ ] store files.
- [ ] read location.
- [ ] read user datasets.
- [ ] publish/share data.
- [ ] access local/private sources.

## 12.4 Provider SDK

Document:

- [ ] tile provider API.
- [ ] vector provider API.
- [ ] elevation provider API.
- [ ] live provider API.
- [ ] historical provider API.
- [ ] authentication adapter.
- [ ] attribution API.
- [ ] caching API.

## 12.5 Dataset SDK

Allow a dataset plugin to provide:

- [ ] schema.
- [ ] spatial extent.
- [ ] temporal extent.
- [ ] feature types.
- [ ] styling metadata.
- [ ] provenance.
- [ ] update mechanism.

## 12.6 Developer tools

Create an Atlas DevTools app or panel exposing:

- [ ] provider registry.
- [ ] layer graph.
- [ ] tile request inspector.
- [ ] cache inspector.
- [ ] geometry inspector.
- [ ] performance stats.
- [ ] renderer diagnostics.
- [ ] spatial query debugger.

## Phase 12 exit checklist

- [ ] A provider can be added without core modification.
- [ ] A custom dataset can render through standard interfaces.
- [ ] Plugins have permissions.
- [ ] Plugins can be disabled cleanly.
- [ ] Engine remains usable without third-party plugins.

---

# PHASE 13 — PRODUCTIZATION & RELEASE

## Objective

Turn Atlas Engine into the standalone application and integrate it back into the existing projects.

## 13.1 Standalone Atlas application

Primary navigation:

```text
EXPLORE
ATLAS
FIELD
ANALYSIS
HISTORY
DATA
SETTINGS
```

## 13.2 Explore mode

Designed for normal users:

- [ ] Current location.
- [ ] Search.
- [ ] Standard map.
- [ ] Satellite.
- [ ] Topo.
- [ ] landmarks.
- [ ] place details.
- [ ] directions handoff.

## 13.3 Atlas mode

Designed for deeper geographic exploration:

- [ ] layer catalog.
- [ ] historical timeline.
- [ ] boundaries.
- [ ] parcels.
- [ ] place names.
- [ ] data inspector.

## 13.4 Field mode

- [ ] MGRS.
- [ ] Compass.
- [ ] Waypoints.
- [ ] Tracks.
- [ ] Routes.
- [ ] Offline packs.
- [ ] Measurement.
- [ ] Terrain/LOS.

## 13.5 Analysis mode

- [ ] Query.
- [ ] Buffer.
- [ ] Intersect.
- [ ] Profile.
- [ ] Viewshed.
- [ ] Temporal compare.
- [ ] Export.

## 13.6 Settings

- [ ] Providers.
- [ ] Layers.
- [ ] Offline storage.
- [ ] Privacy.
- [ ] Location permissions.
- [ ] Sharing permissions.
- [ ] Units.
- [ ] Coordinate system.
- [ ] Appearance.
- [ ] Advanced diagnostics.

## 13.7 Sovereign Mantle integration

Replace duplicated map capability with Atlas adapters:

```text
Sovereign Mantle
├── ancestry data adapter
├── land/parcel adapter
├── migration adapter
├── blueprint adapter
├── mesh transport adapter
└── Atlas map UI
```

Preserve Mantle-specific semantics in adapters rather than in Atlas core.

## 13.8 Recovery for All integration

Replace duplicated map infrastructure with:

```text
Recovery for All
├── meeting provider
├── recovery-specific markers
├── recovery location workflow
└── Atlas map UI
```

The project's existing extraction blueprint already anticipates the eventual standalone SDK direction, with current P0–P3 map behavior becoming an adapter over the future TacMap SDK. fileciteturn4file0L2-L7

## 13.9 Testing matrix

### Unit

- [ ] coordinate tests.
- [ ] geometry tests.
- [ ] provider normalization tests.
- [ ] cache tests.
- [ ] pack tests.
- [ ] state-machine tests.

### Integration

- [ ] provider → layer.
- [ ] layer → renderer.
- [ ] offline → online transition.
- [ ] location → tactical state.
- [ ] terrain → LOS.
- [ ] historical → timeline.

### Device

- [ ] cold start.
- [ ] rotation/resume.
- [ ] permission denial.
- [ ] GPS unavailable.
- [ ] network loss.
- [ ] storage full.
- [ ] corrupted cache.
- [ ] low-memory behavior.
- [ ] long map sessions.

### Accessibility/usability

- [ ] touch targets.
- [ ] screen readers.
- [ ] contrast.
- [ ] text scaling.
- [ ] error messaging.
- [ ] casual mode simplicity.

## 13.10 Performance targets

Define benchmarks rather than guessing.

- [ ] map boot time.
- [ ] first visible frame.
- [ ] pan latency.
- [ ] layer-toggle latency.
- [ ] feature query latency.
- [ ] offline tile throughput.
- [ ] memory use at deep zoom.
- [ ] 3D frame performance.
- [ ] analysis completion time.

## 13.11 Release hardening

- [ ] crash reporting.
- [ ] structured logs.
- [ ] provider health diagnostics.
- [ ] data migration strategy.
- [ ] cache migration strategy.
- [ ] API versioning.
- [ ] plugin compatibility policy.
- [ ] licensing review.
- [ ] privacy review.
- [ ] security review.
- [ ] documentation.

## Phase 13 exit checklist

- [ ] Standalone Atlas app is functional.
- [ ] Core map capabilities are shared rather than duplicated.
- [ ] Recovery consumes Atlas services.
- [ ] Sovereign Mantle consumes Atlas services.
- [ ] Offline mode is stable.
- [ ] Core providers are production-tested.
- [ ] Advanced tools are accessible without overwhelming casual users.
- [ ] Release documentation exists.

---

# MASTER FEATURE INVENTORY

## Basemap / Rendering

- [ ] OSM.
- [ ] Esri satellite.
- [ ] Esri light.
- [ ] Esri dark.
- [ ] Topographic.
- [ ] USGS.
- [ ] Local imagery.
- [ ] Vector tiles.
- [ ] Raster overlays.
- [ ] Dynamic layer ordering.
- [ ] Deep vector zoom.
- [ ] Attribution.

## Navigation

- [ ] Pan.
- [ ] Pinch zoom.
- [ ] Rotate.
- [ ] Tilt.
- [ ] Compass.
- [ ] Recenter.
- [ ] Search.
- [ ] Coordinate jump.
- [ ] Camera persistence.

## Coordinates

- [ ] Lat/lon.
- [ ] DMS.
- [ ] UTM.
- [ ] MGRS.
- [ ] H3.
- [ ] Custom grids.

## Geospatial

- [ ] Distance.
- [ ] Bearing.
- [ ] Area.
- [ ] Perimeter.
- [ ] Buffer.
- [ ] Intersect.
- [ ] Union.
- [ ] Difference.
- [ ] Spatial query.
- [ ] Spatial join.

## Data

- [ ] Roads.
- [ ] Buildings.
- [ ] Water.
- [ ] Trails.
- [ ] Boundaries.
- [ ] Tribal geography.
- [ ] PLSS.
- [ ] Parcels.
- [ ] Elevation.
- [ ] Land cover.
- [ ] Geological layers.

## Live

- [ ] Weather.
- [ ] Radar.
- [ ] Alerts.
- [ ] Earthquakes.
- [ ] Water gauges.
- [ ] Fire.
- [ ] Flood.
- [ ] Environmental data.

## Historical

- [ ] Historic maps.
- [ ] Aerial imagery.
- [ ] Historic boundaries.
- [ ] Indigenous place names.
- [ ] Treaties.
- [ ] Historic transportation.
- [ ] Timeline.
- [ ] Swipe compare.

## Terrain

- [ ] DEM.
- [ ] Hillshade.
- [ ] Contours.
- [ ] Slope.
- [ ] Aspect.
- [ ] Elevation profile.
- [ ] 2.5D.
- [ ] 3D.

## Tactical / Field

- [ ] Waypoints.
- [ ] Tracks.
- [ ] Routes.
- [ ] MGRS.
- [ ] Grid.
- [ ] Range rings.
- [ ] Ruler.
- [ ] LOS.
- [ ] Viewshed.
- [ ] Radio LOS.
- [ ] Peer position.
- [ ] SOS.
- [ ] Sharing.
- [ ] Offline packs.

## Developer

- [ ] Provider SDK.
- [ ] Dataset SDK.
- [ ] Plugin SDK.
- [ ] Layer SDK.
- [ ] Analysis SDK.
- [ ] DevTools.
- [ ] Diagnostics.

---

# MASTER DATA FLOW

```text
                  EXTERNAL / LOCAL DATA
                           │
          ┌────────────────┼────────────────┐
          │                │                │
       PROVIDERS        IMPORTERS         LOCAL
          │                │                │
          └────────────────┼────────────────┘
                           ▼
                    NORMALIZATION
                           │
                           ▼
                  PROVENANCE + POLICY
                           │
                           ▼
                     DATA ENGINE
                           │
                 ┌─────────┴─────────┐
                 ▼                   ▼
            CACHE/OFFLINE       LIVE STATE
                 │                   │
                 └─────────┬─────────┘
                           ▼
                      LAYER ENGINE
                           │
                           ▼
                      GEO ENGINE
                           │
                           ▼
                    RENDERER ADAPTER
                           │
                           ▼
                          MAP
```

---

# MASTER ZOOM / RESOLUTION MODEL

The following is a design model, not a fixed provider requirement. Providers and datasets should advertise their own real resolution and native limits.

```text
Z0–5    Global / continent
Z6–9    Region / state
Z10–13  County / municipality / regional geography
Z14–16  Street / parcel context
Z17–19  Property / structure
Z20–24  Deep vector / survey / blueprint detail
```

Checklist:

- [ ] Zoom visibility is data-driven.
- [ ] Raster native resolution is preserved.
- [ ] Vector layers may extend beyond raster limits.
- [ ] UI warns when the view is overzoomed.
- [ ] Dataset resolution is shown in Inspector.

---

# MASTER UX MODEL

## Casual

```text
SEARCH
MY LOCATION
MAP
LAYERS
PLACE DETAILS
```

## Advanced

```text
LAYERS
COORDINATES
MEASURE
TERRAIN
HISTORY
DATA
OFFLINE
```

## Expert

```text
PROVIDERS
SPATIAL ANALYSIS
MGRS/UTM
VIEWSHED
RADIO
CACHE
DATASETS
PROVENANCE
DIAGNOSTICS
PLUGIN TOOLS
```

Rule:

> Advanced functionality is accessible, not hidden; it is progressively disclosed so the initial interface remains understandable.

---

# MASTER SECURITY / PRIVACY MODEL

## Data classes

```text
PUBLIC
COMMUNITY
PRIVATE
LOCAL_ONLY
SENSITIVE
```

## Required controls

- [ ] Explicit sharing state.
- [ ] Provider permissions.
- [ ] Dataset sensitivity metadata.
- [ ] Local-only providers cannot silently fall back to public endpoints.
- [ ] Private imagery never receives an automatic public upload/fallback path.
- [ ] Offline data can be encrypted where required.
- [ ] User-created tracks/locations have clear retention controls.
- [ ] Logs avoid unnecessary precise-location exposure.

---

# MASTER PROVENANCE MODEL

```text
AtlasProvenance
├── provider
├── dataset
├── datasetVersion
├── retrievedAt
├── sourceDate
├── license
├── geometryAccuracy
├── positionalConfidence
├── transformationHistory
└── notes
```

Every transformation that materially changes geometry should be able to record:

- [ ] source geometry.
- [ ] transformation used.
- [ ] output geometry.
- [ ] confidence/accuracy effect.

This is especially important for cadastral, historic and derived layers.

---

# MASTER TEST STRATEGY

## Golden data

Create immutable test fixtures for:

- [ ] known coordinates.
- [ ] known MGRS values.
- [ ] known distances.
- [ ] known bearings.
- [ ] known parcel polygons.
- [ ] known building polygons.
- [ ] known historical extents.
- [ ] known terrain profiles.
- [ ] known LOS obstruction cases.

## Regression policy

- [ ] Existing Sovereign Mantle behavior gets a regression test before extraction.
- [ ] Recovery behavior gets regression coverage before replacing its implementation.
- [ ] Any engine refactor must demonstrate preserved outputs before adding unrelated new behavior.

---

# MASTER PERFORMANCE STRATEGY

## Rendering

- [ ] Avoid unnecessary layer rebuilds.
- [ ] Batch feature updates.
- [ ] Use zoom-aware simplification.
- [ ] Use clustering for dense point datasets.
- [ ] Avoid generating unnecessary geometry every frame.

## Data

- [ ] Cache normalized datasets.
- [ ] Index spatial data.
- [ ] Paginate large queries.
- [ ] Bound memory usage.
- [ ] Cancel abandoned analysis.

## Offline

- [ ] Estimate pack size before download.
- [ ] Resume interrupted downloads.
- [ ] Avoid duplicate tiles.
- [ ] Compress where appropriate.

## 3D

- [ ] Tile terrain.
- [ ] Level-of-detail.
- [ ] Frustum culling.
- [ ] Mesh simplification.
- [ ] Texture streaming.

---

# MASTER DEVELOPMENT ORDER

Do not implement phases as isolated vertical silos. The recommended dependency order is:

```text
PHASE 0
  │
  ▼
PHASE 1 ───────► Core domain/state
  │
  ├────────────► PHASE 2 Basemaps
  │
  ├────────────► PHASE 3 Offline
  │
  └────────────► PHASE 4 Geo
                    │
                    ▼
                 PHASE 5 Data
                    │
           ┌────────┴────────┐
           ▼                 ▼
       PHASE 6           PHASE 7
       Live              History
           │                 │
           └────────┬────────┘
                    ▼
                 PHASE 8
                 Terrain
                    │
                    ▼
                 PHASE 9
                 LOS/RF
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
      PHASE 10             PHASE 11
      Tactical             Analysis
          │                   │
          └─────────┬─────────┘
                    ▼
                 PHASE 12
                 Plugins
                    │
                    ▼
                 PHASE 13
               Productization
```

---

# MASTER DEFINITION OF DONE

Atlas Engine is considered production-ready only when all of the following are true:

- [ ] The engine can initialize without a network.
- [ ] Basemaps are provider-driven.
- [ ] Multiple layers can stack.
- [ ] Offline packs work.
- [ ] Core coordinate systems work.
- [ ] Spatial measurements work.
- [ ] Major public data classes have normalized provider interfaces.
- [ ] Historical data is time-aware.
- [ ] Elevation is available through a provider abstraction.
- [ ] Terrain analysis is functional.
- [ ] Tactical capabilities are reusable outside Sovereign Mantle.
- [ ] Analysis results are inspectable and exportable.
- [ ] Provenance follows data.
- [ ] Sensitivity/sharing policy follows data.
- [ ] Recovery for All consumes the shared mapping infrastructure.
- [ ] Sovereign Mantle consumes the shared mapping infrastructure.
- [ ] Standalone Atlas is the reference consumer.
- [ ] Renderer-specific implementation is replaceable.
- [ ] Test coverage exists for core geospatial logic.
- [ ] Performance and failure behavior are documented.

---

# FIRST IMPLEMENTATION BACKLOG

This is the recommended first actual coding sequence.

## Sprint A — Establish repository

- [ ] Create `atlas-engine` repository.
- [ ] Create monorepo/workspace skeleton.
- [ ] Add CI.
- [ ] Add formatting/linting.
- [ ] Add unit-test harness.
- [ ] Add architecture decision record folder.
- [ ] Add `README.md`.
- [ ] Add this blueprint.

## Sprint B — Extract pure logic

- [ ] Coordinate model.
- [ ] Distance.
- [ ] Bearing.
- [ ] Camera state.
- [ ] Pin state.
- [ ] Range rings.
- [ ] GeoJSON conversion abstractions.

## Sprint C — Layer/provider interfaces

- [ ] `AtlasLayer`.
- [ ] `AtlasProvider`.
- [ ] `RasterTileProvider`.
- [ ] `VectorDataProvider`.
- [ ] `LocalDatasetProvider`.
- [ ] Attribution metadata.
- [ ] Provenance metadata.

## Sprint D — Tile engine

- [ ] Cache.
- [ ] Tile key.
- [ ] Provider policy.
- [ ] Prefetch.
- [ ] Pack manifest.
- [ ] Offline status.

## Sprint E — Reference Atlas map

- [ ] OSM.
- [ ] satellite.
- [ ] topo.
- [ ] dark/light.
- [ ] layer stack.
- [ ] location.
- [ ] MGRS.
- [ ] ruler.
- [ ] rings.

## Sprint F — First data layers

- [ ] administrative boundaries.
- [ ] parcels/PLSS.
- [ ] roads.
- [ ] buildings.
- [ ] water.

## Sprint G — Terrain proof

- [ ] DEM.
- [ ] hillshade.
- [ ] profile.
- [ ] terrain LOS.

## Sprint H — Historical proof

- [ ] first historical map provider.
- [ ] timeline.
- [ ] opacity comparison.

## Sprint I — Integration

- [ ] Recovery adapter.
- [ ] Sovereign Mantle adapter.
- [ ] eliminate duplicate map infrastructure where practical.

---

# ARCHITECTURAL NORTH STAR

The original TacMap should be remembered as the **prototype that proved the capabilities**.

Recovery for All is the **portability experiment that proved those capabilities can cross platforms**.

Atlas Engine becomes the **generalized platform that makes those capabilities reusable**.

The final relationship is:

```text
                  ATLAS ENGINE
                       │
        ┌──────────────┼──────────────┐
        │              │              │
      ATLAS        SOVEREIGN       RECOVERY
     STANDALONE     MANTLE          FOR ALL
        │              │              │
        ▼              ▼              ▼
     General       Genealogy       Recovery
     Mapping       / Land /       / Meetings
     + GIS         Mesh           / Community
        │              │              │
        └──────────────┼──────────────┘
                       │
                  Shared spatial
                    capabilities
```

The ultimate goal is that a new map capability is implemented **once** in Atlas Engine and then made available to every consuming application that needs it.

---

# CHANGE CONTROL

When a new feature is proposed, answer these questions before coding:

1. Is this a core geospatial capability, a provider, an application feature, or a UI feature?
2. Does it belong in Atlas Engine or an adapter?
3. Does it require a new interface or fit an existing one?
4. What is its offline behavior?
5. What is its provenance model?
6. What is its sensitivity/sharing model?
7. What are its test fixtures?
8. What happens when its provider fails?
9. What happens at maximum zoom/data density?
10. Can another Atlas consumer use the capability without importing application-specific code?

A feature should not enter the core merely because it appears visually on the map.

---

# CURRENT REFERENCE IMPLEMENTATION NOTES

The original TacMap already has a strong conceptual layer order: optional raster sources, an offline graticule, heritage/H3 vectors, real parcel boundaries, blueprint structures, migration flows, range rings, interactive markers and measurement overlays. fileciteturn10file1L644-L647 fileciteturn10file1L739-L849

It also already treats blueprint geometry as direct WGS84 coordinates resolved against parcel context and carries feature metadata such as ID, kind, level and label. fileciteturn11file0L19-L44

These behaviors should be treated as extraction requirements where they are still desired, not discarded during the Atlas redesign.

---

# END STATE

Atlas Engine is complete when it feels like:

> **A modern digital map, a professional GIS workstation, a field navigation tool, and a living historical atlas sharing one underlying spatial engine.**

It should be simple enough to open and immediately understand, but deep enough that an expert can spend hours inside the layer stack, data inspector, historical timeline, terrain engine and analysis workspace without hitting an artificial ceiling.
