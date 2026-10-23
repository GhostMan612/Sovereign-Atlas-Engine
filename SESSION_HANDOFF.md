# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence stays in
> `blueprints/app-track/`; this file points at it, never duplicates it.
>
> **Split 2026-09-30.** This file was 1,636 lines / 90 KB with ~40 `##` blocks,
> most of them closed sprints that a cold start had to read past to reach live
> state. All of that history now lives in **`blueprints/app-track/HISTORY.md`**
> (byte-identical, moved with `git mv`). Read it only when chasing a specific
> past decision. Do not read it on a normal cold start.

## Verification posture

- **The host gate is `:atlas-sdk:test` + `:sdk-consumer:test` + `:app:testPlayDebugUnitTest` + `:app:testEnterpriseDebugUnitTest`.**
  There is no `:app:testDebugUnitTest`. Two product flavors make the unflavored
  name ambiguous, and Gradle fails it with *"task 'testDebugUnitTest' is ambiguous
  in project ':app'"*. Confirmed by `--dry-run`. Do not quote that task name; it is
  the single most-repeated error in this repo's docs.
- **Last measured 2026-10-23: 1,530 tests, 0 failures+errors across all modules:**
  - `:atlas-sdk:test`: 12 tests
  - `:sdk-consumer:test`: 6 tests
  - `:app:testPlayDebugUnitTest`: 756 tests
  - `:app:testEnterpriseDebugUnitTest`: 756 tests
  Counts recorded from JUnit XML across module build directories.
- The gate runs **once per phase** (RULES.md §1A, §3), never per edit. Cadence history
  is audited and recorded honestly in `GATE_MATRIX.md`.
- Pure boundary scan passes: 104 Kotlin files across pure packages, `:atlas-sdk`, and
  `samples/sdk-consumer` have 0 forbidden imports (100% clean).
- A non-zero failure count is a real failure. Fix the code, or say why the test
  is wrong. Never re-run to manufacture a green.

## CI

- **`verify` is the only workflow and has been green since run #72 (2026-09-29).**
  Judge a red run by per-step conclusions, not the job conclusion.
- The long-red `Unit tests` step was a **Linux-only teardown race** in
  `AndroidKeyProviderTest.keySurvivesNewInstance`: `harnessOver` passed one
  `CoroutineScope` to both the DataStore builder and the provider, so `cancel()`
  tore down the `stateIn` collector that owned the scratch file. Fixed by joining
  the cancelled scope. Widening the timeout was never the fix and RULES 4.5 forbids it.
- Three other real CI defects were fixed with it: `setup-android` v3's bundled
  runtime, `gradlew` committed mode 100644, and `google-services` applied at
  configuration time without the gitignored `google-services.json`.
- **Reading CI output still requires a signed-in human.** Step logs are sign-in
  gated, the REST logs endpoint returns 403, artifact download returns 401. The
  upload-artifact step is the correct surface; tooling here cannot fetch it.

## Standing environment facts

- **No builds here unless explicitly asked** (RULES.md §1.6). The human builds and
  smokes in Android Studio; debug device failures from their pasted output.
- Toolchain: Android SDK `C:\android`, JDK 21, AGP 8.13.2, Kotlin 2.1.0, MapLibre 13.3.1.
- **Working JDK is `C:\Users\612co\.jdks\jbr-21.0.11`.** The Android Studio JBR at
  `C:\Program Files\Android\Android Studio\jbr` is stripped — no `lib/jvm.cfg` — and
  every Gradle invocation fails against it. Set both `JAVA_HOME` and `ANDROID_HOME`
  before any Gradle call.
- `androidTest/` connected tests need a device and run **only on explicit ask**.
- Never pipe `adb pull` or binary output through PowerShell pipes. Never edit sources
  via PS text pipelines. `adb kill-server` fixes most wedges (RULES.md §3).
