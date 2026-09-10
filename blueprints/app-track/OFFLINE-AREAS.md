# Offline Areas Application Track (Blueprint 3.5) — Built Actuals

- **Date:** 2026-09-10. App-only change (no engine file touched; engine
  suite unchanged at 453/413/0/8/32).
- **Scope:** blueprint 3.5 UX plus the 3.4-UX halves (restrictions/estimates
  surfaced before download) against the existing Phase 3 engine
  (`atlas_providers` planner, `atlas_offline` downloader/manifest/limiter,
  `atlas_tiles` memory store). File export/import is NOT built (manifest
  JSON is viewable + round-trip-tested; bytes on disk are future work).

## Architecture (new app code, `apps/atlas/lib/`)

- `offline/offline_pack.dart` — `OfflinePackRecord`: app-side bookkeeping
  (ranges, estimate basis, lifecycle, bytes, seal, refusal/block detail).
  Engine values held by reference, never re-derived. Refusals stay inside
  the engine reason set; app-side blocks (`ESTIMATE_REQUIRED`,
  `UNKNOWN_PROVIDER`, `NO_LOCATOR`, `APP_PACK_TOO_LARGE`) are separate
  plain text, never dressed as engine refusals.
- `offline/offline_repository.dart` — `ChangeNotifier` orchestrator. Owns
  the engine store (pack-slot accounting, capacity 64 — explicit app
  choice), record set, downloader lifecycles, the HTTP chunk source
  (engine `AtlasTileRequest.resolveUrl` + engine `httpTransport` — the app
  states WHICH tile, the engine states HOW the URL reads), manifest
  assembly + seal self-check on completion, one store index entry per
  completed pack (`resource` namespace, payload = seal), and a bounded
  (200) event log. Clock and chunk source injectable; production defaults.
- `offline/offline_page.dart` — Downloads / Saved Areas / Storage /
  Providers tabs + New-pack sheet (explicit ranges, REQUIRED bytes-per-tile
  with no default, bulk-approval checkbox, prefetch switch, live policy
  flags + bulk-guard text before planning).
- `diagnostics/diagnostics_page.dart` — operator health: app version,
  wired engine packages, registry self-validation counts, store/download
  state, newest-first event log. No analytics/telemetry exists or claimed.
- `main.dart` — `AtlasApp` holds one repository shared by map, Offline,
  and Diagnostics pages (constructor-injectable for tests); map AppBar
  gains Offline + Diagnostics actions.

## Blueprint checklist mapping (all shown values are live engine values)

- 3.5 "available offline" → Saved Areas tab (completed packs: tiles,
  received bytes, age, indexed/unindexed state, manifest viewer, delete).
- 3.5 "stale data age" → age shown RAW (`formatAge`); no stale threshold
  exists anywhere, so none is displayed (inventing one = fabrication).
- 3.5 "pack size" → tile count + pre-download estimate + actual bytes.
- 3.5 "remaining storage" → Storage tab (index slots used/remaining +
  session bytes held, with the session-resident caveat stated on screen).
- 3.4-UX "bulk guard enforced" → OSM plans refuse `BULK_GUARD` unless the
  operator checks approval (consent-gated, not absolute — proven).
- 3.4-UX "rate limits centralized" → ONE shared limiter throttles all
  packs (see finding below); no per-pack throttle exists.
- 3.4-UX "estimated size / restrictions before download" → plan preview
  button (`Download N tiles (est. X)`) + provider cards quoting declared
  policy verbatim (flags, maxTiles-or-undeclared, rate-or-undeclared,
  bulk text, license, attribution).
- 3.2-UX "resumable / cancellable / progress / clean deletion" → Pause
  (cooperative, same resume path), Cancel (bytes held), polled progress
  bar + `received/planned (bytes)` text, Delete (index + bytes + record).

## Findings (analysis grounded in read engine code + proven by tests)

1. **Centralized throttle is REQUIRED, not stylistic.** The engine
   downloader takes one timestamp per run and re-walks every tile on
   resume with the limiter take BEFORE the already-received skip check
   (`downloader.dart` loop order). A per-pack limiter therefore can never
   bank enough tokens to resume past a pause it caused (needs ≥ tiles
   takes, holds at most capacity < tiles). First implemented per-pack,
   proven unrecoverable in test, redesigned to blueprint-centralized
   ("Provider rate limits are centralized" — 3.4 says it outright).
2. **Session tile cap (4096).** No builtin declares maxTiles, so the
   planner would enumerate unbounded ranges; the app counts by saturating
   arithmetic BEFORE planning and refuses above cap (bytes are
   session-resident RAM — stated in UI, docs, and code; file persistence
   is the explicit future work, not a silent gap).
3. **Local bundles blocked honestly** (`NO_LOCATOR`): bundle-backed
   endpoints have no template, so no byte source exists; the app refuses
   at plan time instead of failing mid-download.
4. **One index entry per pack**, not per tile: tile-level entries would
   make a 512-entry store absurd; pack-level keeps Storage math truthful.

## Verification totals (see DEVICE-005 for device rows)

- `flutter analyze --no-pub`: clean. `flutter test test/`: 30/30 green
  (21 repository incl. pause/resume, cancel, quota pause→resume→complete,
  seal + manifest round-trip, store indexing, delete, clearStore, log
  bound; 7 page incl. estimate-required, BULK_GUARD verbatim, full fake-
  source download to Saved Areas + Storage; 2 pre-existing shell/picker).
- `flutter test integration_test`: 2/2 green on device (picker + offline,
  `OFFLINE_DEVICE_RESULT: complete` twice).
- Engine regression `dart tools/atlas_tool.dart all`: 453/413/0/8/32.
- Deps: direct + dev-direct all up-to-date; webdriver 3.1.0→3.2.0 taken
  (lock-only, tests re-green); hard ceiling unchanged (SDK-pinned
  material_color_utilities/test_api); engine workspace has zero hosted
  dependencies (path-only, nothing to audit).
