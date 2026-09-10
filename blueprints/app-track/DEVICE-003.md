# DEVICE-003 — Provider Picker Verification Status (MIXED)

- **Date:** 2026-09-10. Same `atlas_avd` session (app reinstalled with picker
  build; PID 5429 at close, healthy, no fatals).

## Proven

- **Widget level (deterministic):** picker opens by tooltip, Satellite
  selects, TileLayer URL flips to arcgis, Esri attribution appears
  (`flutter test`: 2/2 green).
- **Device level (boot/render):** reinstalled APK launches, process alive,
  log clean (same rigor as DEVICE-001/002).

## NOT proven on device

- **Sheet opening via coordinate tap:** 4 candidate points around the AppBar
  action ((1010,130), (1024,120), (990,140), (1050,110)) all produced
  ~0.0001 frame diffs (clock-tick scale, not a sheet). Taps missed the icon
  or hit AppBar dead space — coordinate guessing without visible inspection
  is declared INEFFECTIVE, not retried further.
- Template-switch pixels on device (same mechanism as boot render, but the
  post-tap state was never reached to compare).

## Next attempt prescription (not more guessing)

Drive the tap from INSIDE the test harness instead of blind coordinates:
`flutter drive` / integration_test with `flutter test integration_test`
tapping `find.byTooltip('Basemap')` on the attached emulator — deterministic
locators, same proof strength as DEVICE-002's byte-compare. That is the
correct tool; coordinate taps are retired.

## Dependency ceiling (recorded in pubspec + here)

`flutter pub upgrade --major-versions` applied: latlong2 0.9.1→0.10.1, meta,
vector_math, hooks, code_assets, objective_c, record_use up; flutter_map
stays 8.3.2 (max with our constraints). Hard ceiling: newer
material_color_utilities/test_api are SDK-pinned. `flutter analyze` clean,
`flutter test` green on the upgraded set.
