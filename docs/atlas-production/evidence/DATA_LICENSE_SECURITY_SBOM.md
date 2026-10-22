# Dataset Provenance, License Catalog, Security Audit, SBOM & Operator Runbook (WO-04)

Date: 2026-10-22  
Audited By: Principal Software Architect, Security Lead & Release Engineer  
Scope: Work Order 04 (`WO-04_CERTIFICATION_AND_RELEASE.md`) & `specs/OFFLINE_DATA_LICENSE_SECURITY.md`

---

## 1. Dataset & Provider Licensing Catalog (`G07`)

Per `specs/OFFLINE_DATA_LICENSE_SECURITY.md` and `OfflineProviders.kt`, every built-in provider and local bundle source is cataloged below. Open data is explicitly distinguished from freely viewable proprietary tiles, and bulk prefetch guards are enforced in `OfflineStore.plan()`.

| Provider ID | Title | Owner / Source | Coverage & Zoom | Format | License | Attribution String | Offline Prefetch Allowed | Rate Limit / Guard Policy | API Key Policy | Redistribution Disposition |
|---|---|---|---|---|---|---|---|---|---|---|
| `osm-standard` | OpenStreetMap Standard | OpenStreetMap Foundation (`tile.openstreetmap.org`) | Global, `z0–z19` | Raster PNG | ODbL (`openstreetmap.org/copyright`) | `© OpenStreetMap contributors` | **No** (`prefetchAllowed = false`) | `BULK_GUARD` refuses unapproved bulk downloads; 500ms min interval; custom `User-Agent` | None | Open data (ODbL); tile servers subject to OSM Tile Usage Policy |
| `usgs-topo` | USGS Topo (The National Map) | U.S. Geological Survey (`basemap.nationalmap.gov`) | US & Territories, `z0–z16` | Raster Tile | **Public Domain** (17 U.S.C. § 105) | `USGS The National Map` | **Yes** (`prefetchAllowed = true`) | 500ms min interval (`OfflineDownloader`); max 10,000 session tiles (`MAX_SESSION_TILES`) | None | Public Domain; unrestricted offline storage & redistribution |
| `opentopomap` | OpenTopoMap | OpenTopoMap (`{s}.tile.opentopomap.org`) | Global, `z0–z17` | Raster PNG | CC-BY-SA (`opentopomap.org`) | `© OpenStreetMap contributors, SRTM \| style: © OpenTopoMap (CC-BY-SA)` | **No** (`prefetchAllowed = false`) | Refused by `BULK_GUARD` without explicit approval; 500ms interval | None | Share-alike attribution required; no bulk scraping |
| `esri-imagery` | Esri World Imagery | Esri / Maxar / Earthstar (`server.arcgisonline.com`) | Global, `z0–z19` | Raster Tile | Proprietary — Esri Terms of Use (`arcgis.com`) | `Esri, Maxar, Earthstar Geographics, and the GIS User Community` |Bounded (`prefetchAllowed = true`, capped at `MAX_SESSION_TILES`) | 500ms min interval; 30s per-tile timeout; session quota cap | Commercial redistribution requires Esri agreement | **PROPRIETARY VIEWING TERMS** — Operator legal sign-off required before commercial redistribution |
| `esri-light-gray` | Esri Light Gray Canvas | Esri / HERE / Garmin / OSM | Global, `z0–z16` | Raster Tile | Proprietary — Esri Terms of Use | `Esri, HERE, Garmin, OpenStreetMap contributors` | Bounded (`MAX_SESSION_TILES`) | 500ms min interval; session quota cap | Commercial redistribution requires Esri agreement | **PROPRIETARY VIEWING TERMS** — Operator legal sign-off required |
| `esri-dark-gray` | Esri Dark Gray Canvas | Esri / HERE / Garmin / OSM | Global, `z0–z16` | Raster Tile | Proprietary — Esri Terms of Use | `Esri, HERE, Garmin, OpenStreetMap contributors` | Bounded (`MAX_SESSION_TILES`) | 500ms min interval; session quota cap | Commercial redistribution requires Esri agreement | **PROPRIETARY VIEWING TERMS** — Operator legal sign-off required |
| `carto-positron` | CARTO Positron | CARTO (`basemaps.cartocdn.com`) | Global, `z0–z20` | Raster PNG | CC-BY-SA / CARTO Terms (`carto.com/attribution`) | `© OpenStreetMap contributors © CARTO` | **No** (`prefetchAllowed = false`) | Unresolved `{key}` placeholder in raw registry; fails loudly unless user injects Tink-encrypted key via `withApiKey()` | Tink AEAD `AndroidKeyProvider` | Requires user-supplied CARTO API key; zero embedded credentials |
| `carto-dark-matter` | CARTO Dark Matter | CARTO (`basemaps.cartocdn.com`) | Global, `z0–z20` | Raster PNG | CC-BY-SA / CARTO Terms | `© OpenStreetMap contributors © CARTO` | **No** (`prefetchAllowed = false`) | Same as `carto-positron` | Tink AEAD `AndroidKeyProvider` | Requires user-supplied CARTO API key; zero embedded credentials |
| `local-bundle` | Local Bundle (MBTiles / DEM / Roads / Knowledge) | User-supplied local files | Per-bundle metadata | `.mbtiles` (PNG/PBF), `.dem`, `roads.db`, `.json` | Travels with bundle metadata (`MbtilesScanner`, `KnowledgeBundle.license`) | Travels with bundle metadata (`LayerCompositor.getAttributionRollup()`) | N/A (Local disk) | Served via loopback `PackTileServer` (`127.0.0.1`); SHA-256 sidecar & atomic verification | None | Unverified packs remain flagged unverified (`LocalHistoricalAssetRepositoryTest`) |