- Device: Moto G **ZT4222BMWN**, driven by the `atlas_device` tool over wireless TCP.
  A device **is** attached here.

## Toolchain & Production Overlay state

- Master production overlay installed under `blueprints/atlas-production/` and
  `docs/atlas-production/`. SHA-256 manifest in `OVERLAY_SHA256.json`.
- **WO-01 (Convergence and Hardening): COMPLETE.** Baseline capability audit,
  decision register, gate matrix, and SDK interface inventory documented in
  `docs/atlas-production/evidence/`.
- **WO-02 (Navigation, Knowledge, Layers): COMPLETE & INTEGRATED.**
  1. Road navigation subsystem (`geo.navigation`) implemented in pure domain logic,
     complemented with `AndroidVoiceGuidance` (TTS adapter) and `AndroidRoadGraphLoader`
     (offline `roads.pack` and `roads.db` loading with SHA-256 sidecar verification & `TurnRestriction` ingestion),
     penalty-based route alternatives (`calculateRouteAlternatives`), and profile-aware ETA provenance.
  2. Full user flow wired into the host application: GoTo target selection with interactive
     `TravelProfile` selector (`DRIVING` / `CYCLING` / `WALKING`) triggers road route calculation
     and Turn-by-Turn navigation via "Navigate" button; live location fixes feed
     `NavigationSession.updateLocation()` with profile-scaled remaining ETA and automatic off-route
     rerouting; active maneuver instructions, distances, snap confidence, and ETA provenance render in
     `NavigationGuidanceHud`; route geometry renders onto MapLibre `Route` layer.
  3. Multi-layer compositor (`layers.compositor`) wired into `AtlasServices` with
     simultaneous multi-map raster blending across all 12 URL-backed providers (`USGS 3DEP Hillshade`,
     `USGS NHD Hydrography`, `USGS Topo`, `USGS Imagery+Topo Hybrid`, `OpenTopoMap`, `Esri Imagery`,
     `Esri Light Gray Canvas`, `Esri Dark Gray Canvas`, `OSM Standard`, `CARTO Positron/Dark Matter`,
     `OpenSeaMap Nautical`), deterministic `zIndex` ordering, historical overlays (`Historical Dossier Pins`,
     `BLM Land Patents`, `Sanborn Blueprints`), tactical/field overlays (`CoT Markers`, `PLI & Mesh Tracks`,
     `Operational Graphics`, `Radial Geofence`, `Graticule`, `MGRS Grid`, `Range Rings`, `Waypoints`,
     `Track`, `Measure`, `Route`), per-layer interactive opacity sliders (`10%–100%`), 6 curated
     one-tap combination presets (`AtlasLayerPresets`), and deterministic state persistence (`exportStateSnapshot` / `restoreStateSnapshot`) in `SettingsRepository` and `LayersDialog`.
  4. Historical encyclopedia dossier system (`core.knowledge`) fully wired into
     `AtlasServices`, `HistoricalKnowledgeIndex`, Verified `.pack` directory auto-ingestion
     (`KnowledgeBundleSerializer.loadVerifiedDirectory`), interactive Historical Epoch Timeline
     Slider (`1750..2025` + `All Epochs` toggle) and Dossier Search & Jump bar in `LayersDialog`,
     map pin rendering (`HISTORICAL_DOSSIERS_LAYER`), and the map UI with `HistoricalDossierSheet`
     displaying confidence badges (CONFIRMED/PROBABLE/UNCERTAIN/DISPUTED), temporal intervals,
     primary archival citations, and alternative disputed claims.
- **ADR-006 Resolved:** `offline/` networking extracted into `android/offline/`
  (`PackTileServer.kt`, `AndroidHttpDownloader.kt`), while `offline/` holds pure
  interfaces (`LocalTileServer.kt`, `OfflineDownloader.kt`). 100% pure boundary clean.
- **IIM Statement Converter Resolved:** Committed in `5b9dbde5` with Assume guard
  for vault presence and fraction unit fix. Included in test count.
