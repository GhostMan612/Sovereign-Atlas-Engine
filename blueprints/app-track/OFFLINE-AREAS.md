# Offline Areas Application Track (Blueprint 3.5) — Built Actuals

- **Date:** 2026-09-10. App-only change (no engine file touched; engine
  suite unchanged at 453/413/0/8/32).
- **Scope:** blueprint 3.5 UX plus the 3.4-UX halves (restrictions/estimates
  surfaced before download) against the existing Phase 3 engine
  (`atlas_providers` planner, `atlas_offline` downloader/manifest/limiter,
  `atlas_tiles` memory store), extended to **offline rendering**
  (resolution → flutter_map seam, device-proven — see §"Offline rendering"
  below). User-visible file export/import is NOT built (manifest JSON is
  viewable + round-trip-tested; the disk journal is app-private
  persistence, not an export surface).

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
   arithmetic BEFORE planning and refuses above cap (the RAM serve map is
   bounded by the cap; completed packs persist to the journal — the cap
   guards enumeration and serve memory, not disk).
3. **Local bundles blocked honestly** (`NO_LOCATOR`): bundle-backed
   endpoints have no template, so no byte source exists; the app refuses
   at plan time instead of failing mid-download.
4. **One index entry per pack**, not per tile: tile-level entries would
   make a 512-entry store absurd; pack-level keeps Storage math truthful.

## Offline rendering (resolution → renderer seam)

"The tile exists in storage" ≠ "the renderer rendered it offline" — the
second claim is now proven (DEVICE-006). Smallest adapter, no engine
bypass:

- `offline/offline_tile_provider.dart` — `AtlasOfflineTileProvider extends
  TileProvider` (the exact flutter_map seam: `getImageWithCancelLoading-
  Support`). Order per tile: (1) repository bytes → `MemoryImage`;
  (2) miss → standard `NetworkTileProvider` (URL mechanics unchanged,
  same as before — the DOWNLOAD path still resolves engine-side);
  (3) fallback construction/invocation throws → transparent tile
  (local-only degradation; missing degrades, served renders, nothing
  faked). Fallback is LAZY (under a blocked transport even client
  construction throws — must degrade per-tile, never at build) and
  injectable for tests. Store-first means packed tiles serve local even
  while online (offline-first, by construction).
- Knowledge gates bytes: `resolveTileBytes` serves ONLY when the pack's
  engine index entry is live (`store.get` per resolve — cheap map op;
  recency refresh on serve is semantically fine). Unindexed bytes are
  pending deletion, never "available offline" (proven by clearing the
  store out from under held bytes → miss).
- Provider isolation: serve map keyed `$providerId/$key`; Esri bytes
  never serve an OSM layer (proven — including accidentally on device,
  where the first render attempt correctly served nothing).
- Disk journal (app-side; engine never touches disk):
  `offline_packs/<packId>/<z>_<x>_<y>.tile` + `index.json` under the app
  documents dir (`path_provider ^2.1.6` — sole new dependency, justified
  in pubspec: dart:io cannot resolve that location). `restore()` rebuilds
  RAM + engine index in a fresh repository; corrupt entries (missing
  files, oversized key lists, unparseable index) are SKIPPED with a log
  line, never half-loaded. `deletePack` removes record + RAM + disk +
  index entry; `clearStore` evicts everything (index, RAM, journal) and
  keeps records as byteless history (seal/manifest nulled — half-held
  state would make "available offline" a lie).
- Renderer-observable counters on the repository (`offlineTileHits`,
  `networkTileRequests`; deliberately un-notified per tile, polled by
  Diagnostics) — these ARE the device proof instruments.

## Findings, continued

5. **Prism enumeration is engine-visible from the app.** The planner
   enumerates the full inclusive prism with no grid validation (a z0-2 /
   x0-2 / y0-2 request plans 27 tiles, including grid-invalid ones) — the
   New-pack form therefore requires all six bounds (caught live when a
   maxes-only test entry over-planned). No app-side grid validation added
   (out of scope; one-line observation, no behavior change).
6. **SDK rename caught by the compiler, not by docs:** `HttpOverrides.
   createClient` is now `createHttpClient` (verified in the bundled SDK
   sources). The offline transport block uses the current name.

## Hardening pass (APP-AUDIT-001, all six FIX NOWs, no engine changes)

7. **Deletion authoritative over in-flight futures.** `deletePack`
   cancels/pauses first AND two post-await ownership checkpoints (after
   the chunk loop, after persist) discard late-settling results — no
   resurrection through either window (both windows device-tested at
   host level with gated fakes).
8. **LRU eviction consumed, not ignored.** The engine-reported evicted
   entry unflags exactly the victim record (+ event log); the live gate
   agrees (victim unserved, survivors served).
9. **Persistence required for completion.** Journal failure → `failed`
   with `PERSIST_FAILED` (index entry rolled back, partial dir dropped,
   bytes kept for resume-retry — which then succeeds once healed).
10. **Journal untrusted.** Whole-`restore()` catch, non-object entries
    skipped, resident records win over journal collisions, pack-id mint
    loops past restored ids (no overwrite, format preserved).
11. **Wording/duplication/labels.** Cap message states the true
    rationale; tile keys use engine `AtlasPackDownloader.keyOf`;
    Diagnostics reports `unfinished` + `downloading` instead of a
    misleading `active`.

## Verification totals (see DEVICE-005/006 for device rows)

- `flutter analyze --no-pub`: clean. `flutter test test/`: 48/48 green
  (41 baseline + 7 hardening: delete-during-download,
  delete-during-persist, LRU eviction, persist-failure+recovery, mixed
  journal, dead-directory restore, id-collision loop).
- `flutter test integration_test`: 5/5 green on device (picker + offline
  acquisition + render A/B + blocked-transport failure proof
  [`OFFLINE_FAILURE_RESULT: failed` — SocketException detail surfaced,
  nothing indexed, app usable]).
- Engine regression `dart tools/atlas_tool.dart all`: 453/413/0/8/32
  (untouched — no engine file modified in this pass).
- Deps: direct + dev-direct all up-to-date (incl. new path_provider
  2.1.6); hard ceiling unchanged (SDK-pinned
  material_color_utilities/test_api); webdriver stays 3.2.0; engine
  workspace zero hosted dependencies.
