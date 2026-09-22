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

Host gate (no device, no build):

```powershell
# from apps/atlas-android with JAVA_HOME + ANDROID_HOME set
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

184/184 unit tests green at last gate. The human builds in Android
Studio; device runs are explicit-ask only.

## Lineage

`blueprints/` + `docs/` preserve the retired Dart/Flutter history
(engine packages, golden fixtures, slice closures, ADRs/DECs) as
read-only context. The MGRS-001 golden stays schema-only by
governance; terrain-aware LOS is reserved to a future DEM source
(RADIO-002). Phase 13 (productization/release) was never executed.
