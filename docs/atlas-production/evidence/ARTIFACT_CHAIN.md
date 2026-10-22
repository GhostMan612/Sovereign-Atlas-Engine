# Artifact Chain — Sovereign Atlas Production (`WO-01` through `WO-04`)

Date: 2026-10-22  
Reviewer and timestamp: Principal Software Architect & QA Lead — 2026-10-22T15:18:00-05:00

---

## 1. `REQ-P1-01` — Pure Offline Socket Boundary & Baseline Convergence
- **Requirement ID:** `REQ-P1-01`
- **Source and verified revision:** `RULES.md` §2.1, `WO-01_CONVERGENCE_AND_HARDENING.md`
- **User-visible acceptance:** Offline packs serve raster, vector PBF, and DEM tiles over loopback HTTP while keeping `com.sovereignatlas.atlas.offline` 100% free of networking/platform imports.
- **Architecture decision / ADR:** `ADR-006` (`DEC-P1-001`), `DEC-P1-002`, `DEC-P1-003`
- **Blueprint/work order:** `blueprints/atlas-production/work-orders/WO-01_CONVERGENCE_AND_HARDENING.md`
- **Affected exact paths:**
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/offline/LocalTileServer.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/offline/OfflineDownloader.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/android/offline/PackTileServer.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/android/offline/AndroidHttpDownloader.kt`
- **Data/license/provenance:** Per-provider metadata and `BULK_GUARD` enforced in `OfflineProviders.kt` and `OfflineStore.kt`.
- **Test fixture and expected result:** Seeded temporary tile directories and mock `MbtilesTileSource` serve HTTP 200 on valid XYZ/TMS requests and HTTP 404 on missing/exhausted/traversal paths.
- **Unit/integration/device evidence:** `PackTileServerTest`, `TileBucketQuotaTest`, `AndroidHttpDownloaderTest`, `OfflineStoreTest`.
- **Performance and accessibility evidence:** Rate limiting (500ms default interval) and session quotas (`BASEMAP_SESSION_TILES`, `DEM_SESSION_TILES`, `GLOBAL_TILE_HARD_CAP`) verified.
- **Risk / rollback:** Isolated to `android/offline/` adapter tier; reversible via Git commit.
- **Status:** `VERIFIED`
- **Release disposition:** Approved for host certification.

---

## 2. `REQ-P2-01` — Offline Road Routing, Maneuvers, Voice Guidance & Synthetic Fallback Guard
- **Requirement ID:** `REQ-P2-01`
- **Source and verified revision:** `specs/NAVIGATION_AND_COMPASS.md`, `WO-02_NAVIGATION_KNOWLEDGE_LAYERS.md`
- **User-visible acceptance:** Users can plan road routes with turn restrictions and travel profiles (`DRIVING`, `WALKING`, `CYCLING`), receive maneuver HUD banners and Android TTS voice prompts, and automatically fall back to straight-line Go-To when `roads.db` is unavailable (never silently activating a synthetic road graph in production).
- **Architecture decision / ADR:** `DEC-P1-005`, `DEC-P3-008`
- **Blueprint/work order:** `blueprints/atlas-production/work-orders/WO-02_NAVIGATION_KNOWLEDGE_LAYERS.md`
- **Affected exact paths:**
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/geo/navigation/RoadGraph.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/geo/navigation/RoadRoutingEngine.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/geo/navigation/RouteSnapper.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/geo/navigation/RoadManeuvers.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/geo/navigation/NavigationSession.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/geo/navigation/RoadNavigationState.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/geo/navigation/VoiceGuidance.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/android/navigation/AndroidRoadGraphLoader.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/android/navigation/AndroidVoiceGuidance.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/map/AtlasMap.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/ui/GoToCard.kt`
- **Data/license/provenance:** Local `roads.db` SQLite graph; returns `RoadNetworkGraph.EMPTY` when absent unless `allowDemoSimulation = true` is explicitly passed in tests.
- **Test fixture and expected result:** Multi-node road graphs with one-way edges and turn restrictions compute optimal A* paths, snap GPS fixes, trigger off-route recalculation, and return null route on `EMPTY` graph.
- **Unit/integration/device evidence:** `RoadRoutingEngineTest`, `RouteSnapperTest`, `ManeuverEngineTest`, `NavigationSessionTest`, `RoadNavigationStateTest`, `AndroidRoadGraphLoaderTest`. Physical device road-dataset run pending (`G10`).
- **Performance and accessibility evidence:** Deterministic A* priority queue; TalkBack announcements and TTS voice guidance deduplicated per maneuver step.
- **Risk / rollback:** If `roads.db` is absent on device, straight-line Go-To remains 100% operational.
- **Status:** `VERIFIED` (Host) / `PROVISIONAL` (Physical Device Road Dataset)
- **Release disposition:** Host verified; physical device validation gated on operator run (`G10`).