- **Blender Discovery:** Blender is not in PATH; no Blender MCP registered.
  Documented in decision register. Proceeding with vector asset specifications in WO-03.

- **WO-03 (Premium UX & Reusable SDK): COMPLETE.**
  1. Guarded synthetic routing risk: `AndroidRoadGraphLoader` defaults to `allowDemoSimulation = false`,
     returns `RoadNetworkGraph.EMPTY` when `roads.db`/`roads.pack` is missing, reports routing data
     unavailable to user, and strictly preserves direct-bearing Go-To guidance.
  2. Material 3 chrome/graphite UI design tokens implemented: precision matte charcoal (`#10151C`),
     graphite (`#1B2631`), surface dark (`#141C24`), brushed chrome (`#A8B5C1`), navigation cyan (`#39C5F3`),
     and caution amber in `ui/theme/Color.kt` and `Theme.kt`, with live user-configurable Dark/Light Theme,
     Reduced-Motion (5° compass snap), and independent `Explorer` / `Tactical` / `Studio` workspace toggles
     in `SettingsScreen.kt` and `MainActivity.kt`.
  3. Canvas-based CompassTape HUD upgraded: continuous heading tape with degree ticks, cardinal/intercardinal
     labels, active target bearing diamond, course deviation indicator (CDI) needle, pinned off-tape
     arrows (`◀ TGT` / `TGT ▶`), reduced-motion 5°-step snapping support (`reducedMotion: Boolean`),
     and TalkBack accessibility semantics in `ui/hud/CompassTape.kt`, plus top-left GPS status/speed,
     offline pack coverage HUD pill, and `OverscalePolicy` zoom overscaling badge (`OVERSCALED (zX.X > native zY)`) in `AtlasMap.kt`.
  4. 10 standalone vector navigation XML drawables in `res/drawable/` integrated into `NavigationGuidanceHud`.
  5. Standalone SDK module (`:atlas-sdk`) & independent consumer (`samples/sdk-consumer`) established
     per ADR-008 (DEC-P3-011): pure domain contracts extracted into publishable Kotlin library
     (`com.sovereignatlas:atlas-sdk:1.0.0`) with `maven-publish`, published to `~/.m2/repository` via
     `:atlas-sdk:publishToMavenLocal`, explicit dependency boundaries, and zero platform leaks.
  6. External consumer project (`samples/sdk-consumer`) has standalone Gradle configuration,
     executing `ExternalConsumerVerificationTest.kt` verifying Spatial, Navigation, Layers, and
     Knowledge without `:app` or Android platform dependencies.

