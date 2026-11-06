# DEC-024 — GIS Parsers Move Into `:atlas-sdk`

- **Status:** Accepted (Operator directive, 2026-11-06).
- **Date:** 2026-11-06.
- **Baseline:** `9e73f73`.
- **Scope:** `apps/atlas-android` module boundary only. Eight source files
  move from `:app` to `:atlas-sdk` byte-identical, under the same packages;
  `:atlas-sdk` gains one runtime library (§2.2) and its published version
  becomes `1.1.0` (§2.5); two `:app` call sites read a moved property into
  a local `val` (§2.4). No behavior, public signature, test or CI change
  (Compose stability: §4).

## 1. Context

ADR-008 houses the pure domain packages (`core`, `geo`, ...) in
`:atlas-sdk`. The Phase 11 GIS parsers (`geo.gis`) and the BLM GLO land
patent parser (`core.LandPatentGeoJsonParser`) were written into `:app`
instead. They import no Android, MapLibre, `org.json` or `xmlpull` type,
but no JVM consumer can reach them: `:app` is an application module that
also depends on Firebase (BoM, Firestore, Auth) and applies
`google-services` whenever `google-services.json` is present.

Sovereign Mantle consumes Atlas through `:atlas-sdk` only, and may not
import `:app` or any `com.google.firebase` artifact. The parsers have to
live in the SDK first.

## 2. Decision

### 2.1 The move set

The four parsers and the `:app` declarations they need, found by closing
over their references in `:app` main (the closure ends at eight files):

| File | Why it moves |
|------|--------------|
| `geo/gis/GeoJsonParser.kt` | parser |
| `geo/gis/KmlStreamingParser.kt` | parser |
| `geo/gis/ShapefileParser.kt` | parser |
| `core/LandPatentGeoJsonParser.kt` | parser |
| `core/GeoJsonGeometry.kt` | `LngLat`, `GeoJsonGeometry` (all four parsers) |
| `geo/gis/GisModels.kt` | `GisDataset` and the other parser outputs |
| `geo/render/RenderGeometry.kt` | `RenderGeometry`, `RenderProperty` (the three `geo.gis` parsers) |
| `core/HistoricalAsset.kt` | `LandPatent`, `HistoricalLicense` (the land patent parser) |

`AtlasBoundingBox`, the only other type they use, is already in
`:atlas-sdk`. Each file keeps its package, so no import in the 19 `:app`
files that use them changes and no typealias is needed; two of those files
change for another reason (§2.4). The files move with `git mv` and keep
their history.

`WkbDecoder.kt` and `GisRepository.kt` are pure as well but are not needed
by the four parsers; they stay in `:app` and read the moved types through
`implementation project(':atlas-sdk')`. The platform adapter
`android/gis/AndroidGisImporter.kt` stays in `:app`.

### 2.2 The new SDK dependency

`:atlas-sdk` gains
`implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3'`,
the version a downstream consumer of the SDK already runs. `:app` keeps
its own `1.9.0` declaration; Gradle resolves `:app` to the higher version,
and the parsers call only the stable JSON-tree API that both versions
share:

- Runtime library only. `GeoJsonParser` and `LandPatentGeoJsonParser` walk
  the JSON tree (`parseToJsonElement`); nothing is `@Serializable`, and the
  serialization compiler plugin stays unapplied, as in `:app`.
- `implementation`, not `api`: no public signature of a moved file takes or
  returns a `kotlinx.serialization` type (`GeoJsonParser.parse` and
  `LandPatentGeoJsonParser.parse(json: String)` take strings).
- Pure JVM. The ADR-008 boundary (no Android framework, Compose or MapLibre
  type on the module classpath) is unchanged.
- The parsers compile against 1.7.3 in `:atlas-sdk`; their tests execute
  in `:app` (§2.3), which resolves 1.9.0.

### 2.3 Tests stay where CI runs them

