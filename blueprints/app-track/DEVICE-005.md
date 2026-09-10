# DEVICE-005 — Offline Areas On-Device Verification (PASS)

- **Date:** 2026-09-10. Same `atlas_avd` session (fresh boot; earlier
  session's emulator process had died, new boot `boot_completed=1`).
- **Method:** `flutter test integration_test/offline_test.dart` (semantic
  locators only) + final-APK reinstall, launch check, inspected screencap.

## Proven on device

1. **Refusal without network:** default OSM plan, no bulk approval →
   `BULK_GUARD` visible on device (assertion passed in-harness).
2. **Real download end to end:** 1-tile Esri World Imagery pack →
   `OFFLINE_DEVICE_RESULT: complete` (real HTTP through engine URL
   resolution + engine transport → bytes → manifest → seal → store
   index). Ran TWICE (single-file + full-directory runs), complete both
   times. The `failed`-with-detail branch was NOT exercised (no failure
   occurred; the branch is covered by host tests with throwing sources).
3. **Saved Areas on device:** after completion, the tab shows
   `Esri World Imagery` (assertion passed in-harness).
4. **Final build healthy:** rebuilt `app-debug.apk` installed `Success`,
   launched (PID 4708), logcat shows no FATAL for the package.
5. **Inspected screencap:** map renders REAL OSM tiles (independent proof
   the device has internet), all three AppBar actions present
   (Basemap / Offline / Diagnostics), marker + readout + OSM attribution
   correct. (Temp frame pulled off-device and deleted afterwards.)

## NOT claimed

- Multi-tile / large-pack downloads on device (1 tile proven; larger is
  host-test territory until persistence exists).
- The `failed`-terminal UI on a real device (only host-proven).
- Offline RENDERING from packs (no custom TileProvider built — packs are
  indexed + inspectable, not yet served to the map; explicit future work).
- Battery/radio behavior, physical-device behavior.
