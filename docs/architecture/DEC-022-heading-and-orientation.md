# DEC-022 — Heading Acquisition + Orientation (Slices 2+3 Combined)

- **Status:** Accepted (combined Slices 2+3 implementation authorized; follows
  the Slice 1 dependency graph: acquisition before presentation).
- **Date:** 2026-09-11.
- **Baseline:** `e8d6211` (verified clean pre-flight).
- **Scope:** Host application only (`apps/atlas` Dart + Android host). No file
  under `packages/*/lib/` is modified. No new engine contract is created.
  `AtlasHeading{degrees, source}` + `normalized` are consumed unmodified;
  engine gains no frame tag, accuracy, or timestamp (forbidden by the slice
  mandate).
- **Identifier derivation (not invented):** in-repo max is DEC-021
  (Slice 1); repository-wide grep proves no DEC-022 reference exists.
  DEC-001..019 and DEC-012 stay outside/open and unmodified.
- **Follow policy is EXCLUDED** ("eventually" per the authorized graph):
  heading updates never move the camera center; heading-up rotates in place
  only. Follow mode needs its own camera-policy decision later.

## 1. Context

Slice 1 (DEC-021) delivered the fix stream; the compass had no source.
The engine owns a frame-less heading value (`AtlasHeading`, snapshot,
degrees + `normalized`, no accuracy/timestamp/frame). The platform facts
below are verified against the project's own SDK, not memory: `javap` on
`C:\android\sdk\platforms\android-36\android.jar` proves
`Sensor.getDefaultSensor(int)`, `registerListener` overloads,
`getRotationMatrixFromVector`, `getOrientation`, `remapCoordinateSystem`,
`SENSOR_STATUS_{HIGH,MEDIUM,LOW,UNRELIABLE,NO_CONTACT}`,
`SensorEvent{values,accuracy,timestamp}`, and
`GeomagneticField(float,float,float,long)` + `getDeclination()`; the
android-36 platform sources
(`sdk\sources\android-36\...\SensorManager.java:1466-1467`) prove the
`getOrientation` azimuth is the angle between the device Y axis and the
**magnetic north pole**. Magnetic reference is therefore source-verified.

## 2. Decision

**A. Sensor source: `TYPE_ROTATION_VECTOR` through a new host-side
`HeadingChannel`, no new dependencies.** Methods
`com.sovereignatlas.atlas/heading`: `start` / `stop` / `status`;
events `com.sovereignatlas.atlas/heading_stream` carrying magnetic degrees
(azimuth rad→deg, normalized 0–360), sensor accuracy int, and event time.
Rotation vector is the platform-fused orientation source, so no manual
accelerometer+magnetometer fusion is written. Absence of the sensor
(common on minimal emulator images) is an explicit `unsupported` state,
not a silent zero. Sampling rate `SENSOR_DELAY_UI`: compass-display rate
is the honest need; game rate would burn battery for no display gain.
`MainActivity` only wires the channel, mirroring the location wiring.

**B. Frames (explicit):** sensor azimuth is MAGNETIC (proven §1).
True heading = magnetic + declination, declination from `GeomagneticField`
evaluated Kotlin-side per sensor event at the last-known location with
**altitude fixed at 0 m — a documented approximation, not a measurement**:
declination's altitude sensitivity is negligible for surface compass
display, while the alternatives (requiring fix altitude the engine
deliberately lacks, or a barometer — new sensor scope) are worse. Display
rounds to whole degrees so no false precision is presented. With no
location available, true is null and the UI labels the reading MAGNETIC;
with location, TRUE. Neither frame is ever presented as the other.

**C. Accuracy:** sensor accuracy int maps to
`unknown | high | medium | low | unreliable` (`NO_CONTACT` and any
unexpected code map to `unreliable` — fail dim, never fail bright).
Pre-first-event reads `unknown`. Compass renders full-brightness only on
high/medium and dims otherwise, following the UNRELIABLE-dimming
precedent recorded normative in `location-contract.md` §4. Accuracy lives
host-side in adapter state; the engine value is untouched.

**D. Timestamp correlation (no invented clock mapping):**
`SensorEvent.timestamp` is boot-nanos — a different clock than the fix
`at` (unix ms) — and this record invents no conversion between them. The
adapter stamps Dart receipt wall-clock on both fix and heading samples
(one shared clock), which is the join key later slices use; the sensor
tNanos travels opaquely for future use. Correlation in this slice =
receipt-time coexistence, nothing stronger, nothing claimed.