- **WO-04 (Certification & Release — Host Side): 100% COMPLETE.**
  1. Accepted and implemented `ADR-009` (`DEC-P4-012`): hardened `PackTileServer` to bind strictly to `InetAddress.getLoopbackAddress()` (`127.0.0.1`), added `isSafePackId` allowlist and canonical path prefix enforcement in `fileFor`, and verified concurrent ephemeral port isolation (`PackTileServerTest`).
  2. Accepted and implemented `ADR-010` (`DEC-P4-013`): added deterministic `.pack` + `.sha256` sidecar ingestion for road networks (`RoadGraphBundleSerializer`, `TurnRestriction` SQL & pack loading) and historical dossiers (`KnowledgeBundleSerializer.loadVerifiedDirectory`), golden Presidio fixtures (`road_network_presidio.pack`, `historical_dossiers_presidio.pack`), programmatic `redistributionAllowed` provider policy (`OfflineBuiltinProviders.redistributableProviders()`), and fail-closed release signing guard verification (`ReleaseSigningGuardTest`).
  3. Authored full provider/dataset licensing catalog, security audit, SBOM, and operator rollback runbook in `docs/atlas-production/evidence/DATA_LICENSE_SECURITY_SBOM.md`.
  4. Completed `docs/atlas-production/evidence/ARTIFACT_CHAIN.md` and `docs/atlas-production/evidence/RELEASE_ACCEPTANCE.md`.
  5. Sanctioned multi-module host gate green: **1,474 passing unit tests** (`12 :atlas-sdk`, `6 :sdk-consumer`, `728 Play`, `728 Enterprise`), 0 failures, 0 errors, and 0 pure-boundary violations across 98 pure files.
  6. **Physical Device Smoke (`G10`) Verified on Moto G `ZT4222BMWN` (`moto g - 2025`, Android 16 / SDK 36, `10.1.10.13:45099`):** Both `app-play-debug.apk` and `app-enterprise-debug.apk` assembled and installed via wireless ADB (`Success`); `com.sovereignatlas.atlas/.MainActivity` launched (`pid: 30540`, `topResumedActivity`), 0 fatal logcat exceptions, and live `CompassTape`, `MGRS` (`15T VK 92031 75105`), `GPS: VALID • OFFLINE: 0 packs`, `TacNavHud` (`275° / 4885 mils TRUE`), and multi-source attribution rollup verified on screen.
  7. **Sovereign Cockpit UI/UX Overhaul (`TacticalHud.kt`, `TacNavHud.kt`, `TacticalDrawingToolbar.kt`, `ic_compass_overlay.xml`, `AtlasMap.kt`):**
     - Replaced unframed floating HUD text with high-contrast Matte Charcoal (`#D910151C`) + Brushed Chrome (`#4DA8B5C1`) instrument pills (`MgrsHud`, `HudTelemetryPill`, `ScaleBar` with bracketed end ticks, and framed `TacNavHud` bearing/mils card).
     - Upgraded `ic_compass_overlay.xml` to a filled dark charcoal/chrome HSI compass dial with Navigation Cyan (`#39C5F3`) north pointer, and refined `TacNavHud` center reticle with a dark contrast halo and precision sight ticks.
     - Eliminated the bottom-left triple overlap by stacking `ScaleBar` (`bottom = 30.dp`) and `TacticalDrawingToolbar` (`bottom = 30.dp`) cleanly above the single-line bottom attribution micro-pill (`bottom = 6.dp`).
     - Replaced the clipped square right-rail FABs with `AtlasRailPillButton` controls (`minWidth = 88.dp`, active cyan border/fill) and upgraded the `Tools` bottom sheet into the **Sovereign Command Center** with a one-tap **Quick Workspace Switcher** (`All`, `Clean Nav`, `Explorer`, `Tactical`, `Studio`) and structured Graphite command cards.

- **Sovereign Visual, Iconography, Motion, Splash & Loading System (COMPLETE):**
  - Animated Android 12+ splash (`avd_splash_atlas.xml` over `ic_splash_compass.xml`, `#10151C` background) with an exit transition in `MainActivity` (320 ms fade + icon zoom, 100 ms fade-only when `reducedMotion`).
  - Adaptive launcher icons (`ic_launcher_background/foreground/monochrome.xml`) in the charcoal / chrome / cyan / amber palette.
  - `AtlasLoadingOverlay` rebuilt as a cinematic boot screen: `bg_sovereign_topography.jpg` backdrop (`drawable-nodpi`), `Canvas` radar sweep with counter-rotating astrolabe arcs, boot-stage telemetry from `AtlasMotionMath.bootTelemetryStage`. `AtlasActivityBanner` micro-loader is wired to the MBTiles swap, offline pack scan, road route calculation and LoS profile calculation.
  - 15 new control vector icons (`ic_hud_*`, `ic_draw_*`, `ic_tool_*`) wired into the rail pills, Command Center rows, drawing toolbar and `HudTelemetryPill` (`iconRes`, `pulse`). MIL-STD-2525D symbols and `wp-icon` / `scrub-icon` were redrawn at unchanged pixel sizes.
  - Pure `ui/hud/AtlasMotionMath.kt` (shortest-arc unwrap, pack download fraction, boot stage labels) with `AtlasMotionMathTest`. `CompassOverlay` and `CompassTape` animate along the shortest arc when `reducedMotion` is false and keep the 5° snap when it is true.
  - **Physical Device Smoke & Fresh Clean Deployment:** Uninstalled previous packages (`com.sovereignatlas.atlas`, `com.sovereignatlas.atlas.enterprise`) from Moto G `ZT4222BMWN`. Executed full clean build (`clean :app:assemblePlayDebug :app:assembleEnterpriseDebug` in 7m 43s; Play APK SHA-256 `1338b5500d41b9b1ed6a7a258e02c33ee35bdf66409852cf8331f4126982d718` / 125,314,971 bytes; Enterprise APK SHA-256 `0d75154032c9bb3d4e16030683f1dbb4f48770500c1dcbfe3fa1fb6efb34a821` / 125,314,983 bytes). Fresh installed both packages onto Moto G (`ZT4222BMWN`, Android 16 / SDK 36), launched `com.sovereignatlas.atlas/.MainActivity` (PID 32396), and verified 0 runtime crashes / 0 uncaught exceptions.