---

## 2. Privacy & Security Audit (`G08`)

### 2.1 Zero Embedded Secrets & Encrypted Key Storage
- **No Embedded Credentials:** Verified across all source files and tests (`AndroidHttpDownloaderTest.theCartoProvidersStillCarryAnUnresolvedKeyPlaceholder`). Raw provider descriptors never hold API keys.
- **Tink AEAD Key Persistence:** User-supplied provider keys are encrypted at rest via `AndroidKeyProvider` backed by Android Keystore AES-256-GCM and Jetpack DataStore (`AndroidKeyProviderTest.storedFileDoesNotContainPlaintext`, `tamperedFileRecoversToEmpty`).

### 2.2 Localhost Tile Server Hardening (`PackTileServer.kt` — ADR-009)
- **Loopback-Only Socket Binding:** `ServerSocket(port, 4, InetAddress.getLoopbackAddress())` binds strictly to `127.0.0.1`. External devices on Wi-Fi/LAN cannot connect to the ephemeral tile server port. Verified by `PackTileServerTest.serverBindsStrictlyToLoopbackAddress`.
- **Path Traversal Defense-in-Depth:**
  1. `isSafePackId` rejects empty strings, leading dots (`.hidden`), `..` segments, and any character outside `[A-Za-z0-9._-]`.
  2. `isTileRef` restricts `z`, `x`, and `y` strictly to 1–7 ASCII digits.
  3. Canonical path verification (`target.canonicalFile.path.startsWith(root.canonicalFile.path + File.separator)`) prevents symlink or traversal escapes outside `packsDir()`.
  4. Verified by `PackTileServerTest.traversalIs404` and `encodedAndDotPrefixTraversalVariantsAreRejectedWith404`.
- **Port & Lifecycle Isolation:** Ephemeral port allocation (`port = 0`), idempotent `start()`, and clean `stop()` shutdown of `MbtilesTileSource` verified by `PackTileServerTest.restartAndConcurrentInstancesIsolateEphemeralLoopbackPorts`.

### 2.3 Checksum Verification & Atomic Bundle Rollback
- **DEM Packs:** SHA-256 sidecars verified in `DemPackTest.sidecarsAreWrittenWithVerifiableSha256Sums`.
- **Knowledge Bundles:** `KnowledgeBundleSerializer.atomicWriteVerified()` verifies SHA-256 before disk write, writes to `.tmp`, re-verifies on-disk bytes, and atomically replaces the target file—rolling back cleanly and preserving existing valid data on tampered payloads (`KnowledgeBundleSerializerTest.atomicWriteVerifiedCommitsValidBundleAndPreservesOriginalOnTamperedUpdate`).
- **Offline Pack Index:** `OfflineStore.restore()` gracefully catches corrupted JSON and unsupported schema versions without crashing (`OfflineStoreTest.corruptedIndexJsonSetsErrorWithoutCrashing`, `unsupportedIndexVersionIsRejectedGracefully`).

### 2.4 Location Privacy & Data Controls
- **Opt-In Recording Only:** `TrackRecorder` starts in `Idle` state (`TrackRecorderTest.initialStateIsIdleWithNoPoints`) and records GPS fixes only when explicitly started by the user.
- **No Fabricated Null-Island Coordinates:** Missing or acquiring GPS never defaults to `(0.0, 0.0)` (`TrackRecorderTest.acquiringServiceYieldsNoFabricatedPoints`, `AtlasLocationTest`).
- **Private Waypoint Sharing Scope:** Waypoints default to private scope and require explicit user action to share over CoT mesh (`WaypointSharingUiTest.thePrivateDescriptionSaysItIsNeverTransmitted`).
- **Export & Delete Controls:** Users can export tracks/waypoints to standard GPX 1.1 (`GpxExportTest`) and permanently delete tracks (`TrackRepositoryTest.roundTripInsertAndDelete`) and offline packs (`OfflineStoreTest.removeDeletesPack`).