**E. Course-vs-heading distinction (normative for this slice):**
`AtlasLocationFix.headingDeg` is course-over-ground (motion-derived, null
when stationary); the compass shows the sensor only. No course display is
built, so the two cannot be confused in UI copy.

**F. Orientation UX (Slice 3, host-only):** a compass dial overlay
(N/E/S/W ring + needle, preferred frame labeled TRUE/MAG) appears when
heading is active; **tapping the compass is the Face North control**
(`rotate(0)`, north-up mode) — no separate button, fewest controls. A
heading-up toggle (tooltip `heading-up`) switches orientation mode.
Heading-up drives `controller.rotate(-trueDeg normalized to [0,360))`:
physical reasoning — facing heading H, the world must appear rotated
counterclockwise by H to put H up, and flutter_map positive rotation is
clockwise (0° = North per the installed 8.3.2 API docs) — so rotation =
−H. Widget tests assert this convention deterministically (90° in →
270° rotation; Face North → 0). Leaving heading-up calls `rotate(0)`.
North-up remains the default; location updates still never rotate (§2 of
DEC-021 holds — only heading updates rotate, only in heading-up mode).
Readout gains an `ORIENT` line (`off | north-up | heading-up`).

**G. Verification:** `flutter analyze --no-pub` + `flutter test test/`
with an injectable fake heading source (states, accuracy mapping,
null-true handling, rotation contract, mode transitions, cleanup,
no-duplicate-subscription). Engine suite at staged-commit verification
per RULES §1A (no engine files change, so it witnesses no-change).
No `integration_test/` (no device-only behavior). No DEVICE-00X claim;
manual smoke matrix in §5 for the human's run.

## 3. Consequences

- Zero new dependencies (pub or Gradle); manifest unchanged (no location
  permission covers sensors; no sensor permission exists to declare).
- `HeadingService` (ChangeNotifier, injectable source) parallels
  `LocationService`; orientation mode lives in page state (no new
  state-management package).
- Known residuals, all explicit: emulator images may report `unsupported`
  (itself smoke-testable); declination tracks the last-known location
  coarsely as the device moves (recomputed per event; spatial drift of
  declination is slow — acceptable for a compass, stated not hidden);
  altitude-0 approximation per §2.B; process death resets channel state.
- Azimuth is device-frame with no screen-rotation remap: portrait-primary.
  A rotated display shows uncorrected azimuth — stated, acceptable for a
  dial whose failure mode is a constant offset, not silent invention.

## 4. Alternatives rejected (evidence)

- **Sensor/compass plugins:** same rejection as DEC-021 §4 — unjustified
  hosted weight for framework-covered behavior.
- **Manual accel+magnetometer fusion:** more code for what the rotation
  vector already fuses; rejected on smallest-seam grounds.
- **Separate Face North button:** compass-tap covers it; fewer permanent
  controls on a map instrument.
- **Heading-up from course-over-ground:** needs motion and conflates
  course with facing; the compass works stationary.
- **Follow mode in this slice:** excluded by the authorized graph
  ("eventually"); camera-center policy is a later decision, not a side
  effect smuggled in with rotation.
- **True-north via engine frame tag:** would modify `atlas_location`
  for app convenience — forbidden; frames stay host-side.

## 5. Manual smoke matrix (human, Android Studio)

1. Fresh launch → ORIENT `off`, no compass (heading idle until asked).
2. Enable heading (toggle) → permission-independent start; compass dial
   appears on a sensor-bearing image; `unsupported` path visible on
   images without the virtual sensor (acceptable result, report which).
3. Rotate the device (or drive the emulator virtual sensors) → needle
   tracks; TRUE/MAG label correct per location availability.
4. Tap compass → rotation snaps 0, mode north-up.
5. Heading-up toggle → map rotates as device turns; center never moves;
   readout ORIENT `heading-up`.
6. Cover/disable sensor accuracy (if reproducible) → dial dims, no
   invented steadiness.
7. Location updates during heading-up → position moves, rotation follows
   heading only.
8. Deny location entirely → compass still works, labeled MAGNETIC.
