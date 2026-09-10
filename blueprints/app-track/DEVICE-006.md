# DEVICE-006 — Offline Rendering On-Device Proof (PASS)

- **Date:** 2026-09-10. Same `atlas_avd` (multiple fresh boots; a stale
  `multiinstance.lock` from a killed instance blocked one boot — removed,
  documented as environment flake, not app behavior).
- **Method:** `integration_test/offline_render_test.dart` (semantic
  locators only), production chunk source throughout phase A.

## Proven on device

1. **A — acquire:** real 4-tile Esri pack (z2 x1–2 y1–2, viewport center
   at the map's initial 0,0/z2) → `OFFLINE_RENDER_PACK: 1 complete,
   4 tiles` (seal + store index + journal, all production paths).
2. **B — relaunch from disk:** FRESH repository + `restore()`, zero
   downloads this launch → 1 pack re-indexed, all 4 keys resolving
   (proves the journal survives process-state restart; the harness only
   uninstalls at file end, so same-file phase B is a genuine relaunch
   simulation).
3. **B — render with transport blocked:** `HttpOverrides.global` denying
   ALL `HttpClient` construction (the client `package:http` builds on),
   image cache evicted, layer cycled for full re-resolution →
   `OFFLINE_RENDER_RESULT: renderHits=8 network=56`. Eight local serves
   (packed center tiles, online switch + blocked re-resolution) and 56
   miss attempts, every one dead-ending at the blocked stack and
   degrading to transparent — no crash, no faked tile, no network byte.
4. **Reproduction:** full-file rerun printed IDENTICAL counters
   (4 / 8 / 56) — the render path is deterministic, not luck.
5. **Restored records drive UI:** Saved Areas shows the pack with no
   download this launch (assertion passed in-harness).
6. **Negative control (accidental, kept):** the first run's layer/pack
   provider mismatch (OSM layer, Esri pack) served NOTHING locally —
   provider isolation holding on-device, by the same counters.

## On-device failure terminal (hardening pass, PASS)

- **Method:** `integration_test/offline_failure_test.dart` — real 1-tile
  Esri download attempt with `BlockedTransport` active from launch.
- **Result:** `OFFLINE_FAILURE_RESULT: failed` (00:20, green). The card
  surfaces the engine failure detail (`SocketException ... transport
  blocked`), Saved Areas reads "Nothing available offline", Storage
  reads `Pack index: 0/64`, Diagnostics opens over the failed state.
- **What this closes:** the last unproven terminal on hardware. Success
  under a dead transport would have been a fabrication bug; the rig
  asserts `failed` exactly (it fails the test on `complete`).
- **Scope preserved:** transport-blocked, not radio-off (same boundary
  as the render proof above).

## On-device timeout mapping (DEC-020 proof, PASS)

- **Method:** `integration_test/offline_timeout_test.dart` — injected
  never-answering source + 2 s bound through the REAL app flow
  (navigate → plan → download). Deterministic trigger, not a natural
  stall: real socket timing is nondeterministic and unclaimed.
- **Result:** `OFFLINE_TIMEOUT_RESULT: failed` (00:22, green).
  `TimeoutException` identity visible on the card, Saved Areas empty,
  `Pack index: 0/64`, app fully usable afterwards.
- **What this closes:** the mapping half of the timeout decision on
  hardware. The mechanism half (deadline fires, received preserved,
  resume, cancel-wins, wedge closure) is host-proven; the device proves
  the real app reaches the real terminal through the real UI.

## Scope honesty

- The block is TRANSPORT-level, not radio-off: for the map renderer the
  two are indistinguishable (zero network bytes can flow either way).
  Radio state is outside the renderer's observable universe — claiming
  "airplane-mode tested" would overstate; "transport-blocked tested" is
  exact.
- No pixels captured: the harness uninstalls at file end, and
  reconstructing the state by hand would be blind-tap theater. The
  counters (hits + attempts, printed verbatim) measure the seam directly
  and are stronger evidence than a frame.
- Multi-tile here means 4 center tiles (viewport-fitted by design);
  larger packs are host-test territory until file persistence pressure
  demands otherwise. Physical-device behavior still NOT claimed.

## Environment flakes (not app behavior, recorded for the next session)

- The emulator process dies between turns (~15–25 min lifetime) AND a
  stale `multiinstance.lock` blocked one reboot (fix: delete the lock).
  One combined `flutter test integration_test` run lost the device
  mid-run (2 failures = "no devices connected"); per-file reruns on a
  fresh boot went 4/4 green. Budget boots defensively.
