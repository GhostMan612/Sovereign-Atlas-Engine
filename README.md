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
.\gradlew.bat :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest --console=plain
```

There is no `:app:testDebugUnitTest` — the two product flavors make that name
ambiguous, and Gradle fails on it. The human builds in Android Studio; device runs
are explicit-ask only. GitHub Actions additionally runs `:atlas-sdk:test`, in-tree
`:sdk-consumer:test`, and a standalone Maven binary-artifact job (`sdk-consumer-maven`)
that publishes `com.sovereignatlas:atlas-sdk:1.1.0` to a temporary repository and tests
[`samples/sdk-consumer`](samples/sdk-consumer/README.md) from an isolated directory.
See `AGENTS.md` for the one-gate-per-phase local rule.

## Release Packaging

Release APK builds remain gated by `RULES.md` §1.6: AI sessions here run host unit tests only and never assemble release APKs or store keystore secrets. Humans build/sign locally in Android Studio using gitignored `apps/atlas-android/app/keystore.properties` and `apps/atlas-android/app/google-services.json` (or `-PKEYSTORE_*` / `KEYSTORE_*` environment variables).

Automated signed releases run in `.github/workflows/atlas.yaml` (`release` job, gated by `needs: [verify, sdk-consumer-maven]`):

- **Triggers:** semver tag pushes (`v*`, validated as `vMAJOR.MINOR.PATCH[-PRERELEASE]`) or manual `workflow_dispatch` restricted strictly to `refs/heads/main`.
- **Fail-closed signing:** `apps/atlas-android/app/build.gradle` rejects any release task before packaging if `storeFile`/`KEYSTORE_PATH`, `storePassword`/`KEYSTORE_PASSWORD`, `keyAlias`/`KEY_ALIAS`, or `keyPassword`/`KEY_PASSWORD` is missing/empty or the keystore file is missing/empty. No unsigned APKs are ever published.
- **Verification & delivery:** release builds run with `set +x`, `umask 077`, and exit cleanup of ephemeral keystore/Firebase files; verify both `app-play-release.apk` and `app-enterprise-release.apk` with `apksigner verify --verbose --print-certs`; check APK entries for zero secret artifacts; emit `SHA256SUMS.txt` and `release-manifest.json`; upload with `if-no-files-found: error` (`retention-days: 90`); and publish a GitHub Release for semver tags. Manual `workflow_dispatch` with `dry_run=true` verifies the fail-closed Gradle guard safely without credentials or APK output.

## Lineage

`blueprints/` + `docs/` preserve the retired Dart/Flutter history
(engine packages, golden fixtures, slice closures, ADRs/DECs) as
read-only context. The MGRS-001 golden stays schema-only by
governance; terrain-aware LOS is reserved to a future DEM source
(RADIO-002). Phase 13's tactical mesh and SDK implementation is recorded as complete in
`docs/atlas-production/evidence/RELEASE_MANIFEST_PHASE13.md`. Live release runs require configured repository signing secrets (`G12`), and live hardware validation remains pending.
