# Sovereign Atlas

Native Kotlin + MapLibre mapping/atlas application for Android, descended
from the Sovereign Mantle TacMap lineage. Single host app in
`apps/atlas-android/` — no other runtimes, no cross-platform layer.

## What it does

Offline-first field mapping: GPS location + compass heading, measure,
waypoints + journal, track recording, Go-To navigation, offline tile
packs (plan/download/render from an embedded localhost tile server),
base-layer picker (OSM, Esri, OpenTopoMap, USGS, CARTO), graticule,
range rings, radio link calculator, session geofence, GPX export,
follow mode.

## Architecture

Pure-logic modules (`core/`, `geo/`, `camera/`, `layers/`, `field/`,
`measure/`, `goto/`, `track/`, `offline/`, `tactical/`) carry zero
MapLibre/Android/Compose imports. Rendering and platform sources live
in `map/` + `ui/` + `location/` + `heading/`. Boundary rules in
`ARCHITECTURE.md`; operating law in `RULES.md`; live state in
`SESSION_HANDOFF.md`.

## Verification

Host gate (no device, no build), **once per phase**:

```powershell
# from apps/atlas-android with JAVA_HOME + ANDROID_HOME set
# Prefer the atlas_gates tool: it sets both variables and reports real counts.
.\gradlew.bat :atlas-sdk:test :sdk-consumer:test :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest --console=plain
```

There is no `:app:testDebugUnitTest` — the two product flavors make that name
ambiguous, and Gradle fails on it. The human builds in Android Studio; device runs
are explicit-ask only. This canonical host gate matches the GitHub Actions `verify` job;
GitHub Actions also runs an isolated binary-artifact job (`sdk-consumer-maven`)
that publishes `com.sovereignatlas:atlas-sdk:1.1.0` to a temporary repository and tests
[`samples/sdk-consumer`](samples/sdk-consumer/README.md) from an isolated directory.
See `AGENTS.md` for the one-gate-per-phase local rule.

## Release Packaging

Release distribution remains **NO-GO** pending rights review and an independently controlled publisher. No workflow in this checkout signs or publishes a release. The sole active workflow is `.github/workflows/atlas-ci-safe.yml` (development verification only: host suites, A4 candidate checks, distribution-hold checks and the isolated Maven consumer); its `verify` and `sdk-consumer-maven` jobs are green on `main`. The historical `atlas.yaml` workflow was removed locally in `90c4ca1`; it is not restored by changing this checkout, and any historical refs or credentials still require external revocation.

Release APK builds remain subject to `RULES.md` §1.6. The Gradle signing guard is not distribution authorization. Do not add signing credentials to the candidate repository or re-enable the historical workflow. See `docs/atlas-production/evidence/DISTRIBUTION_GATE.md` for the unresolved release controls.

## Lineage

`blueprints/` + `docs/` preserve the retired Dart/Flutter history
(engine packages, golden fixtures, slice closures, ADRs/DECs) as
read-only context. The MGRS-001 golden stays schema-only by
governance; terrain-aware LOS is reserved to a future DEM source
(RADIO-002). Phase 13's tactical mesh and SDK implementation is recorded as complete in
`docs/atlas-production/evidence/RELEASE_MANIFEST_PHASE13.md`. Live release runs require configured repository signing secrets (`G12`), and live hardware validation remains pending.
