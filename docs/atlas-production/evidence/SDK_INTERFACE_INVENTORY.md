# SDK Interface Inventory & Seam Mapping (WO-01)

Date: 2026-10-14
Architect: Principal Software & SDK Architect
Reference Spec: `blueprints/atlas-production/specs/SDK_CONTRACT.md`

## 1. Pure Domain Layer (Contract Tier)
Zero imports of `android.*`, `androidx.*`, `org.maplibre.*`, or `kotlinx.coroutines.android`.
All types are immutable data classes, sealed interfaces, and pure calculation engines.

### 1.1 Core & Coordinates (`com.sovereignatlas.atlas.core`, `com.sovereignatlas.atlas.geo`)
- `AtlasCoordinate(val latitude: Double, val longitude: Double, val altitudeMeters: Double? = null)`
- `AtlasBoundingBox(val south: Double, val west: Double, val north: Double, val east: Double)`
- `AtlasGeoMath`: Haversine distance, initial bearing, destination point, cross-track distance.
- `CompassMath`: Angle normalization [0, 360), minimal angular delta, cardinal/intercardinal string formatting.
- `MgrsConverter` & `MgrsEngine`: WGS84 to/from Military Grid Reference System (MGRS), dynamic grid line generation by zoom.

### 1.2 Camera & Navigation State (`com.sovereignatlas.atlas.camera`, `com.sovereignatlas.atlas.goto`)
- `AtlasCameraState(val center: AtlasCoordinate, val zoom: Double, val bearing: Double, val pitch: Double)`
- `GoToState`: Target bearing, distance, off-course indicator, target arrival threshold.

### 1.3 Layers & Feature Rendering Abstraction (`com.sovereignatlas.atlas.geo.render`, `com.sovereignatlas.atlas.layers`)
- `AtlasLayer`: Sealed type identifying all system layers (Basemap, Graticule, MGRS, Waypoints, Tracks, Measures, Rings, CotMarkers, TacticalGraphics, HistoricalPatents, HistoricalBlueprints).
- `FeatureSink`: Pure renderer-agnostic interface for pushing geometry and properties.
  - `fun push(layer: AtlasLayer, feature: RenderFeature)`
  - `fun replace(layer: AtlasLayer, features: List<RenderFeature>)`
  - `fun clear(layer: AtlasLayer)`

### 1.4 Measurement & Field Tools (`com.sovereignatlas.atlas.measure`, `com.sovereignatlas.atlas.field`)
- `MeasureState`: Multi-point geodesic measurement, cumulative distance, segment bearings, polygon area.
- `MeasureUnit`: METRIC (m/km), IMPERIAL (ft/mi), NAUTICAL (nm).
- `JournalJson`: Geo-tagged field notes serialization.

### 1.5 Track & GPX (`com.sovereignatlas.atlas.track`)
- `TrackRecorder`: Time and distance thresholded GPS position sampling, active track accumulator.
- `GpxExport`: Standard GPX 1.1 schema generator.

### 1.6 Offline Policies & Contracts (`com.sovereignatlas.atlas.offline`)
- `LocalTileServer`: Pure contract for loopback tile serving without exposing sockets or HTTP.
- `OfflineMapDownloader`: Pure contract for downloading bounding box tile prisms with rate limiting and timeouts.
- `OfflinePackRecord`, `OfflineProviderDescriptor`: Metadata and manifests.

### 1.7 Tactical Analysis (`com.sovereignatlas.atlas.tactical`, `com.sovereignatlas.atlas.geo.los`)
- `Geofence`: Radial and polygonal geofencing with containment math.
- `RadioLink`: Free-space and knife-edge RF path loss estimation.
- `LineOfSightEngine`: Elevation-based ray casting and Fresnel zone clearance.

---

## 2. Platform Adapter Tier (`com.sovereignatlas.atlas.android.*`, `com.sovereignatlas.atlas.map.*`)
Platform implementations that wire pure contracts to Android hardware and MapLibre:
- `MapLibreFeatureSink`: Converts `RenderFeature` to `org.maplibre.geojson.Feature` and updates MapLibre `GeoJsonSource`.
- `AndroidLocationSource`: FusedLocationProvider / LocationManager to `AtlasLocation`.
- `AndroidHeadingSource`: SensorManager rotation vector to `AtlasHeading`.
- `PackTileServer`: Local loopback `ServerSocket` server in `android.offline`.
- `AndroidHttpDownloader`: `HttpURLConnection` transport implementing pure `OfflineMapDownloader`.
