# DEC-021 — Foreground Location Slice 1 (Acquisition + Marker + Recenter)

- **Status:** Accepted (Phase B Slice 1 implementation prompt; governed by the
  Phase B forensic/integration inventory final report).
- **Date:** 2026-09-11.
- **Baseline:** `1f36043` (clean, `origin/main == 1f36043` verified pre-flight).
- **Scope:** Host application only (`apps/atlas` Dart + Android host). No file
  under `packages/*/lib/` is modified. No new engine contract is created.
- **Identifier derivation (not invented):** `docs/architecture/` contains
  ADR-001..005 and exactly one DEC, DEC-020; repository-wide grep proves no
  DEC-021 reference exists. DEC-001..019 live outside this tree and stay open.
  Next in-repo DEC is therefore 021 by evidence, not by choice.
- **Relation to DEC-012 (external, OPEN):** DEC-012 (location freshness +
  heading truth) stays open and unmodified. This record decides only
  slice-local app policy; the engine-level freshness contract is untouched.

## 1. Context

The forensic inventory proved the engine owns complete location *values*
(`AtlasLocationFix{position, at, accuracyMeters?, speedMetersPerSecond?,
headingDeg?, source}`, `AtlasHeading`, `AtlasTrackLog`) while the app has no
acquisition seam at all: no permissions in any manifest (main manifest zero,
debug/profile INTERNET only), a `FlutterActivity` subclass with no channels,
an instantiated-but-never-driven `MapController`
(`apps/atlas/lib/main.dart:77`; zero `move`/`rotate`/`fitBounds` calls), one
map-center echo pin that is not a device fix, and no location/permission/
sensor dependency in `apps/atlas/pubspec.yaml`. The location-contract
(`blueprints/phase-0/location-contract.md`) is PROPOSED with thresholds
explicitly open ("observed data points ... are source facts, not Atlas
thresholds"), and LOC-002 records thresholds as `DEC-012 (open)`. Normative
precedent from that contract: denied vs disabled are distinct states; no fake
fix; implementation is adapter-resident; RES-002 forbids engine-global
fallback coordinates.

## 2. Decision

**A. Acquisition mechanism: host-side Android platform channel on the
framework `android.location.LocationManager`, consumed by a host-side Dart
adapter. No new pub dependency. No new Gradle dependency.**

Concrete shape:

1. `apps/atlas/android/.../LocationChannel.kt` (new, host-only): a
   `MethodChannel` (`com.sovereignatlas.atlas/location`) for
   `getStatus` / `requestPermission` / `openAppSettings` / `getLastKnownFix`,
   plus an `EventChannel` (`com.sovereignatlas.atlas/location_stream`) that
   emits fix maps from a `LocationListener` on GPS + NETWORK providers at
   2000 ms / 0 m (the Mantle-observed cadence recorded in the
   location-contract). `MainActivity` only wires the channels
   (`configureFlutterEngine`, permission-result forwarding); it owns no
   location logic.
2. `apps/atlas/lib/location/location_service.dart` (new, host-only):
   `AtlasLocationSource` (injectable; channel vs fake implementations),
   `LocationService extends ChangeNotifier` (permission state, latest fix,
   receipt clock, stale computation, idempotent start, disposal).
3. Fix maps carry latitude, longitude, nullable accuracy/speed/bearing,
   platform time, provider name. The channel impl parses them into the
   *existing* `AtlasLocationFix` (plus `atlas_location` as a manifest-level
   `path:` dependency only). No engine file changes; no engine provider,
   stream, `DateTime`, altitude, or frame is added.

**B. Permission model (explicit states, never a boolean):**
`notRequested | denied | permanentlyDenied | granted`, orthogonal to
`servicesEnabled` (any provider enabled). Derived UI status:
`notRequested → denied → permanentlyDenied → servicesDisabled →
acquiring (granted, no fix yet) → valid → stale → error`. Permanently-denied
is detected as: a request was made in this process AND the grant was denied
AND `shouldShowRequestPermissionRationale` is false (pre-API-23 devices
report granted — install-time model — by version guard). Denied-forever
offers the app-settings path (`ACTION_APPLICATION_DETAILS_SETTINGS`,
fire-and-forget; the record claims "launched", never "opened
successfully"). Foreground-only: `ACCESS_FINE_LOCATION` +
`ACCESS_COARSE_LOCATION` in the main manifest, nothing else. Approximate
(Android 12+) grants are honored implicitly — reduced precision arrives via
`accuracyMeters` and is displayed honestly; no API-31-specific branching.
Background location, foreground services, and background tracking are out.

**C. Position semantics:** consume `AtlasLocationFix` unmodified. Host-local
convention (not an engine claim): `at` carries the platform fix time in
milliseconds since Unix epoch as reported by Android. Staleness is computed
from the Dart-side receipt clock, never from `at`, so no engine timestamp
semantics are depended upon or defined. `accuracyMeters == null` means
UNKNOWN, displayed verbatim — never 0, never a default radius, never an
invented circle. `headingDeg`/`speedMetersPerSecond` are carried, never
shown as compass heading in this slice.

**D. No fallback coordinates:** with no valid fix the app renders no
position marker and moves no camera. The map keeps its existing camera
state, which is labeled MAP and never presented as the user. RES-002 holds.

**E. Offline behavior:** acquisition degrades to the explicit states above;
no-network never produces a coordinate and tile acquisition is untouched.

**F. Lifecycle:** foreground-only. `start()` is idempotent (single
subscription; no subscription from `build()`); `dispose()` removes updates
and cancels the subscription. No recording, no GPX, no geofencing, no
follow mode, no mock-flag surfacing (deferred: `isMock()` is API-31+ and
the slice refuses version-branched Kotlin; provider name travels as
`source`, which preserves provenance without the flag).

**G. Slice-local stale policy (decision with rationale):** a fix is STALE
when its age reaches `kLocationStaleAfterSeconds = 30`. Rationale: updates
are requested at 2 s cadence, so 30 s means ~15 consecutive missed updates —
unambiguously not live — while short enough for an operator to reproduce in
manual smoke; it matches the order of magnitude of the existing app policy
`kDefaultPerTileTimeout = 30 s` (DEC-020), keeping app-side time bounds
consistent; the last-known fix stays visible labeled STALE per the
location-contract correction (adapters choose display defaults; core
invents nothing). The 5-minute last-known observation stays a source fact,
not a threshold. DEC-012 remains the owner of any engine-level contract.

**H. Camera/readout/north-up:** recenter drives the *existing*
`MapController.move(target, currentZoom)` — zoom is preserved, no zoom
policy is invented; recenter with no fix moves nothing. `MapOptions`
declares `initialRotation: 0.0` and slice 1 never calls `rotate`, so
bearing stays 0 (north-up asserted by construction; verified by test, not
by pixels). The readout gains a DEVICE line distinct from the MAP line.
Feedback for actions is the persistent status line, not transient UI.

**I. Verification:** `flutter analyze --no-pub` + `flutter test test/`
(host gates; engine suite untouched by design — no engine file changes, so
re-running 453 fixtures proves nothing about this slice and is reserved for
staged-commit verification per RULES §1A). Widget/unit tests with an
injectable fake source cover all mandated states (see §4). No
`integration_test/` addition (no device-only behavior). No DEVICE-00X claim
from this record; manual smoke matrix is specified in §5 for the human's
Android Studio run.

## 3. Consequences

- Zero new dependencies (pub or Gradle): no supply-chain, version-ceiling,
  or offline-resolution risk; `flutter pub get` resolves locally (one
  `path:` line for `atlas_location`).
- One injectable host seam (`AtlasLocationSource`) unlocks slices 2+
  (heading, follow, recording) without revisiting acquisition.
- Known residuals, all explicit: emulator fixes are operator-injected, so
  smoke proves plumbing, not GPS accuracy; the stale flip rides one
  single-shot timer armed per fix (cancelled on dispose or superseding
  fix); process death resets the requested-before flag (permanently-denied then reads as
  denied until the next request — honest, documented, acceptable for slice
  1); `isMock` deferred as decided above.

## 4. Alternatives rejected (evidence)

- **geolocator + permission_handler plugins:** full-featured but unjustified
  here — two hosted dependencies plus transitive Play-services Gradle weight
  for foreground fixes the framework already provides; version compatibility
  with Flutter 3.47 / Dart 3.13 / the SDK-pinned ceiling unverifiable
  without network resolution; violates "smallest valid seam" and RULES §3
  (new deps need written justification — this record is the written
  rejection).
- **FusedLocationProviderClient via channel:** better battery/quality, but
  requires a Play-services Gradle dependency and emulator images without
  Play services would break smoke; LocationManager works on every image
  (emulator geo-fix drives the GPS provider). The channel method names are
  provider-agnostic, so a fused swap later touches Kotlin only.
- **Polling `getLastKnownPosition` on a timer:** no live updates, timer
  churn, still needs permissions — strictly worse than the event stream.
- **Reusing the map-center pin as the fix marker:** rejected — conflates MAP
  with DEVICE and would violate §2.D the first time no fix exists.
- **Forcing a recenter zoom (e.g. 14):** rejected — an invented camera
  policy with no evidence; zoom preservation is the neutral choice.

## 5. Manual smoke matrix (human, Android Studio; emulator fixes injected)

1. Fresh launch → DEVICE reads not-requested; no blue marker; MAP line only.
2. Tap my-location → system permission sheet; grant precise → acquiring →
   valid fix appears (inject via emulator geo-fix); blue marker distinct
   from red center pin; DEVICE line shows lat/lon/accuracy.
3. Tap my-location with valid fix → camera centers on fix; zoom unchanged;
   rotation stays 0.
4. New fix while panning → marker moves; camera does NOT follow; rotation
   stays 0.
5. Deny → denied state, no marker, recenter tap changes nothing.
6. Deny twice / "don't ask again" → permanently-denied state + settings
   path offered.
7. Disable location services → services-disabled state.
8. Granted, no injection → acquiring persists; no fake marker.
9. Wait 30 s past last fix → STALE label with last-known retained.
10. Null-accuracy injection (if reproducible) → UNKNOWN; never a circle.
11. In every unavailable state: no blue marker, no camera jump.
