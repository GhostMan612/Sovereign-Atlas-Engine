# BP-01: Modular Map & Screen Architecture (Zero-Monolith Blueprint)

> **Status:** EXECUTED & VERIFIED (2026-10-23)  
> **Target:** `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/map/`  
> **Issue Addressed:** `AtlasMap.kt` previously contained 3,618 lines with `AtlasMapScreen` exceeding Android ART runtime JIT instruction limits (16,956 instructions). The function body was deconstructed into modular composables, dropping bytecode sizes well under compiler thresholds.

---

## 1. Architectural Problem Statement

The single largest technical debt in the user interface was `AtlasMap.kt`. At 3,618 lines, it performed six conflicting responsibilities within one file and predominantly within a single `@Composable fun AtlasMapScreen`:
1. **MapLibre View Lifecycle & Camera Management:** SurfaceView creation, camera movement, heading rotation, and state persistence.
2. **Feature Synchronization:** Pushing 14+ distinct GeoJSON sources (GPS puck, MGRS graticule, GoTo lines, range rings, LoS rays, CoT markers, PLI tracks, tactical graphics, historical dossiers).
3. **Style & Layer Blending:** Ingesting 12 raster providers, offline MBTiles packs, local loopback tile servers, and historical blueprints into MapLibre styles.
4. **HUD & Telemetry Overlays:** CompassTape, scale bar, attribution pill, GPS acquisition pill, overscaling badge, activity banner, and loading overlay.
5. **Interactive Sheets & Dialog Routing:** Layers dialog, settings sheet, track recorder dialog, waypoint sheet, target action sheet, link estimation, fence editor, and Sovereign Command Center.
6. **Gesture & Event Routing:** Map clicks, long-press coordinate picking, feature query detection, drawing tool taps, and back-button intercepts.

### ART Runtime Consequence
Android 12–16 ART JIT compilation halts when a single bytecode method exceeds compilation limits. In device logcat on the Moto G:
```text
I eignatlas.atlas: Method exceeds compiler instruction limit: 16956 in void com.sovereignatlas.atlas.map.AtlasMapKt.AtlasMapScreen(...)
```
This forces Android ART to run `AtlasMapScreen` through the interpreter rather than native JIT-optimized machine code.

---

## 2. Landed Decomposition Architecture

Decomposed `AtlasMap.kt` and `AtlasMapScreen` into cohesive, decoupled Kotlin source files within `com.sovereignatlas.atlas.map`:

```text
com.sovereignatlas.atlas.map/
├── AtlasMap.kt              (Root coordinator: MapView lifecycle, state bindings, & child composable assembly)
├── AtlasMapControls.kt      (Rail pills, workspace chips, tool rows)
├── AtlasMapOverlays.kt      (Top HUD, CompassTape, activity banner, telemetry pills, instrument HUD, attribution)
├── AtlasGoToPanel.kt        (GoTo routing calculation card & measure panel hosts)
├── AtlasMapDialogsHost.kt   (Command center sheet, layer manager sheet, target drop sheet, dialogs host)
├── MapFeatureSync.kt        (MapLibre FeatureSink and GeoJSON layer pushing functions)
└── MapStyleSync.kt          (Base templates, raster compositor blending, MBTiles pack sync)
com.sovereignatlas.atlas.ui.hud/
└── NavigationGuidanceHud.kt (Turn-by-turn road navigation guidance HUD)
```

---

## 3. Structural File Boundaries

