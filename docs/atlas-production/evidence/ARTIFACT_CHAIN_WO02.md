# Artifact Chain — WO-02 Navigation, Knowledge, Layers

Requirement ID: REQ-WO02-NAV-LAYERS-KNOWLEDGE
Source and verified revision: Baseline `c37cec97937152bb598aefd8284ba00ca6d68d7b` + WO-02 implementation
User-visible acceptance:
- Daily-driver road routing subsystem operating offline with A* edge expansion, turn restrictions, and driving/walking/cycling travel profiles.
- Automatic maneuver generation (turn slight left/right, turn left/right, sharp turns, U-turns, depart, arrive) with localized instructions.
- Real-time route snapping, off-route detection (cross-track thresholding), and reroute dispatch.
- NavigationSession state machine driving distance-based audio announcements (500m, 100m, now) with mute toggle and arrival detection.
- Composable multi-layer compositor with independent opacity controls, z-order sorting, temporal epoch filters, zoom bounds, and attribution rollup.
- Historical encyclopedic dossier system with temporal validity (valid-from/to), archival citations, confidence scores, conflicting claims, spatial/temporal querying, and SHA-256 verified knowledge bundles.

Architecture decision / ADR:
- DEC-P1-005 (Road Navigation Subsystem Isolation)
- DEC-P1-006 (Multi-Layer Compositor Architecture)
- DEC-P1-007 (Historical Knowledge Dossier Model)

Blueprint/work order:
- `blueprints/atlas-production/00_MASTER_PLAN.md`
- `blueprints/atlas-production/work-orders/WO-02_NAVIGATION_KNOWLEDGE_LAYERS.md`
- `blueprints/atlas-production/specs/NAVIGATION_AND_COMPASS.md`
- `blueprints/atlas-production/specs/LAYERS_HISTORICAL_TACTICAL.md`

Affected exact paths:
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/geo/navigation/RoadGraph.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/geo/navigation/RoadManeuvers.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/geo/navigation/RoadRoutingEngine.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/geo/navigation/RouteSnapper.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/geo/navigation/VoiceGuidance.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/geo/navigation/NavigationSession.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/layers/compositor/LayerDescriptor.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/layers/compositor/LayerCompositor.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/core/knowledge/HistoricalDossier.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/core/knowledge/HistoricalKnowledgeIndex.kt`
- `apps/atlas-android/app/src/main/java/com/sovereignatlas/atlas/core/knowledge/KnowledgeBundleSerializer.kt`
- `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/geo/navigation/RoadRoutingEngineTest.kt`
- `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/geo/navigation/RouteSnapperTest.kt`
- `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/geo/navigation/ManeuverEngineTest.kt`
- `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/geo/navigation/NavigationSessionTest.kt`
- `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/layers/compositor/LayerCompositorTest.kt`
- `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/core/knowledge/HistoricalKnowledgeIndexTest.kt`
- `apps/atlas-android/app/src/test/java/com/sovereignatlas/atlas/core/knowledge/KnowledgeBundleSerializerTest.kt`

Data/license/provenance:
- OSM-derived routing graph data models with clear profile attribution.
- Citations, author/archive provenance, and licensing attached to every historical dossier record.
- Attribution rollup combines all visible layer attribution without redundancy.

Test fixture and expected result:
- 24 new deterministic unit tests covering routing, snapping, turn restrictions, maneuvers, voice announcements, layer compositing, temporal filtering, and bundle checksums.
- 689 tests per flavor (1,378 total across `play` and `enterprise`), 0 failures, 0 errors.

Unit/integration/device evidence:
- JUnit XML files in `apps/atlas-android/app/build/test-results/testPlayDebugUnitTest/` and `testEnterpriseDebugUnitTest/`.
- Purity scan: 95 Kotlin files across 10 pure packages, 0 violations (100% clean).

Performance and accessibility evidence:
- PriorityQueue-based A* routing with edge contraction.
- Voice guidance prompts debounced and distance-thresholded with mute toggle.
- Layer compositor provides constant-time visibility lookups and cached z-order sorting.

Risk / rollback:
- Zero regression risk to existing features: hiking `RoutingEngine.kt` and `GoToState.kt` remain untouched and operational.

Status: VERIFIED
Reviewer and timestamp: Principal Software Architect, 2026-10-14T10:36:00-05:00
Release disposition: APPROVED for Phase 3 (WO-03) transition.