- **Monolithic Screen Deconstruction & Advanced Roadmap Blueprints (AUDITED & AMENDED 2026-10-23):**
  - **Monolithic `AtlasMap.kt` & `AtlasMapScreen` Deconstructed:** Decomposed 3,618-line `AtlasMap.kt` down to 1,592 lines (-2,026 lines, -56.0%). `AtlasMapScreen` method body was cleanly extracted into cohesive child composables, genuinely eliminating the ART compiler instruction limit warning (`Method exceeds compiler instruction limit: 16956`):
    1. Extracted Turn-by-Turn navigation guidance HUD to `ui/hud/NavigationGuidanceHud.kt`.
    2. Extracted rail pills, chips, and tool row buttons to `map/AtlasMapControls.kt`.
    3. Extracted base layer styling, MBTiles pack sync, DEM raster sources, blueprint rasters, and compositor overlay visibility to `map/MapStyleSync.kt`.
    4. Extracted MapLibre feature pushers (`pushMarkers`, `pushOpsGraphics`, `pushMeshTracks`, `pushPli`, `pushPosition`, `pushMeasure`, `pushFence`, `pushGoTo`, `pushRings`, `pushGraticule`, `pushHistoricalDossiers`, `pushScrubPoint`, `pushRouteResult`, `pushRoadRouteResult`, `pushLosState`), icon decoders, and foot routing to `map/MapFeatureSync.kt`.
    5. Extracted HUD overlays (`AtlasTopHud`, `AtlasTelemetryHud`, `AtlasNavInstrumentHud`, `AtlasMapRailPills`, `AtlasBottomAttribution`) to `map/AtlasMapOverlays.kt`.
    6. Extracted GoTo routing computation card and measure panel hosts to `map/AtlasGoToPanel.kt`.
    7. Extracted Sovereign Command Center, Layer Manager, Target Marker Drop, and all modal dialogs to `map/AtlasMapDialogsHost.kt`.
  - **Complete Subsystem Blueprints Audited & Corrected (`blueprints/`):**
    1. `BP-01: Modular Map & Screen Architecture` (`blueprints/01_MODULAR_MAP_ARCHITECTURE.md`) — Updated to reflect the landed 7-file decoupled map presentation architecture.
    2. `BP-02: 2.5D DEM Hillshade, Pitch Perspective & Dynamic 2D Viewshed Mask` (`blueprints/02_TRUE_3D_DEM_AND_VIEWSHED.md`) — Corrected to reflect actual MapLibre Native Android 13.3.1 capabilities (2.5D raster-dem hillshading with camera pitch up to 60°; true 3D meshes are web-only); standardized elevation decoding on Mapbox Terrain-RGB to align with `DemEngine.kt`.
    3. `BP-03: Plug-and-Play Meshtastic & LoRa Tactical Transport` (`blueprints/03_MESHTASTIC_LORA_TACTICAL_TRANSPORT.md`) — Aligned transport with official Meshtastic Client Protobuf specs (`ToRadio`/`FromRadio` over BLE/Serial); grounded DTN reassembly in actual Sovereign Mantle patterns (`FragmentStore`, `FragmentAckCodec`); enforced strict Atlas Network Profile law.
    4. `BP-04: Field GIS Ingestion Subsystem` (`blueprints/04_FIELD_GIS_INGESTION.md`) — Enforced strict pure boundary: pure streaming interfaces in `gis/ingestion/` with zero Android imports, isolating `XmlPullParser` and `android.database.sqlite` in platform adapters.
  - **Verification:** Verified 1,480 passing tests (12 :atlas-sdk, 6 :sdk-consumer, 734 Play, 734 Enterprise), 0 failures, 0 errors, 0 pure-boundary violations.

  - **Phase 11 (Field GIS Ingestion Subsystem - COMPLETE & INTEGRATED):**
    1. Pure domain GIS contracts (`geo.gis`) implemented with 0 Android/Compose/MapLibre imports: `GisModels.kt`, `WkbDecoder.kt` (supporting Big/Little Endian, GeoPackage binary headers `0x47 0x50` with envelope decoding), `GeoJsonParser.kt` (RFC 7946 features, properties, vertex budget caps), `ShapefileParser.kt` (ESRI Shapefile byte-stream, dBASE III attribute table, `.prj` projection SRID detection with Web Mercator reprojection), `KmlStreamingParser.kt` (Placemarks, paths, polygons, ExtendedData attributes), and `GisRepository.kt` (`InMemoryGisRepository`).
    2. Platform adapter (`android.gis.AndroidGisImporter`) implemented with Zip-Slip path traversal protection on `.kmz` and zipped shapefiles, Android `SQLiteDatabase` query engine for `.gpkg` feature tables and geometry columns, and system file picker ingestion.
    3. MapLibre dynamic styling and feature synchronization: added `USER_GIS_*` constants to `AtlasLayerInstaller.kt`, dynamic `syncUserGisLayers` in `MapStyleSync.kt`, and `pushUserGisDatasets` in `MapFeatureSync.kt`.
    4. Sovereign Cockpit UI & feature inspection: added "Field GIS" to Sovereign Command Center, built `GisLayersSheet` (format badges, visibility toggles, palette chips, stroke width slider), and `GisFeatureInspectorSheet` (feature ID, geometry type, coordinates, GoTo navigation, attribute table).
    5. Interactive map hit-testing: wired click interrogation on active user GIS layers in `AtlasMap.kt`.
    6. Verified 100% green host gate: **1,530 passing unit tests** (+50 tests: 12 :atlas-sdk, 6 :sdk-consumer, 756 Play, 756 Enterprise), 0 failures, 0 errors, 0 pure-boundary violations across 104 pure files.

## Next moves

- **Implementation Roadmap from Corrected Blueprints:**
  1. **Phase 12 (2.5D DEM Hillshade, Pitch Perspective & Dynamic 2D Viewshed Mask):** Implement 2.5D hillshade styling, perspective camera pitch controls, and 360° radial viewshed polygon projection based on corrected `blueprints/02_TRUE_3D_DEM_AND_VIEWSHED.md`.
  2. **Phase 13 (Plug-and-Play Meshtastic / LoRa Tactical Transport):** Integrate USB-Serial & BLE hardware drivers, Protobuf wire framing, and delay-tolerant store based on corrected `blueprints/03_MESHTASTIC_LORA_TACTICAL_TRANSPORT.md`.
- **Remaining Release Action (`G12`):**
  1. **`G12` (Signed Release Packaging):** Provide release signing credentials (`keystore.properties` or `-PKEYSTORE_*`) when ready to emit signed release APKs.
