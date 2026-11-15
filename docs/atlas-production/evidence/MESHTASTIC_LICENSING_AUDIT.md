# Meshtastic Licensing & Dependency Audit (Issue #16 — Phases A1 & A2)

- **Date:** 2026-11-14
- **Baseline Commit:** `ea9f03dcf96b3fd62b09b234927de269c82fc1a4`
- **Scope:** Issue `#16` Option A Feasibility Audit — **Phases A1** (resolved release-runtime dependency & license inventory) and **A2** (Wire-generated protobuf expression & compatibility assessment).
- **Governance Guardrail:** This document records **technical and license evidence only**. It makes **no** relicensing declaration, adds **no** root `LICENSE`/`NOTICE` file, and modifies **no** Maven POM metadata or source headers (pending Phase `A3` ownership & distribution sign-off).

> **Historical assessment, not a current disposition.** The observations and counts below concern the stated `ea9f03d` baseline and its inspected build outputs. They do not identify or authorize a later release candidate. The legal compatibility and downstream-obligation statements in this historical audit are unreviewed assessments, not established conclusions; each proposed artifact, its dependencies, its delivery model and the applicable terms require qualified review. See [CANDIDATE_EVIDENCE_PACKAGE.md](CANDIDATE_EVIDENCE_PACKAGE.md) for the pending artifact-specific evidence.

## 1. Executive Summary

1. **`:atlas-sdk` Runtime (`14` Maven artifacts) is `100% Apache-2.0`, but its compiled JAR embeds `573` Wire-generated `org/meshtastic/proto/*.class` types derived from `GPL-3.0` `.proto` inputs:**
   - `com.sovereignatlas:atlas-sdk:1.1.0` (`atlas-sdk-1.1.0.jar`) contains `573` `org/meshtastic/proto/*.class` files + `181` `com/sovereignatlas/**/*.class` files, **`0` `.proto` resource files**, and **`0` `META-INF/LICENSE*` / `META-INF/NOTICE*` files**.
   - Published `atlas-sdk-1.1.0.pom` omits `<licenses>` and `<scm>`.
   - Wire generated `107` `.kt` files (`43,707` lines); **`105` of `107` files include `/** ... */` KDoc prose copied from upstream `GPL-3.0` `.proto` comments** (e.g., `PortNum.kt`: `216`/`337` doc lines; `FromRadio.kt`: `89`/`591`; `ToRadio.kt`: `31`/`251`).
2. **`:app` `playReleaseRuntimeClasspath` and `enterpriseReleaseRuntimeClasspath` are currently `100%` identical (`192` Maven artifacts + `project :atlas-sdk`):**
   - Neither flavor currently excludes Play Services or Firebase.
   - `179` of `192` Maven runtime artifacts carry GPL-3.0-compatible open-source POM licenses (`169` Apache-2.0 — noting `okhttp:4.12.0` embeds an `MPL-2.0` Public Suffix List notice — `6` MIT, `2` BSD-2-Clause, `2` BSD-3-Clause).
   - **`13` bundled proprietary Google/Play binary AARs** (pulled transitively by direct dependencies `com.google.firebase:firebase-auth:23.2.1` and `com.google.firebase:firebase-firestore:25.1.4`) carry proprietary terms (`11` `Android Software Development Kit License`, `1` `Play Core Software Development Kit Terms of Service`, `1` `Play Integrity API Terms of Service`).
3. **Critical Option A Feasibility Result:**
   - **Standalone `:atlas-sdk` (`GPL-3.0`):** Technically feasible from a third-party dependency standpoint (`14/14` runtime dependencies are Apache-2.0, compatible with GPL-3.0), **provided** Atlas approves GPL-3.0 licensing and Corresponding Source/notice obligations — which would propagate `GPL-3.0` obligations to downstream consumers (`Sovereign Mantle`, `:app`, and external Maven consumers).
   - **Combined `:app` Play & Enterprise APKs (`GPL-3.0`):** **Direct distribution blocker under standard GPL-3.0 §§ 5(c)/6**, because both flavors statically bundle `13` proprietary Google/Play AARs that do not permit GPL-3.0 sublicensing or Corresponding Source distribution. Unless proprietary AARs are decoupled/removed from a GPL-distributed flavor or rights-holder/independent options (`B` / `C`) are chosen, the current combined `:app` APKs cannot satisfy standard GPL-3.0 redistribution.
