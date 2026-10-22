# ADR-008: Standalone Sovereign Atlas SDK Architecture and Independent Consumer Verification

## Status
ACCEPTED / IMPLEMENTED

## Context
In Work Order 03 (Premium UX & Reusable SDK), the `com.sovereignatlas.sdk.AtlasSdk` facade was introduced to provide public access to Sovereign Atlas's Spatial, Navigation, Layers, and Knowledge capabilities. However, all contracts and the facade were initially situated within the `:app` Android application module, and verified via `SdkConsumerVerificationTest.kt` inside the host test tree.

An Android application module (`com.android.application`) produces an APK, not a consumable library artifact (AAR or JAR). Consequently, external applications cannot declare a Gradle dependency on `:app`. Furthermore, in-tree unit tests do not establish that an independent consumer project can resolve, compile, and execute against the SDK without transitively pulling in Android UI, Jetpack Compose, or MapLibre dependencies.

To establish genuine SDK independence, the repository requires:
1. A publishable Kotlin/Android library artifact.
2. Explicit, decoupled dependency boundaries with zero platform leaks.
3. A separate external Gradle consumer project.
4. A verified external consumer compile and test gate.

## Decision
1. **Standalone SDK Module (`:atlas-sdk`):**
   - Create a dedicated library module at `apps/atlas-android/atlas-sdk/`.
   - Configure `:atlas-sdk` as a pure Kotlin/JVM library using `java-library`, `org.jetbrains.kotlin.jvm` (v2.1.0), and `maven-publish`.
   - Publish coordinates: `com.sovereignatlas:atlas-sdk:1.0.0`.
   - Pure domain packages (`core`, `geo`, `layers/compositor`, `core/knowledge`, `geo/navigation`, and `com.sovereignatlas.sdk`) are housed in `:atlas-sdk`.
   - ZERO Android framework, Jetpack Compose, or MapLibre Native imports are permitted. Purity is enforced at compile time by the Kotlin JVM compiler (these dependencies do not exist on the module classpath).

2. **Host Application Integration (`:app`):**
   - The Android host application `:app` declares `implementation project(':atlas-sdk')`.
   - UI layers, Compose screens, MapLibre renderers, and hardware sensors in `:app` consume domain models and navigation sessions via the SDK library.

3. **Independent External Consumer (`samples/sdk-consumer`):**
   - Create a standalone Gradle consumer project at `samples/sdk-consumer/`.
   - Configured with its own `settings.gradle` and `build.gradle`, consuming `com.sovereignatlas:atlas-sdk:1.0.0`.
   - Contains `ExternalSdkConsumerTest.kt` exercising spatial calculations, road route planning, multi-layer composition, attribution rollups, and historical knowledge queries.
   - Runs independently of `:app` and proves that an external JVM application can consume the SDK without any Android framework dependencies.

## Consequences
- **Positive:**
  - True physical decoupling of pure domain logic from Android host application code.
  - Compile-time guarantee of domain purity.
  - Reusable across any JVM runtime (Android, desktop, backend services, edge CLI).
  - Clean separation between internal host tests and genuine external consumer verification.
- **Negative / Neutral:**
  - Multi-project Gradle build configuration requires maintaining `:atlas-sdk` and `samples/sdk-consumer`.
