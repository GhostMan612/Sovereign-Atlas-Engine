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
- **Last measured 2026-10-22: 1,445 tests, 0 failures+errors across all modules:**
  - `:atlas-sdk:test`: 10 tests
  - `:sdk-consumer:test`: 5 tests
  - `:app:testPlayDebugUnitTest`: 715 tests
  - `:app:testEnterpriseDebugUnitTest`: 715 tests
  Counts recorded from JUnit XML across module build directories.
- The gate runs **once per phase** (RULES.md §1A, §3), never per edit. Cadence history
  is audited and recorded honestly in `GATE_MATRIX.md`.
- Pure boundary scan passes: 47 Kotlin files across pure packages, `:atlas-sdk`, and
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
     (offline graph loading & operational default graph).
  2. Full user flow wired into the host application: GoTo target selection triggers
     road route calculation and Turn-by-Turn navigation via "Navigate" button; live
     location fixes feed `NavigationSession.updateLocation()` with automatic off-route
     rerouting; active maneuver instructions, distances, and ETAs render in
     `NavigationGuidanceHud`; route geometry renders onto MapLibre `Route` layer.
  3. Multi-layer compositor (`layers.compositor`) wired into `AtlasServices`, synchronizing
     runtime layer visibility and opacity with MapLibre style layers and exposed in
     `LayersDialog` as the compositor stack inspector.
  4. Historical encyclopedia dossier system (`core.knowledge`) fully wired into
     `AtlasServices`, `HistoricalKnowledgeIndex`, and the map UI with `HistoricalDossierSheet`
     displaying confidence badges (CONFIRMED/PROBABLE/UNCERTAIN/DISPUTED), temporal
     intervals, primary archival citations, and alternative disputed claims.
  5. 100% test pass rate across both product flavors (695 tests per flavor, 1,390 total).
  6. Pure boundary scan passes across 96 Kotlin files in 10 pure packages with 0 violations.
- **ADR-006 Resolved:** `offline/` networking extracted into `android/offline/`
  (`PackTileServer.kt`, `AndroidHttpDownloader.kt`), while `offline/` holds pure
  interfaces (`LocalTileServer.kt`, `OfflineDownloader.kt`). 100% pure boundary clean.
- **IIM Statement Converter Resolved:** Committed in `5b9dbde5` with Assume guard
  for vault presence and fraction unit fix. Included in test count.
- **Blender Discovery:** Blender is not in PATH; no Blender MCP registered.
  Documented in decision register. Proceeding with vector asset specifications in WO-03.

- **WO-03 (Premium UX & Reusable SDK): COMPLETE.**
  1. Guarded synthetic routing risk: `AndroidRoadGraphLoader` defaults to `allowDemoSimulation = false`,
     returns `RoadNetworkGraph.EMPTY` when `roads.db` is missing, reports routing data
     unavailable to user, and strictly preserves direct-bearing Go-To guidance. Verified
     with 4 unit tests in `AndroidRoadGraphLoaderTest.kt`.
  2. Material 3 chrome/graphite UI design tokens implemented: precision matte charcoal (`#10151C`),
     graphite (`#1B2631`), surface dark (`#141C24`), brushed chrome (`#A8B5C1`), navigation cyan (`#39C5F3`),
     and caution amber in `ui/theme/Color.kt` and `Theme.kt`, wrapped at application root in `MainActivity.kt`.
  3. Canvas-based CompassTape HUD upgraded: continuous heading tape with degree ticks, cardinal/intercardinal
     labels, active target bearing diamond, course deviation indicator (CDI) needle, pinned off-tape
     arrows (`◀ TGT` / `TGT ▶`), and TalkBack accessibility semantics in `ui/hud/CompassTape.kt`.
     Math verified with 5 unit tests in `CompassTapeMathTest.kt`.
  4. 10 standalone vector navigation XML drawables in `res/drawable/` (`ic_atlas_emblem.xml`, `ic_nav_straight.xml`,
     `ic_nav_turn_left.xml`, `ic_nav_turn_right.xml`, `ic_nav_turn_slight_left.xml`, `ic_nav_turn_slight_right.xml`,
     `ic_nav_turn_sharp_left.xml`, `ic_nav_turn_sharp_right.xml`, `ic_nav_uturn.xml`, `ic_nav_arrive.xml`)
     integrated into `NavigationGuidanceHud`.
  5. Standalone SDK module (`:atlas-sdk`) & independent consumer (`samples/sdk-consumer`) established
     per ADR-008 (DEC-P3-011): pure domain contracts extracted into publishable Kotlin library
     (`com.sovereignatlas:atlas-sdk:1.0.0`) with `maven-publish`, explicit dependency boundaries,
     and zero platform leaks. Host `:app` consumes `:atlas-sdk` via project dependency.
  6. External consumer project (`samples/sdk-consumer`) has standalone Gradle configuration,
     executing `ExternalConsumerVerificationTest.kt` verifying Spatial, Navigation, Layers, and
     Knowledge without `:app` or Android platform dependencies.
  7. 100% test pass rate across all modules and flavors: 1,433 total passing unit tests
     (10 in `:atlas-sdk:test`, 5 in `samples/sdk-consumer:test`, 709 in `:app:testPlayDebugUnitTest`,
     709 in `:app:testEnterpriseDebugUnitTest`).
  8. Pure boundary scan passes across 47 Kotlin files in pure packages, `:atlas-sdk`, and
     `samples/sdk-consumer` with 0 violations (100% clean).

- **WO-04 (Certification & Release — Host Side): COMPLETE.**
  1. Accepted and implemented `ADR-009` (`DEC-P4-012`): hardened `PackTileServer` to bind strictly to `InetAddress.getLoopbackAddress()` (`127.0.0.1`), added `isSafePackId` allowlist and canonical path prefix enforcement in `fileFor`, and verified concurrent ephemeral port isolation (`PackTileServerTest`).
  2. Added SHA-256 checksum-verified atomic bundle persistence and rollback (`KnowledgeBundleSerializer.atomicWriteVerified`) and failure-injection tests for corrupt/unsupported offline pack indexes (`KnowledgeBundleSerializerTest`, `OfflineStoreTest`).
  3. Authored full provider/dataset licensing catalog, security audit, SBOM, and operator rollback runbook in `docs/atlas-production/evidence/DATA_LICENSE_SECURITY_SBOM.md`.
  4. Completed `docs/atlas-production/evidence/ARTIFACT_CHAIN.md` and `docs/atlas-production/evidence/RELEASE_ACCEPTANCE.md` with honest `RELEASE BLOCKED` disposition for the three operator-gated items (`G07` legal redistribution sign-off, `G10` physical Moto G run, `G12` keystore-signed release APK build).
  5. Sanctioned multi-module host gate green: **1,445 passing unit tests** (`10 :atlas-sdk`, `5 :sdk-consumer`, `715 Play`, `715 Enterprise`), 0 failures, 0 errors, and 0 pure-boundary violations across 47 pure files.

## Next moves

- **Operator-Gated Release Actions (Requires Explicit Operator Action / Approval):**
  1. **`G07` (Legal Sign-Off):** Review `docs/atlas-production/evidence/DATA_LICENSE_SECURITY_SBOM.md` §1 and sign off on provider redistribution terms.
  2. **`G10` (Physical Device Smoke):** Authorize or run physical device acceptance on Moto G `ZT4222BMWN`.
  3. **`G12` (Signed Release Packaging):** Provide release signing credentials (`keystore.properties` or `-PKEYSTORE_*`) and authorize `assemblePlayRelease` / `assembleEnterpriseRelease`.
