---
description: Drive the attached Moto G and report instrumented VERIFIED / NOT VERIFIED evidence
agent: build
---

Verify a Sovereign Atlas behaviour on the physical device (Moto G, `ZT4222BMWN`).

Prefer the `atlas_device` tool for every step: `devices`, `install`, `launch`, `stop`,
`clear_logs`, `logs`, `screenshot`, `profile_help`. It handles the wireless-TCP transport
and pulls screenshots to a temp path you can then read.

Device facts that change the outcome, all learned the hard way:

- Install `apps/atlas-android/app/build/outputs/apk/play/debug/app-play-debug.apk`.
  The `app-debug.apk` at the outputs root is a STALE pre-flavor artifact; ignore it.
- **CoT marker drops need Network Profile Off-Grid Mesh Only or Hybrid.** Radio Silence
  (EMCON) throws "Markers require an active mesh profile" and Cloud-Only Chat also refuses.
  A marker that fails to appear may be a profile guard, not a rendering bug. Use
  `profile_help` for the tap path.
- Long-press opens the targeting sheet: `adb -s <device> shell input swipe X Y X Y 1200`.
- Never drop a marker at screen centre; the green position puck is drawn on top and hides it.
- If a drawing mode is active, it consumes the long press meant for the marker sheet.
- LoS without a `.mbtiles` DEM pack correctly reports that no DEM elevation is available.
  That message is the expected result, not a defect.
- ADB wedges are fixed by `adb kill-server` then `adb start-server`. If the device leaves the
  network entirely, mDNS advertises nothing and you must wait for it to return.
- The app draws edge to edge; the top band is black and carries the system status bar.

RULES.md 4.3: a screenshot alone never proves a behaviour. For each claim, name the
instrumented evidence: a logcat line matched by tag, a `dumpsys` field, or a value the app
printed. Use `clear_logs` before the action so the evidence is attributable.

Report a short table: behaviour, VERIFIED / NOT VERIFIED / CONTRADICTED, and the evidence
that backs it. If you only have pixels, say the behaviour is unverified.
