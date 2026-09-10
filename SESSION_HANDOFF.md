# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence docs stay in
> `blueprints/app-track/`; this file points at them, never duplicates them.

## Where we are (2026-09-10)

- **Baseline:** `d0881ff` local, clean, one ahead of `origin/main`
  (`96f81b7` pushed). Awaiting push authorization for `d0881ff`.
- **Track status:** Offline Areas + offline rendering + hardening +
  chunk-source timeout (DEC-020) all landed and verified. The map is a
  proven foundation; it is not yet an instrument.
- **Rule set:** `RULES.md` canonical (Sovereign Directives adopted, §1.1
  grandfather clause for existing files), `AGENTS.md` trimmed to ramp,
  slash commands in `.opencode/commands/`.
- **Next milestone:** Phase B forensic/integration planning (NOT
  implementation): inventory `atlas_location` / `atlas_tactical` /
  `atlas_analysis` / `atlas_terrain` + app-host seams for the field
  navigation track, then a bounded plan. CARTO joins via
  evidence → contract → credential-boundary → fixture → implementation
  (keys never enter repo/history/logs).

## Standing environment facts

- **No builds here unless explicitly asked** (RULES.md §1.6). Gates are
  `pub get` / `analyze` / host `test`. `integration_test/` runs only on
  explicit ask (it compiles). Human builds + smoke-tests in Android
  Studio; debug from pasted output.
- Toolchain: Flutter 3.47 / Dart 3.13 / `C:\android` / bundled JDK.
- Emulator here is short-lived (~15–25 min); stale `multiinstance.lock`
  blocks reboot (delete it). `adb kill-server` fixes most wedges.
- Never pipe `adb pull`/binaries through PowerShell pipes; never edit
  sources via PS text pipelines (RULES.md §3).
- Physical device seen once: moto g 2025, launch + render clean, no
  errors (unstructured observation — physical-device proof NOT claimed).

## Verification posture

- Host: 52/52 app tests + engine 453/413/0/8/32 + analyze clean.
- Device (emulator, prior sessions): picker, acquisition, render
  (4 tiles / 8 serves / 56 attempts), blocked-transport failure,
  forced-timeout mapping — all green when run.
- Full MGRS blocked (MGRS-001). DEC-001..019 + MGRS-001 live outside
  this tree; in-repo decisions: ADR-001..005, DEC-020.
- Open threads: timeout engine-side scope (out — DEC-020 is app-only by
  decision); flutter_map's implicit 168h HTTP cache coexisting with
  engine-indexed packs (flagged for productization, no action).

## Next actions

1. Push authorization for `d0881ff` (operator decision).
2. Phase B forensic/integration planning pass (planning only).
3. CARTO provider contract (keys stay out of repo).