---

## 3. `REQ-P2-02` — Multi-Layer Compositor & Temporal Historical Dossiers
- **Requirement ID:** `REQ-P2-02`
- **Source and verified revision:** `specs/LAYERS_HISTORICAL_TACTICAL.md`, `WO-02_NAVIGATION_KNOWLEDGE_LAYERS.md`
- **User-visible acceptance:** Users can toggle, reorder, opacity-adjust (`0.0..1.0`), and temporally filter map layers with live attribution rollup in `LayersDialog`, and inspect historical dossiers with confidence scores (`CONFIRMED`, `PROBABLE`, `UNCERTAIN`, `DISPUTED`), archival citations, and disputed claims in `HistoricalDossierSheet`.
- **Architecture decision / ADR:** `DEC-P1-006`, `DEC-P1-007`
- **Blueprint/work order:** `blueprints/atlas-production/work-orders/WO-02_NAVIGATION_KNOWLEDGE_LAYERS.md`
- **Affected exact paths:**
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/layers/compositor/LayerDescriptor.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/layers/compositor/LayerCompositor.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/core/knowledge/HistoricalDossier.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/core/knowledge/HistoricalKnowledgeIndex.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/core/knowledge/KnowledgeBundleSerializer.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/ui/LayersDialog.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/ui/historical/HistoricalRecordSheet.kt`
- **Data/license/provenance:** Every `LayerDescriptor` carries `LayerAttribution(providerName, license, requiredText)`; every `HistoricalDossier` carries `SourceCitation` with archive, year, shelfmark, and license.
- **Test fixture and expected result:** Deterministic z-index sorting, temporal epoch filtering, attribution string deduplication, and SHA-256 verified atomic bundle persistence.
- **Unit/integration/device evidence:** `LayerCompositorTest`, `HistoricalKnowledgeIndexTest`, `KnowledgeBundleSerializerTest`.
- **Performance and accessibility evidence:** Semantic descriptions on confidence badges and layer sliders.
- **Risk / rollback:** Pure domain models in `:atlas-sdk`; UI sheets gracefully hide when no dossier is selected.
- **Status:** `VERIFIED`
- **Release disposition:** Approved for host certification.

---

## 4. `REQ-P3-01` — Graphite/Chrome Material 3 Theme, Precision CompassTape HUD & Vector Assets
- **Requirement ID:** `REQ-P3-01`
- **Source and verified revision:** `specs/DESIGN_SYSTEM_AND_BLENDER.md`, `specs/NAVIGATION_AND_COMPASS.md`, `WO-03_PREMIUM_UX_AND_SDK.md`
- **User-visible acceptance:** High-contrast matte charcoal (`#10151C`), graphite (`#1B2631`), brushed chrome (`#A8B5C1`), and navigation cyan (`#39C5F3`) theme; continuous 120° FOV `CompassTape` with target bearing diamond, Course Deviation Indicator (CDI) bar, pinned off-tape arrows (`◀ TGT` / `TGT ▶`), and 10 vector maneuver drawables.
- **Architecture decision / ADR:** `DEC-P1-004`, `DEC-P3-009`
- **Blueprint/work order:** `blueprints/atlas-production/work-orders/WO-03_PREMIUM_UX_AND_SDK.md`
- **Affected exact paths:**
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/ui/theme/Color.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/ui/theme/Theme.kt`
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/ui/hud/CompassTape.kt`
  - `apps/atlas-android/app/src/main/res/drawable/ic_atlas_emblem.xml` and `ic_nav_*.xml` (10 files)
- **Data/license/provenance:** First-party vector XML assets authored in-repo (Public Domain / project license).
- **Test fixture and expected result:** Wrap-around angular deltas (`[-180, +180]`), tape visibility within `±60°` FOV, off-tape left/right pinning, and normalized CDI clamping (`[-1.0, +1.0]`).
- **Unit/integration/device evidence:** `CompassTapeMathTest`, `CompassMathTest`.
- **Performance and accessibility evidence:** Single-pass Compose `Canvas` drawing; TalkBack `contentDescription` announces heading, cardinal direction, target bearing, and course deviation.
- **Risk / rollback:** Self-contained theme and HUD components.
- **Status:** `VERIFIED` (Host)
- **Release disposition:** Approved for host certification; visual inspection on Moto G (`G10`) pending operator run.

---