### 2.5 Fail-Closed Release Signing Guard (`app/build.gradle`)
- Lines 89–101 of `apps/atlas-android/app/build.gradle` inspect the Gradle task graph prior to execution. Any task containing `release` without valid signing credentials (`keystore.properties`, `-PKEYSTORE_PATH`, or `KEYSTORE_PATH` environment variable) throws `GradleException("FAIL-CLOSED: Release signing credentials missing. Halting before packaging.")`.

---

## 3. Software Bill of Materials (SBOM)

| Module | Dependency Coordinate | Version | License | Purpose |
|---|---|---|---|---|
| `:atlas-sdk` | `org.jetbrains.kotlin:kotlin-stdlib` | `2.1.0` | Apache-2.0 | Pure Kotlin standard library |
| `:atlas-sdk` | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | `1.10.1` | Apache-2.0 | Asynchronous flows & state management |
| `:app` | `org.maplibre.gl:android-sdk` | `13.3.1` | BSD-2-Clause | Native OpenGL/Vulkan map rendering |
| `:app` | `androidx.compose:compose-bom` | `2024.12.01` | Apache-2.0 | Jetpack Compose UI toolkit & Material 3 |
| `:app` | `androidx.activity:activity-compose` | `1.9.3` | Apache-2.0 | Android Activity Compose integration |
| `:app` | `androidx.lifecycle:lifecycle-runtime-ktx` | `2.8.7` | Apache-2.0 | Android lifecycle-aware coroutines |
| `:app` | `app.cash.sqldelight:android-driver` | `2.0.2` | Apache-2.0 | Offline SQLite persistence (`AtlasDatabase` v2) |
| `:app` | `app.cash.sqldelight:coroutines-extensions` | `2.0.2` | Apache-2.0 | Reactive SQLite queries |
| `:app` | `com.google.crypto.tink:tink-android` | `1.15.0` | Apache-2.0 | AEAD encryption for API key storage |
| `:app` | `androidx.datastore:datastore-preferences` | `1.1.1` | Apache-2.0 | Encrypted key-value storage backing |
| `:app` | `com.google.protobuf:protobuf-javalite` | `3.25.5` | BSD-3-Clause | CoT / TAK protobuf payload parsing |
| `test` | `junit:junit` | `4.13.2` | EPL-1.0 | Deterministic unit testing across all modules |

---

## 4. Operator Runbook & Recovery / Rollback Procedures

### 4.1 Running the Sanctioned Verification Gate (Host)
```powershell
$env:JAVA_HOME = "C:\Users\612co\.jdks\jbr-21.0.11"
$env:ANDROID_HOME = "C:\android"
cd C:\Sovereign-Atlas-Engine\apps\atlas-android
.\gradlew.bat :atlas-sdk:test :sdk-consumer:test :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest --console=plain
```

### 4.2 Publishing the Standalone SDK Artifact Locally
```powershell
.\gradlew.bat :atlas-sdk:publishToMavenLocal --console=plain
```
Publishes `com.sovereignatlas:atlas-sdk:1.0.0` to `%USERPROFILE%\.m2\repository\com\sovereignatlas\atlas-sdk\1.0.0\`.

### 4.3 Building Signed Release Artifacts (Requires Operator Keystore)
Provide credentials via `apps/atlas-android/app/keystore.properties` (gitignored) or `-PKEYSTORE_PATH=... -PKEYSTORE_PASSWORD=... -PKEY_ALIAS=... -PKEY_PASSWORD=...`:
```powershell
.\gradlew.bat :app:assemblePlayRelease :app:assembleEnterpriseRelease --console=plain
```
Without credentials, the build halts immediately via the fail-closed signing guard.

### 4.4 Rollback & Recovery Procedures
1. **Corrupted Offline Pack / Knowledge Bundle Recovery:**
   - Remove the affected pack via the in-app Offline Packs dialog (`OfflineStore.removePack(packId)`) or clear `<appFilesDir>/packs/<packId>/`.
   - `KnowledgeBundleSerializer.atomicWriteVerified` automatically discards partial `.tmp` files and preserves the prior verified bundle.
2. **Corrupted Keystore Self-Heal:**
   - `AndroidKeyProvider` automatically detects an unloadable Android Keystore master key, wipes only the broken key entry, and regenerates a fresh master key without crashing (`AndroidKeyProviderTest.anUnloadableMasterKeyIsWipedAndRegenerated`).
3. **Git / Work Order Rollback:**
   - Work orders are committed on isolated commits with explicit paths. To revert to the pre-WO-04 state, run `git revert <wo-04-commit-sha>` or `git reset --hard <wo-03-commit-sha>`.
