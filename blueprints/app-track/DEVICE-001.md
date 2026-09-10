# DEVICE-001 — First Device Verification (PASS)

- **Date:** 2026-09-10. **Target:** `atlas_avd` (Pixel 9, android-35,
  google_apis/x86_64, fresh system image via sdkmanager).
- **ADB recovery note:** the daemon was wedged (`adb devices` hung and killed
  its caller); `adb kill-server` + `start-server` recovered it. No device was
  ever attached — the emulator below is new, not pre-existing.

## Evidence (all observed, none inferred)

1. AVD created from the downloaded image; cold boot reached
   `sys.boot_completed=1`.
2. `adb install -r app-debug.apk` → `Success`.
3. `am start com.sovereignatlas.atlas/.MainActivity` → process alive
   (PID 2921), activity `topResumedActivity` = MainActivity.
4. App-PID logcat: zero exceptions/errors/failures. Single benign warning
   (`HWUI: Failed to initialize 101010-2 format` — emulator swiftshader
   fallback, not an app fault).
5. Pre-build widget smoke test green; tile URL correctness fixture-proven
   (BM-008: exact OSM template render).

## Explicitly NOT claimed

- Pixel-level tile rendering (image viewer limits blocked screenshot
  inspection; no tile/network errors logged, but pixels unverified).
- Any physical-device behavior (emulator only). First-launch UX beyond
  boot (interaction flows) stays app-track work.

## Standing artifacts

- Emulator left RUNNING (`atlas_avd`) for follow-up sessions.
- Shell source: `apps/atlas` (flutter_map 8.3.2, engine-registry template).