4. **Additional Findings Outside Meshtastic Schemas:**
   - **`takmessage.proto` (`atakmap.commoncommo.v1`, `:app`):** Pre-existing file (commit `e385670`) has **no copyright or license header**, is missing from `DATA_LICENSE_SECURITY_SBOM.md`, and Wire compiles `18` Kotlin files from it (`9` classes across `play`/`enterprise`) used at runtime by `AtakPayloadParser.kt` and `CotProtobufGenerator.kt`.
   - **SBOM Drift (`DATA_LICENSE_SECURITY_SBOM.md`):** Existing SBOM lists stale versions (`sqldelight` `2.0.2` vs resolved `2.1.0`, `tink-android` `1.15.0` vs `1.23.0`, `datastore-preferences` `1.1.1` vs `1.3.0-alpha11`), misses Wire's transitive `kotlin-stdlib` bump (`2.1.0` -> `2.2.21`), and omits Firebase/GMS/Play/MGRS/Serialization dependencies.

## 2. Reproducible Audit Method

Resolved without assembling APKs (`RULES.md` §1.6) from `apps/atlas-android` and `samples/sdk-consumer`:

```powershell
$env:JAVA_HOME = "C:\Users\612co\.jdks\jbr-21.0.11"
$env:ANDROID_HOME = "C:\android"
.\gradlew.bat :atlas-sdk:dependencies --configuration runtimeClasspath --no-daemon --console=plain
.\gradlew.bat :atlas-sdk:dependencies --configuration compileClasspath --no-daemon --console=plain
.\gradlew.bat :app:dependencies --configuration playReleaseRuntimeClasspath --no-daemon --console=plain
.\gradlew.bat :app:dependencies --configuration enterpriseReleaseRuntimeClasspath --no-daemon --console=plain
.\gradlew.bat :buildEnvironment :atlas-sdk:buildEnvironment :app:buildEnvironment --no-daemon --console=plain
```

Each unique resolved `group:artifact:version` was cross-checked against its cached `.pom` (recursing parent POM `<licenses>`), `.jar`/`.aar` archive notices (`LICENSE*`, `NOTICE*`, `third_party_licenses.*`, embedded `*.proto`), and generated Wire source/JAR artifacts.

## 3. Phase A1 — Resolved Dependency & License Inventory

### 3.1 Artifact & Archive Summary by Module

| Scope | Resolved Maven Coords | Bundled in Distributed Artifact? | License Breakdown | Key Findings |
|---|---|---|---|---|
| `:atlas-sdk` `runtimeClasspath` & `compileClasspath` | `14` (`4` direct, `10` transitive) | **Yes** (`compile` API + runtime deps of `:atlas-sdk`; plus `573` Wire `.class` inside JAR) | `14` Apache-2.0 | Zero unknown/proprietary Maven deps; `atlas-sdk-1.1.0.jar` lacks `LICENSE`/`NOTICE`; `pom` lacks `<licenses>` |
| `samples/sdk-consumer` `runtimeClasspath` | `14` (`com.sovereignatlas:atlas-sdk:1.1.0` + same `13` Maven deps + `kotlin-stdlib`) | Standalone verification consumer | `14` Apache-2.0 (`external` Maven deps) | Pulls `:atlas-sdk` + Apache-2.0 deps cleanly with `ANDROID_HOME` unset |
| `:app` `playReleaseRuntimeClasspath` | `192` Maven + `project :atlas-sdk` (`26` direct) | **Yes** (bundled into `PlayRelease` APK DEX/resources) | `169` Apache-2.0, `6` MIT, `2` BSD-2-Clause, `2` BSD-3-Clause, `13` Proprietary Google/Play | Identical resolved set to `Enterprise`; `37` archives carry `LICENSE`/`NOTICE`/`third_party_licenses.*` |
| `:app` `enterpriseReleaseRuntimeClasspath` | `192` Maven + `project :atlas-sdk` (`26` direct) | **Yes** (bundled into `EnterpriseRelease` APK DEX/resources) | Identical to `playReleaseRuntimeClasspath` | `0`-artifact diff vs `Play`: `firebase-auth` / `firebase-firestore` also ship in `Enterprise` |
| `:buildEnvironment` (root + `:atlas-sdk` + `:app`) | `222` unique Gradle/plugin coords | **No** (build/codegen tools only; exceptions are Wire-generated `.kt` source compiled into JAR/APK) | AGP/Wire/SQLDelight/Google-Services = Apache-2.0; `protobuf-java`/`java-util` = BSD-3-Clause | Note `com.google.gms:google-services:4.5.0` plugin itself is Apache-2.0; runtime proprietary AARs come from `:app` deps |

