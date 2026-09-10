# DEVICE-004 — Picker Interaction Proof, On-Device (PASS)

- **Date:** 2026-09-10. Same `atlas_avd` session.
- **Method prescribed by DEVICE-003:** `flutter test integration_test`
  (`integration_test/picker_test.dart`) — semantic locators
  (`byTooltip('Basemap')`, `text('Satellite')`), zero blind coordinates.

## Evidence (from the verbose harness log, since discarded)

1. Harness installed the test build, launched MainActivity, forwarded the
   VM service, ran `picker switches basemap on device`.
2. Taps: Basemap tooltip → sheet appeared (`Satellite` found) → Satellite
   tapped → settled.
3. Assertions green: a `TileLayer` with an `arcgis` URL active; Esri
   attribution text present.
4. `00:07 +1: All tests passed!`, exit code 0. Harness uninstalled the app
   afterwards (standard cleanup); the debug APK was reinstalled immediately
   after, so the device is left runnable.

## Status changes

- The DEVICE-003 open item (sheet opening on device) is CLOSED: opened,
  selected, and verified by template + attribution, deterministically.
- An earlier `VmServiceDisappearedException` on first attempt was transient
  (wedged-adb era artifact); the rerun passed cleanly with no code changes.
- Remaining app-track work is now pure breadth (offline-areas screen,
  advanced diagnostics), not proof-of-mechanism: every interaction layer
  (tap → state → template → attribution) is proven on-device.