### 3.1 `MapFeatureSync.kt`
Extract all pure feature conversion and pushing functions that operate on MapLibre `FeatureSink` or GeoJSON sources:
* `pushPosition(sink: FeatureSink, services: AtlasServices)`
* `pushMeasure(sink: FeatureSink, services: AtlasServices)`
* `pushFence(sink: FeatureSink, fence: RadialFence?)`
* `pushGoTo(sink: FeatureSink, services: AtlasServices)`
* `pushRings(sink: FeatureSink, services: AtlasServices)`
* `pushGraticule(map: MapLibreMap, sink: FeatureSink)`
* `pushMarkers(sink: FeatureSink, markers: Map<String, CotMarker>)`
* `pushOpsGraphics(sink: FeatureSink, graphics: List<OperationalGraphic>)`
* `pushMeshTracks(sink: FeatureSink, markers: Map<String, CotMarker>)`
* `pushPli(sink: FeatureSink, plis: Map<String, CotPli>)`
* `pushHistoricalDossiers(sink: FeatureSink, dossiers: List<HistoricalDossier>)`
* `pushScrubPoint(sink: FeatureSink, point: ProfilePoint?)`
* `pushRouteResult(sink: FeatureSink, result: RoutingResult?)`
* `pushRoadRouteResult(sink: FeatureSink, route: RoadRouteResult?)`
* `pushLosState(sink: FeatureSink, losState: LosState, profile: TerrainProfile?, scrubIndex: Int?)`
* `ensureWaypointIcon`, `ensureGpsPuck`, `ensurePliMarker`, `ensureCotTrackIcons`, `ensureScrubIcon`.

### 3.2 `MapStyleSync.kt`
Extract all MapLibre style mutation, raster/vector tile ladder configurations, and server pack mounting:
* `ensureBaseLayer(style: Style, providerId: String, key: String?)`
* `ensureBaseTemplate(style: Style, template: String, minZoom: Int, maxZoom: Int)`
* `removeBaseLayer(style: Style)`
* `ensureVectorBaseTemplate(...)`
* `syncBlendedProviderLayers(style: Style, activeLayers: List<CompositorLayerConfig>, keyProvider: KeyProvider)`
* `syncHistoricalBlueprintLayers(style: Style, assets: List<HistoricalAsset>, visibleIds: Set<String>)`
* `syncMbtilesPackLayers(style: Style, services: AtlasServices, activePacks: Set<String>)`
* `ensureMbtilesPackLayer`, `removeMbtilesPackLayer`, `mountedMbtilesSafeIds`
* `ensureDemSource`, `removeDemSource`, `applyBaseSource`
* `buildMbtilesStyleJson(context: Context, absolutePath: String)`
* `applyOverlayVisibility(style: Style, visibleOverlays: Set<AtlasOverlay>)`
* `combineAttribution(baseAttribution: String, compositorRollup: String): String`

### 3.3 `AtlasMapOverlays.kt`
Extract all Compose overlay widgets that render above the MapLibre canvas:
* Top-left Telemetry Rail: GPS acquisition badge (`HudTelemetryPill`), offline pack count, zoom overscaling warning (`OverscalePolicy`).
* Top Compass: `CompassTape` with shortest-arc motion unwrap and CDI needle.
* Bottom Rails: `ScaleBar`, `TacticalDrawingToolbar`, and one-line bottom attribution micro-pill.
* Floating Indicators: `AtlasActivityBanner` micro-loader, `AtlasLoadingOverlay` cinematic boot radar, and `NavigationGuidanceHud`.

### 3.4 `AtlasMapDialogs.kt`
Extract all modal sheet and dialog invocations:
* `LayersDialog`, `SettingsScreen` dialog, `WaypointsDialog`, `TracksDialog`, `OfflineDialog`.
* `WaypointEditorSheet`, `HistoricalRecordSheet`, `LinkDialog`, `FenceDialog`, `MeasurePanel`.
* Sovereign Command Center bottom sheet with workspace toggles (`All`, `Clean Nav`, `Explorer`, `Tactical`, `Studio`).

### 3.5 `AtlasMap.kt` (Refactored Root)
The clean, readable coordinator:
* MapLibre `AndroidView` lifecycle bindings and gesture detectors (pan, pinch, tap, long-press).
* Wires `AtlasMapState` to `AtlasMapOverlays` and `AtlasMapDialogs`.
* Keeps function bytecode well below ART compiler limits, enabling 100% native AOT/JIT optimization.

---

## 4. Verification & Non-Regression Constraints
1. **Zero Behavioral Change:** All gesture callbacks, layers, styles, and HUD pills must function identically.
2. **Purity Law Maintained:** Files under `map/` remain isolated from pure packages (`core/`, `geo/`, etc.) by consuming them through explicit provider interfaces.
3. **Host Gates:** Multi-module gate must remain **1,480 / 1,480 passing unit tests**.
4. **Logcat Verification:** On device launch, the `Method exceeds compiler instruction limit` warning must disappear.