### 3.2 Complete `:atlas-sdk` Resolved Dependencies (`runtimeClasspath` == `compileClasspath`, `14` Artifacts)

| Coordinate | Origin | POM License (Parent Inherited?) | Archive Notices / Notes |
|---|---|---|---|
| `com.squareup.okio:okio-jvm:3.17.0` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `com.squareup.okio:okio:3.17.0` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `com.squareup.wire:wire-runtime-jvm:6.4.7` | Transitive | Apache-2.0 (parent=No) | None |
| `com.squareup.wire:wire-runtime:6.4.7` | Direct | Apache-2.0 (parent=No) | None |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.21` | Direct | Apache-2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.1` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1` | Direct | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.7.3` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.3` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` | Direct | The Apache Software License, Version 2.0 (parent=No) | None |
| `org.jetbrains:annotations:23.0.0` | Transitive | The Apache Software License, Version 2.0 (parent=No) | None |

### 3.3 Non-Apache-2.0 & Proprietary Runtime Dependencies in `:app` (`23` of `192`, Identical in Play & Enterprise)

| Coordinate | Origin & Pulled By | POM License | Archive Notices / Protos | GPL-3.0 Compatibility Impact |
|---|---|---|---|---|
| `androidx.datastore:datastore-preferences-external-protobuf:1.3.0-alpha11` | Transitive (`datastore-preferences:1.3.0-alpha11`) | BSD-3-Clause ([link](https://opensource.org/licenses/BSD-3-Clause)) | `META-INF/androidx/datastore/datastore-preferences-external-protobuf/LICENSE.txt` | Compatible with GPL-3.0 (attribution/notice retention required) |
| `com.google.android.gms:play-services-auth-api-phone:18.0.2` | Transitive (`firebase-auth` / `play-services-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.gms:play-services-auth-base:18.0.4` | Transitive (`firebase-auth` -> `play-services-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.gms:play-services-auth:20.7.0` | Transitive (`firebase-auth` -> `credentials-play-services-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.gms:play-services-base:18.1.0` | Transitive (`firebase-firestore`, `firebase-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.gms:play-services-basement:18.4.0` | Transitive (`firebase-firestore`, `firebase-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.gms:play-services-fido:20.1.0` | Transitive (`firebase-auth` / `play-services-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.gms:play-services-tasks:18.2.0` | Transitive (`firebase-firestore`, `firebase-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.libraries.identity.googleid:googleid:1.1.0` | Transitive (`firebase-auth` -> `credentials-play-services-auth`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.play:core-common:2.0.3` | Transitive (`firebase-auth` -> `play:integrity:1.3.0`) | Play Core Software Development Kit Terms of Service ([link](https://developer.android.com/guide/playcore/license)) | `LICENSE`, `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.play:integrity:1.3.0` | Transitive (`firebase-auth`, `recaptcha:18.6.1`) | Play Integrity API Terms of Service ([link](https://developer.android.com/google/play/integrity/overview#tos)) | `LICENSE`, `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.android.recaptcha:recaptcha:18.6.1` | Transitive (`firebase-auth:23.2.1`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.firebase:firebase-auth-interop:20.0.0` | Transitive (`firebase-firestore:25.1.4`, `firebase-auth:23.2.1`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.firebase:firebase-auth:23.2.1` | Direct (`:app`) | Android Software Development Kit License ([link](https://developer.android.com/studio/terms.html)) | `third_party_licenses.json`, `third_party_licenses.txt` | **BLOCKER if combined under GPL-3.0** (proprietary binary terms; no GPL sublicense/source) |
| `com.google.protobuf:protobuf-javalite:3.25.5` | Transitive (`firebase-firestore` -> `grpc-protobuf-lite`, `protolite-well-known-types`) | BSD-3-Clause ([link](https://opensource.org/licenses/BSD-3-Clause)) | 10 bundled `.proto` | Compatible with GPL-3.0 (attribution/notice retention required) |
| `mil.nga:color:1.0.0` | Transitive (`mil.nga:grid:1.1.2`) | The MIT License (MIT) ([link](https://github.com/ngageoint/color-java/blob/master/LICENSE.txt)) | None | Compatible with GPL-3.0 (attribution/notice retention required) |
| `mil.nga:grid:1.1.2` | Transitive (`mil.nga:mgrs:2.1.3`) | The MIT License (MIT) ([link](https://github.com/ngageoint/grid-java/blob/master/LICENSE.txt)) | None | Compatible with GPL-3.0 (attribution/notice retention required) |
| `mil.nga:mgrs:2.1.3` | Direct (`:app`) | The MIT License (MIT) ([link](https://github.com/ngageoint/mgrs-java/blob/master/LICENSE.txt)) | None | Compatible with GPL-3.0 (attribution/notice retention required) |
| `mil.nga:sf:2.2.2` | Transitive (`mil.nga:mgrs:2.1.3` -> `grid`) | The MIT License (MIT) ([link](https://github.com/ngageoint/simple-features-java/blob/master/LICENSE.txt)) | None | Compatible with GPL-3.0 (attribution/notice retention required) |
| `org.checkerframework:checker-qual:3.37.0` | Transitive (`firebase-firestore` -> `guava:32.1.3-android`) | The MIT License ([link](http://opensource.org/licenses/MIT)) | `META-INF/LICENSE.txt` | Compatible with GPL-3.0 (attribution/notice retention required) |
| `org.codehaus.mojo:animal-sniffer-annotations:1.23` | Transitive (`firebase-firestore` -> `grpc-core` / `grpc-util`) | MIT license ([link](https://spdx.org/licenses/MIT.txt)) | None | Compatible with GPL-3.0 (attribution/notice retention required) |
| `org.maplibre.gl:android-sdk:13.3.1` | Direct (`:app`) | BSD ([link](https://opensource.org/licenses/BSD-2-Clause)) | None | Compatible with GPL-3.0 (attribution/notice retention required) |
| `org.maplibre.gl:maplibre-android-gestures:0.0.4` | Transitive (`org.maplibre.gl:android-sdk:13.3.1`) | BSD ([link](https://opensource.org/licenses/BSD-2-Clause)) | None | Compatible with GPL-3.0 (attribution/notice retention required) |

### 3.4 Notice-Carrying Apache-2.0 Runtime Archives in `:app` (`169` Total Apache-2.0 Artifacts)

All `169` remaining `:app` runtime artifacts declare Apache-2.0 (GPL-3.0-compatible, **subject to Apache-2.0 §4 notice/LICENSE preservation**), with two specific sub-notices highlighted:
- **`com.squareup.okhttp3:okhttp:4.12.0`:** Embeds `okhttp3/internal/publicsuffix/NOTICE` stating `publicsuffixes.gz` derives from Mozilla Public Suffix List under **MPL-2.0** (GPL-3.0 compatible; preserve PSL/MPL-2.0 notice).
- **AndroidX AARs & Firebase Apache-2.0 AARs:** `14` Apache-2.0 AndroidX AARs embed `META-INF/androidx/.../LICENSE.txt` (`15` total including BSD-3-Clause `datastore-preferences-external-protobuf`); `7` Apache-2.0 Firebase AARs embed `third_party_licenses.json` / `third_party_licenses.txt`.

<details>
<summary>Full list of all 169 resolved Apache-2.0 `:app` runtime artifacts (Play & Enterprise identical)</summary>

| Coordinate | Origin | POM License Name | Archive Notices |
|---|---|---|---|
| `androidx.activity:activity-compose:1.9.3` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.activity:activity-ktx:1.9.3` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.activity:activity:1.9.3` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.annotation:annotation-experimental:1.4.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.annotation:annotation-jvm:1.10.0` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/annotation/annotation/LICENSE.txt` |
| `androidx.annotation:annotation:1.10.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.appcompat:appcompat-resources:1.7.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.arch.core:core-common:2.2.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.arch.core:core-runtime:2.2.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.autofill:autofill:1.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.browser:browser:1.4.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.collection:collection-jvm:1.6.0` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/collection/collection/LICENSE.txt` |
| `androidx.collection:collection-ktx:1.6.0` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/collection/collection-ktx/LICENSE.txt` |
| `androidx.collection:collection:1.6.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.animation:animation-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.animation:animation-core-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.animation:animation-core:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.animation:animation:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.foundation:foundation-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.foundation:foundation-layout-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.foundation:foundation-layout:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.foundation:foundation:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material3:material3-android:1.3.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material3:material3:1.3.1` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material:material-icons-core-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material:material-icons-core:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material:material-icons-extended-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material:material-icons-extended:1.7.6` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material:material-ripple-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.material:material-ripple:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.runtime:runtime-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.runtime:runtime-saveable-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.runtime:runtime-saveable:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.runtime:runtime:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-geometry-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-geometry:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-graphics-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-graphics:1.7.6` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-text-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-text:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-tooling-preview-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-tooling-preview:1.7.6` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-unit-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-unit:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-util-android:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui-util:1.7.6` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.compose.ui:ui:1.7.6` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.compose:compose-bom:2024.12.01` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.concurrent:concurrent-futures:1.1.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.core:core-ktx:1.13.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.core:core-splashscreen:1.2.0` | Direct | The Apache Software License, Version 2.0 | `META-INF/androidx/core/core-splashscreen/LICENSE.txt` |
| `androidx.core:core:1.13.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.credentials:credentials-play-services-auth:1.2.0-rc01` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.credentials:credentials:1.2.0-rc01` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.customview:customview:1.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.datastore:datastore-android:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/datastore/datastore/LICENSE.txt` |
| `androidx.datastore:datastore-core-android:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/datastore/datastore-core/LICENSE.txt` |
| `androidx.datastore:datastore-core-okio-jvm:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/datastore/datastore-core-okio/LICENSE.txt` |
| `androidx.datastore:datastore-core-okio:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.datastore:datastore-core:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.datastore:datastore-preferences-android:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/datastore/datastore-preferences/LICENSE.txt` |
| `androidx.datastore:datastore-preferences-core-android:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/datastore/datastore-preferences-core/LICENSE.txt` |
| `androidx.datastore:datastore-preferences-core:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.datastore:datastore-preferences-proto:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/datastore/datastore-preferences-proto/LICENSE.txt` |
| `androidx.datastore:datastore-preferences:1.3.0-alpha11` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.datastore:datastore-tink-android:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/datastore/datastore-tink/LICENSE.txt` |
| `androidx.datastore:datastore-tink:1.3.0-alpha11` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.datastore:datastore:1.3.0-alpha11` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.emoji2:emoji2:1.3.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.fragment:fragment:1.8.9` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.graphics:graphics-path:1.0.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.interpolator:interpolator:1.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-common-java8:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-common-jvm:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-common:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-livedata-core-ktx:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-livedata-core:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-livedata:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-process:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-runtime-android:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-runtime-compose:2.8.7` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-runtime:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7` | Direct | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.lifecycle:lifecycle-viewmodel:2.8.7` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.loader:loader:1.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.localbroadcastmanager:localbroadcastmanager:1.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.profileinstaller:profileinstaller:1.3.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.savedstate:savedstate-ktx:1.2.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.savedstate:savedstate:1.2.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.sqlite:sqlite-android:2.5.1` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/sqlite/sqlite/LICENSE.txt` |
| `androidx.sqlite:sqlite-framework-android:2.5.1` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/sqlite/sqlite-framework/LICENSE.txt` |
| `androidx.sqlite:sqlite-framework:2.5.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.sqlite:sqlite:2.5.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.startup:startup-runtime:1.1.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.tracing:tracing-android:2.0.1` | Transitive | The Apache Software License, Version 2.0 | `META-INF/androidx/tracing/tracing/LICENSE.txt` |
| `androidx.tracing:tracing:2.0.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.vectordrawable:vectordrawable-animated:1.1.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.vectordrawable:vectordrawable:1.1.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `androidx.viewpager:viewpager:1.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `app.cash.sqldelight:android-driver:2.1.0` | Direct | Apache-2.0 | None |
| `app.cash.sqldelight:async-extensions-jvm:2.1.0` | Transitive | Apache-2.0 | None |
| `app.cash.sqldelight:async-extensions:2.1.0` | Transitive | Apache-2.0 | None |
| `app.cash.sqldelight:coroutines-extensions-jvm:2.1.0` | Transitive | Apache-2.0 | None |
| `app.cash.sqldelight:coroutines-extensions:2.1.0` | Direct | Apache-2.0 | None |
| `app.cash.sqldelight:runtime-jvm:2.1.0` | Transitive | Apache-2.0 | None |
| `app.cash.sqldelight:runtime:2.1.0` | Direct | Apache-2.0 | None |
| `com.google.android:annotations:4.1.1.4` | Transitive | Apache 2.0 | None |
| `com.google.code.findbugs:jsr305:3.0.2` | Transitive | The Apache Software License, Version 2.0 | None |
| `com.google.code.gson:gson:2.13.2` | Transitive | Apache-2.0 | None |
| `com.google.crypto.tink:tink-android:1.23.0` | Direct | Apache License, Version 2.0 | None |
| `com.google.errorprone:error_prone_annotations:2.41.0` | Transitive | Apache 2.0 | None |
| `com.google.firebase:firebase-annotations:16.2.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `com.google.firebase:firebase-appcheck-interop:17.0.0` | Transitive | The Apache Software License, Version 2.0 | `third_party_licenses.json`, `third_party_licenses.txt` |
| `com.google.firebase:firebase-bom:33.16.0` | Direct | The Apache Software License, Version 2.0 | None |
| `com.google.firebase:firebase-common-ktx:21.0.0` | Transitive | The Apache Software License, Version 2.0 | `third_party_licenses.json`, `third_party_licenses.txt` |
| `com.google.firebase:firebase-common:21.0.0` | Transitive | The Apache Software License, Version 2.0 | `third_party_licenses.json`, `third_party_licenses.txt` |
| `com.google.firebase:firebase-components:18.0.0` | Transitive | The Apache Software License, Version 2.0 | `third_party_licenses.json`, `third_party_licenses.txt` |
| `com.google.firebase:firebase-database-collection:18.0.1` | Transitive | The Apache Software License, Version 2.0 | `third_party_licenses.json`, `third_party_licenses.txt` |
| `com.google.firebase:firebase-firestore:25.1.4` | Direct | The Apache Software License, Version 2.0 | `third_party_licenses.json`, `third_party_licenses.txt` |
| `com.google.firebase:protolite-well-known-types:18.0.1` | Transitive | The Apache Software License, Version 2.0 | `third_party_licenses.json`, `third_party_licenses.txt` |
| `com.google.guava:failureaccess:1.0.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `com.google.guava:guava:32.1.3-android` | Transitive | Apache License, Version 2.0 | None |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | Transitive | The Apache Software License, Version 2.0 | None |
| `com.jakewharton.timber:timber:5.0.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `com.squareup.okhttp3:okhttp:4.12.0` | Transitive | The Apache Software License, Version 2.0 | `okhttp3/internal/publicsuffix/NOTICE` |
| `com.squareup.okio:okio-jvm:3.17.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `com.squareup.okio:okio:3.17.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `com.squareup.wire:wire-runtime-jvm:6.4.7` | Transitive | Apache-2.0 | None |
| `com.squareup.wire:wire-runtime:6.4.7` | Direct | Apache-2.0 | None |
| `io.grpc:grpc-android:1.62.2` | Transitive | Apache 2.0 | None |
| `io.grpc:grpc-api:1.62.2` | Transitive | Apache 2.0 | None |
| `io.grpc:grpc-context:1.62.2` | Transitive | Apache 2.0 | None |
| `io.grpc:grpc-core:1.62.2` | Transitive | Apache 2.0 | None |
| `io.grpc:grpc-okhttp:1.62.2` | Transitive | Apache 2.0 | None |
| `io.grpc:grpc-protobuf-lite:1.62.2` | Transitive | Apache 2.0 | None |
| `io.grpc:grpc-stub:1.62.2` | Transitive | Apache 2.0 | None |
| `io.grpc:grpc-util:1.62.2` | Transitive | Apache 2.0 | None |
| `io.perfmark:perfmark-api:0.26.0` | Transitive | Apache 2.0 | None |
| `javax.inject:javax.inject:1` | Transitive | The Apache Software License, Version 2.0 | None |
| `org.jetbrains.kotlin:kotlin-stdlib-common:2.2.21` | Transitive | Apache-2.0 | None |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22` | Transitive | The Apache License, Version 2.0 | None |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22` | Transitive | The Apache License, Version 2.0 | None |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.21` | Direct | Apache-2.0 | None |
| `org.jetbrains.kotlinx:atomicfu-jvm:0.28.0` | Transitive | Apache-2.0 | None |
| `org.jetbrains.kotlinx:atomicfu:0.28.0` | Transitive | Apache-2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1` | Direct | The Apache Software License, Version 2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.9.0` | Transitive | Apache-2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0` | Transitive | Apache-2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0` | Transitive | Apache-2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0` | Transitive | Apache-2.0 | None |
| `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0` | Direct | Apache-2.0 | None |
| `org.jetbrains:annotations:23.0.0` | Transitive | The Apache Software License, Version 2.0 | None |
| `org.jspecify:jspecify:1.0.0` | Transitive | The Apache License, Version 2.0 | None |
| `org.maplibre.gl:android-sdk-geojson:6.0.1` | Transitive | The Apache Software License, Version 2.0 | None |
| `org.maplibre.gl:android-sdk-turf:6.0.1` | Transitive | The Apache Software License, Version 2.0 | None |

</details>

## 4. Phase A2 — Generated Protobuf Code & Obligations Assessment

### 4.1 Meshtastic Schemas -> Square Wire Output -> `:atlas-sdk` Artifact

| Stage | Inspected Artifact / Path | Observed Evidence |
|---|---|---|
| Upstream inputs | `apps/atlas-android/atlas-sdk/src/main/proto/meshtastic/` | `10` `.proto` files (`319,050` bytes total per `UPSTREAM_PIN.json`) copied verbatim from `meshtastic/protobufs` `v2.8.2` (`ffd5ad42`), plus verbatim `LICENSE-GPL-3.0.txt`. |
| Codegen tool | `com.squareup.wire:wire-gradle-plugin:6.4.7` + `wire-kotlin-generator:6.4.7` | Wire compiler itself is `Apache-2.0` and does not impose its own license on generated output; generated code inherits obligations from schema inputs. |
| Generated Kotlin | `apps/atlas-android/atlas-sdk/build/generated/source/wire/` | **`107` generated `.kt` files (`43,707` lines)** in `org.meshtastic.proto.*`. **`105` of `107` files include `/** ... */` prose KDoc copied from `.proto` comments** (e.g. `PortNum.kt`: `216`/`337` doc lines; `FromRadio.kt`: `89`/`591`; `ToRadio.kt`: `31`/`251`), plus exact package/type/field identifiers. |
| Published SDK JAR | `atlas-sdk-1.1.0.jar` | Contains **`573` `org/meshtastic/proto/*.class`** + `181` `com/sovereignatlas/**/*.class`. **`0` `.proto` files** inside the JAR. **`0` `LICENSE`/`NOTICE` files** in `META-INF/`. `atlas-sdk-1.1.0.pom` omits `<licenses>` / `<scm>`. |
| `:app` TAK schema (unrelated) | `apps/atlas-android/app/src/main/proto/takmessage.proto` | Separate pre-existing proto (`atakmap.commoncommo.v1`, commit `e385670`) generating `18` Wire `.kt` files in `:app` (`9` per flavor). **No copyright/license header in file or SBOM (`Unknown`).** |

### 4.2 Legal / Compatibility Chain Analysis (`source -> generated -> JAR -> combined APK`)

1. **Protected expression IS present in Wire inputs and generated Kotlin source:** Even without bundling raw `.proto` files into `atlas-sdk-1.1.0.jar`, `105` generated `.kt` source files reproduce upstream prose KDoc comments verbatim alongside Wire's structural translation of `mesh.proto`, `portnums.proto`, `config.proto`, etc. Under GNU GPL-3.0, the compiled bytecode (`org/meshtastic/proto/*.class`) in `atlas-sdk-1.1.0.jar` and `:app` APKs derives directly from those `GPL-3.0` `.proto` inputs.
2. **Meshtastic SDK ADR-004 distinction:** Upstream's ADR-004 affirms the wire protocol (wire IDs/bytes on the air) is non-copyrightable while treating the `.proto` schema files—and Meshtastic's own SDK compiling them—as `GPL-3.0`. That distinction does **not** relicense the copied `meshtastic/protobufs` `.proto` files or generated `org.meshtastic.proto.*` files inside `:atlas-sdk` away from `GPL-3.0`.
3. **`:atlas-sdk` Standalone Publication (`com.sovereignatlas:atlas-sdk:1.1.0`):**
   - **Internal dependency compatibility:** `14/14` third-party runtime artifacts are `Apache-2.0` (compatible with GPL-3.0).
   - **Architectural friction:** Distributing `:atlas-sdk` publicly under GPL-3.0 means downstream consumers linking `com.sovereignatlas:atlas-sdk:1.1.0` (including `Sovereign Mantle` or other consumers) face GPL-3.0 whole-work obligations unless isolated under Option B or Option C.
4. **`:app` Combined Distribution (`PlayRelease` & `EnterpriseRelease` APKs):**
   - Both flavors bundle **`13` proprietary closed-source Google/Play binary AARs** (`Android SDK License`, `Play Core SDK ToS`, `Play Integrity API ToS`) compiled directly into the APK.
   - Under GPL-3.0 §§ 5(c) and 6, you cannot distribute a combined work linking proprietary AARs that forbid source availability / GPL sublicensing (the GPL §1 System Library exception does not ordinarily cover third-party Maven AARs statically packaged into the APK DEX).
   - **Conclusion on Option A Feasibility:** Public redistribution of the **current** combined `Play`/`Enterprise` APKs while linking GPL-3.0-derived `org.meshtastic.proto.*` classes faces a concrete **GPL-3.0 incompatibility blocker** unless either (a) a GPL-clean APK flavor omits Firebase Auth/Firestore & Play/GMS proprietary libraries, (b) Option **B** obtains permissive/commercial permission from Meshtastic rights holders for the `.proto` schema use, or (c) Option **C** implements an independently developed wire-compatible codec under strict clean-room provenance.

## 5. Phase A3 Baseline Facts (Pending Owner / Legal Decision)

Observed repository ownership/licensing state before any edits:
- **Root repository `LICENSE` / `NOTICE` / `COPYING`:** None present (`0` files).
- **Published Maven POM `<licenses>` / `<scm>`:** Absent on `com.sovereignatlas:atlas-sdk:1.1.0`.
- **Git commit authorship (`git shortlog -sne HEAD`):** `Sovereign Mantle <sovereign@example.com>` (`263` commits), `GhostMan612 <85476477+GhostMan612@users.noreply.github.com>` (`19` commits).
- **`takmessage.proto` provenance:** Introduced in `e385670` without a copyright or upstream license header; needs provenance documentation alongside `A3`.
- **Open questions required to unlock `A3` / `A4`:**
  1. Who holds copyright on `Sovereign-Atlas-Engine`, and what outbound license is intended for `:atlas-sdk` (must `Sovereign Mantle` consume `:atlas-sdk` permissively / proprietarily, or under GPL-3.0)?
  2. Should Option A continue **only** for a GMS-free FOSS build (removing `firebase-auth`/`firebase-firestore` proprietary AARs from `Enterprise` or `:app`), OR pivot to **Option B** (Meshtastic permission/dual-license request) or **Option C** (clean-room independent protocol codec under `#16` §4 provenance rules)?
  3. Once `A3` is decided, Phase `A4` will implement `LICENSE`/`NOTICE` bundling, POM metadata, Corresponding Source delivery (if GPL), updated `DATA_LICENSE_SECURITY_SBOM.md` versions, `takmessage.proto` attribution, and automated release-packaging license checks (`A5`).