## 5. `REQ-P3-02` — Standalone `:atlas-sdk` Module & Independent External Consumer
- **Requirement ID:** `REQ-P3-02`
- **Source and verified revision:** `specs/SDK_CONTRACT.md`, `WO-03_PREMIUM_UX_AND_SDK.md`
- **User-visible acceptance:** External JVM/Android applications can depend on `com.sovereignatlas:atlas-sdk:1.0.0` without depending on `:app` or pulling in Android/Compose/MapLibre dependencies.
- **Architecture decision / ADR:** `ADR-008` (`DEC-P3-010`, `DEC-P3-011`)
- **Blueprint/work order:** `blueprints/atlas-production/work-orders/WO-03_PREMIUM_UX_AND_SDK.md`
- **Affected exact paths:**
  - `apps/atlas-android/atlas-sdk/build.gradle`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/sdk/AtlasSdk.kt`
  - `samples/sdk-consumer/build.gradle`
  - `samples/sdk-consumer/settings.gradle`
  - `samples/sdk-consumer/src/test/kotlin/com/sovereignatlas/consumer/ExternalConsumerVerificationTest.kt`
- **Data/license/provenance:** First-party SDK facade; Apache-2.0 transitive dependencies (`kotlin-stdlib`, `kotlinx-coroutines-core`).
- **Test fixture and expected result:** External consumer project compiles and passes 6 verification tests across `Spatial`, `Navigation`, `Layers`, and `Knowledge`; `:atlas-sdk` passes 12 library tests.
- **Unit/integration/device evidence:** `:atlas-sdk:test` (12 tests), `:sdk-consumer:test` (6 tests), `:app` `SdkConsumerVerificationTest` (6 tests).
- **Performance and accessibility evidence:** Zero platform overhead; compile-time purity enforced by JVM library classpath.
- **Risk / rollback:** Multi-module Gradle setup verified across all 4 tasks.
- **Status:** `VERIFIED`
- **Release disposition:** Approved and verified.

---

## 6. `REQ-P4-01` — Security Hardening, Failure Injection, Provenance/License Catalog & Release Certification
- **Requirement ID:** `REQ-P4-01`
- **Source and verified revision:** `specs/OFFLINE_DATA_LICENSE_SECURITY.md`, `WO-04_CERTIFICATION_AND_RELEASE.md`
- **User-visible acceptance:** `PackTileServer` binds strictly to `127.0.0.1` (loopback) and rejects canonical/encoded path traversal; offline bundles verify SHA-256 and roll back atomically on corruption; corrupted pack indexes fail safely without crashing; fail-closed signing guard blocks unsigned release builds.
- **Architecture decision / ADR:** `ADR-009` (`DEC-P4-012`), `ADR-010` (`DEC-P4-013`)
- **Blueprint/work order:** `blueprints/atlas-production/work-orders/WO-04_CERTIFICATION_AND_RELEASE.md`
- **Affected exact paths:**
  - `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/android/offline/PackTileServer.kt`
  - `apps/atlas-android/atlas-sdk/src/main/java/com/sovereignatlas/atlas/core/knowledge/KnowledgeBundleSerializer.kt`
  - `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/android/offline/PackTileServerTest.kt`
  - `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/core/knowledge/KnowledgeBundleSerializerTest.kt`
  - `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/offline/OfflineStoreTest.kt`
  - `docs/atlas-production/evidence/DATA_LICENSE_SECURITY_SBOM.md`
  - `docs/atlas-production/evidence/RELEASE_ACCEPTANCE.md`
- **Data/license/provenance:** Full provider catalog, SBOM, and security audit recorded in `DATA_LICENSE_SECURITY_SBOM.md`.
- **Test fixture and expected result:** Loopback socket assertion (`boundAddress().isLoopbackAddress == true`), traversal variant 404 assertions, concurrent ephemeral port isolation, SHA-256 atomic write & tamper rollback, and corrupt index recovery tests.
- **Unit/integration/device evidence:** 1,474 total passing unit tests across `:atlas-sdk` (12), `:sdk-consumer` (6), `:app:testPlayDebugUnitTest` (728), and `:app:testEnterpriseDebugUnitTest` (728).
- **Performance and accessibility evidence:** Zero overhead on tile serving; atomic file moves via `Files.move`.
- **Risk / rollback:** Tracked in `ADR-009` / `ADR-010` and reversible by commit.
- **Status:** `VERIFIED` (Host) / `DEFERRED` (Physical Device Smoke `G10` & Keystore Signing `G12`)
- **Release disposition:** `HOST READY / DEVICE & KEYSTORE GATED` pending deferred physical Moto G execution (`G10`) and keystore-signed release packaging (`G12`).

