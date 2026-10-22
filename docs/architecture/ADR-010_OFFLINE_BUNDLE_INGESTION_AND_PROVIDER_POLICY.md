# ADR-010: Verified Offline Road & Knowledge Bundle Ingestion, Sidecar Verification, and Open-Data Redistribution Policy

## Status
ACCEPTED / IMPLEMENTED

## Context
To close all remaining host-side production items across Work Orders 02, 03, and 04 without running physical device smoke tests (`G10`), four capabilities needed end-to-end host completion:
1. **Offline Road Graph Ingestion & Turn Restrictions:** `AndroidRoadGraphLoader` queried `RoadNode` and `RoadEdge` from `roads.db` but omitted `TurnRestriction` rows, lacked `.sha256` sidecar verification on road graph files, and could not be exercised against real on-disk files in standard JVM unit tests because `android.database.sqlite.SQLiteDatabase` is stubbed on the host JVM.
2. **Historical Knowledge Bundle Directory Ingestion:** `KnowledgeBundleSerializer` rendered JSON and performed SHA-256 atomic writes, but `AppServices` did not automatically scan and ingest verified on-disk knowledge bundles from `files/knowledge/` into `HistoricalKnowledgeIndex`.
3. **Provider Redistribution Policy Enforcement (`G07`):** `OfflineProviderDescriptor` enforced `prefetchAllowed` and `bulkGuard`, but needed an explicit `redistributionAllowed` flag distinguishing Public Domain / self-supplied data (`usgs-topo`, `local-bundle`) from proprietary or server-policy-restricted providers (`esri-*`, `osm-standard`, `opentopomap`, `carto-*`).
4. **SDK Artifact Publishing & Fail-Closed Signing Proof (`G11` / `G12`):** Publishing `com.sovereignatlas:atlas-sdk:1.0.0` to Maven Local (`:atlas-sdk:publishToMavenLocal`) and verifying the fail-closed release signing guard in unit tests.

## Decision
1. **Road Graph Bundle Serializer (`RoadGraph.kt` & `AndroidRoadGraphLoader.kt`):**
   - Add `RoadGraphBundle` and `RoadGraphBundleSerializer` to `com.sovereignatlas.atlas.geo.navigation` (`:atlas-sdk`), supporting deterministic line-oriented `.pack` serialization and parsing (`renderPack`, `parsePack`, `loadVerifiedFile`) of `RoadNode`, `RoadEdge`, and `TurnRestriction` records with provenance metadata (`bundleId`, `license`, `attribution`) and optional `.sha256` sidecar verification.
   - Update `AndroidRoadGraphLoader` to query `TurnRestriction` from `roads.db`, verify `.sha256` sidecars before loading `roads.db` or `roads.pack`, and expose `loadFromDirectory(dir: File)` for deterministic host and device ingestion.
2. **Knowledge Bundle Ingestion (`KnowledgeBundleSerializer.kt` & `AppServices.kt`):**
   - Add `renderPack`, `parsePack`, and `loadVerifiedDirectory(dir: File?, index: HistoricalKnowledgeIndex): Int` to `KnowledgeBundleSerializer` with `.sha256` sidecar verification.
   - Wire `loadVerifiedDirectory` into `AppServices.historicalIndex` for both internal (`filesDir/knowledge`) and external (`getExternalFilesDir("knowledge")`) directories.
3. **Golden Real-Data Fixtures (`src/test/resources/golden/`):**
   - Provide real Presidio of San Francisco road network (`road_network_presidio.pack`) and archival HABS/NPS historical dossiers (`historical_dossiers_presidio.pack`) under `apps/atlas-android/app/src/test/resources/golden/` and verify end-to-end ingestion, turn-restricted routing, and spatial/temporal queries in unit tests.
4. **Programmatic Redistribution Policy (`OfflineProviders.kt`):**
   - Add `val redistributionAllowed: Boolean = false` to `OfflineProviderDescriptor` (`true` only for `usgs-topo` and `local-bundle`) and `OfflineBuiltinProviders.redistributableProviders()`.

## Consequences
- **Positive:**
  - Real offline road networks (with turn restrictions) and historical dossier bundles can be ingested from disk and verified with SHA-256 sidecars on both JVM host tests and Android devices.
  - Open-data redistribution compliance (`G07`) is programmatically queryable and enforced in tests.
  - `com.sovereignatlas:atlas-sdk:1.0.0` is verified as a published Maven artifact in `~/.m2/repository`.