The parsers' tests (`GeoJsonParserTest`, `KmlStreamingParserTest`,
`ShapefileParserTest`, `LandPatentGeoJsonParserTest`, `HistoricalAssetTest`)
stay in `:app`'s test tree. They test the moved classes through the
module dependency, as `CompassMathTest` and `MgrsTest` already test
`:atlas-sdk` classes from `:app`. CI (`.github/workflows/atlas.yaml`)
runs `:app:testPlayDebugUnitTest` and `:app:testEnterpriseDebugUnitTest`
only; tests moved into `:atlas-sdk` would leave CI.

### 2.4 Cross-module smart casts

Kotlin smart-casts a public `val` only inside the module that declares it,
because another module's property can change (gain a custom getter)
without the reading module being recompiled. After the move, three
null-checked reads in `:app` stopped compiling, in both flavors:

| File | Read |
|------|------|
| `map/AtlasMap.kt` | `GisImportResult.dataset` |
| `ui/historical/HistoricalRecordSheet.kt` | `SanbornBlueprint.minZoom`, `SanbornBlueprint.maxZoom` |

Each site now copies the property into a local `val` and checks that. The
branches taken and the values passed on are unchanged.

### 2.5 Published version

The `maven-publish` coordinate moves from `com.sovereignatlas:atlas-sdk:1.0.0`
to `1.1.0`. The move adds public API (the parsers and their model types),
and a coordinate that consumers have already resolved must not change
content under the same version. ADR-008 and the earlier evidence records
name `1.0.0` because that is the artifact they verified; they are not
rewritten.

## 3. Verification

- Forbidden-import scan over every `:atlas-sdk` source, main and test
  (`android.`, `androidx.`, `org.json`, `org.xmlpull`,
  `com.google.firebase`, `org.maplibre`): 0 hits in 41 `.kt` files.
- `:atlas-sdk:dependencies --configuration runtimeClasspath`:
  `kotlinx-coroutines-core` 1.10.1, `kotlinx-serialization-json` and
  `kotlinx-serialization-core` 1.7.3, `kotlin-stdlib` 2.1.0 (the Kotlin
  Gradle plugin's version; the serialization library's own 2.0.20 resolves
  up to it), `org.jetbrains:annotations` 23.0.0. No Android, Firebase,
  `org.json`, `xmlpull` or MapLibre artifact.
- Host gate (`:atlas-sdk:test`, `:sdk-consumer:test`,
  `:app:testPlayDebugUnitTest`, `:app:testEnterpriseDebugUnitTest`), three
  runs on 2026-11-06. The first stopped at `:app` compilation, both
  flavors, on the three smart casts (§2.4). The second, after that fix,
  passed with serialization 1.9.0: 1,590 tests. The third, on the
  committed tree after the pin to 1.7.3 and the `1.1.0` bump, forced all
  four test tasks to execute (`--rerun`): 29 / 7 / 777 / 777 tests, 1,590
  in all, 0 failures, 0 errors, 0 skipped.

## 4. Consequences

- **Positive:** the parsers are reachable from a pure JVM consumer, and
  their purity is enforced by the compiler (ADR-008), not by review.
- **Neutral:** `core`, `geo.gis` and `geo.render` are now split across
  `:app` and `:atlas-sdk`, as `core` and `geo` already were. The Compose
  compiler plugin does not process `:atlas-sdk`, so it infers no stability
  for the moved data classes; under strong skipping (the default for the
  Kotlin 2.1.0 plugin `:app` applies) a composable taking one still skips,
  comparing it by instance instead of by `equals`, as every SDK type
  already does.
- **Neutral, for consumers:** `:atlas-sdk`'s published metadata (ADR-008)
  now also declares `kotlinx-serialization-json` 1.7.3 at runtime;
  `kotlin-stdlib` stays at the plugin's 2.1.0. A consumer on another
  serialization version resolves that at its own edge, as `:app` does with
  its `1.9.0`.
- **Negative:** `:atlas-sdk:test` does not exercise the parsers, and CI does
  not run `:atlas-sdk:test` or `:sdk-consumer:test` at all. Moving
  SDK-owned tests into `:atlas-sdk` waits for CI to run those tasks.
