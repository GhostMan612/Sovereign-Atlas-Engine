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
- **Last measured 2026-10-22: 1,469 tests, 0 failures+errors across all modules:**
  - `:atlas-sdk:test`: 12 tests
  - `:sdk-consumer:test`: 5 tests
  - `:app:testPlayDebugUnitTest`: 726 tests
  - `:app:testEnterpriseDebugUnitTest`: 726 tests
  Counts recorded from JUnit XML across module build directories.
- The gate runs **once per phase** (RULES.md §1A, §3), never per edit. Cadence history
  is audited and recorded honestly in `GATE_MATRIX.md`.
- Pure boundary scan passes: 98 Kotlin files across pure packages, `:atlas-sdk`, and
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
     `Track`, `Measure`, `Route`), per-layer interactive opacity sliders (`10%–100%`), and 6 curated
     one-tap combination presets (`AtlasLayerPresets`) in `LayersDialog`.
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
     and caution amber in `ui/theme/Color.kt` and `Theme.kt`, wrapped at application root in `MainActivity.kt`.
  3. Canvas-based CompassTape HUD upgraded: continuous heading tape with degree ticks, cardinal/intercardinal
     labels, active target bearing diamond, course deviation indicator (CDI) needle, pinned off-tape
     arrows (`◀ TGT` / `TGT ▶`), reduced-motion 5°-step snapping support (`reducedMotion: Boolean`),
     and TalkBack accessibility semantics in `ui/hud/CompassTape.kt`, plus top-left GPS status/speed and
     offline pack coverage HUD pill in `AtlasMap.kt`.
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
  5. Sanctioned multi-module host gate green: **1,469 passing unit tests** (`12 :atlas-sdk`, `5 :sdk-consumer`, `726 Play`, `726 Enterprise`), 0 failures, 0 errors, and 0 pure-boundary violations across 98 pure files.

## Next moves

- **Deferred Physical Device & Release Actions (Per Operator Directive):**
  1. **`G10` (Physical Device Smoke):** Deferred until entire phase-set closure; run physical device acceptance on Moto G `ZT4222BMWN` when requested by operator.
  2. **`G12` (Signed Release Packaging):** Provide release signing credentials (`keystore.properties` or `-PKEYSTORE_*`) when ready to emit signed release APKs.

