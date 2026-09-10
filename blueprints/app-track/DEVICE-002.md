# DEVICE-002 — Interaction Verification (PASS)

- **Date:** 2026-09-10. **Target:** same `atlas_avd` session as DEVICE-001
  (app process PID 2921 throughout — no restart between sessions).
- **Question:** does touch input drive re-render (full input→render loop)?

## Method (byte-compare with control — viewer-independent)

Screenshots pulled via on-device file (PowerShell pipe redirection corrupts
binary — earlier 571 KB "screenshots" were corrupt; lesson recorded). A
stdlib-only PNG decoder (zlib + unfilter, since discarded) compared frames:

1. **Control:** two frames 3 s apart, no input → byte-IDENTICAL
   (hash `874fcb97…`, diff 0.0000). Method sound; no ambient animation.
2. **Experiment:** `input swipe 800 1200 → 300 1200` (map pan), 6 s settle,
   third frame → DIFFERENT (hash `bc3a158f…`, byte-diff 0.2555).

The only change between control and experiment was the swipe: a quarter of
the frame re-rendered. Map panning works on-device.

## Health after interaction

- Same PID alive; no `FATAL EXCEPTION` on the app PID in logcat.
- Temp artifacts (frames, script, on-device copies) removed.

## Explicitly NOT claimed

- Which tiles rendered at pixel level (variation proves re-render, not tile
  provenance — tile URL correctness stays fixture-proven, BM-008).
- Multi-touch, rotation, fling physics, or frame-rate characteristics.
