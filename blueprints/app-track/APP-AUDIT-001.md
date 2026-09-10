# APP-AUDIT-001 — Application Integration Hardening Audit (bounded)

- **Date:** 2026-09-10. **Baseline:** `ebf1f79` (authoritative application
  baseline; pre-render assumptions must not leak back in).
- **Method:** code inspection of `apps/atlas/lib` against the 11 directed
  areas, cross-checked with the 41 host + 4 device tests. No code changed,
  no engine reopened, no requirements invented. Evidence cites file +
  member (line numbers only where grep-verified this session).
- **Out:** 6× FIX NOW, 8× ALREADY CLOSED, 3× DEFER, 1× DECISION REQUIRED.

## FIX NOW (small, bounded, no architecture change)

1. **Delete-during-download resurrects the pack (zombie index + orphaned
   bytes).** `deletePack` removes the `_downloads` entry but the in-flight
   `download()` future keeps the orphaned record and reaches
   `_completePack` → `_store.put` re-indexes a deleted pack and
   `_persistPack` writes an orphaned dir. Evidence:
   `offline_repository.dart` — `deletePack` (~line 410) never touches
   `_cancellations`; `startDownload`'s continuation has no
   still-registered guard. Fix: request cancel + pause in `deletePack`,
   plus a post-await `_packs.containsKey` guard; test: delete mid-gate
   → no store entry, no dir, terminal on the orphan only.
2. **LRU eviction drifts the index flag.** The 65th pack silently evicts
   the oldest via `AtlasMemoryStore.put` — whose evicted-entry return is
   ignored at both call sites (`_completePack`, `_restoreOne`) — while the
   evicted record keeps `cacheEntryPresent == true` and Saved Areas shows
   "indexed" for bytes the live gate will never serve. Fix: consume the
   engine-REPORTED eviction (lookup by entry key, flag false, log line);
   test: fill 64 (small-capacity store), complete 65th, assert flag+gate.
3. **Journal-write failure crashes completion.** `_persistPack` (disk
   full, permissions) throws out of `_completePack`, which is awaited
   with no catch: lifecycle sticks at `downloading`, exception escapes
   into an unawaited UI future (zone error). Fix: catch → `failed`
   terminal with `PERSIST_FAILED` detail + best-effort partial-dir
   cleanup; test with throwing directory provider.
4. **`restore()` can throw into the void.** `initState` fires it
   unawaited; any journal IO throw becomes a zone error at startup. Fix:
   catch-and-log inside `restore` (the event log is reachable there).
5. **Pack-id collision across restore.** Ids are `pack-<epoch>-<seq>`
   with per-launch `_packSequence`; a restored id can collide with a new
   plan in the same epoch second (certain under test clocks, possible on
   device across rapid relaunch). Fix: collision loop / reseed sequence
   from restored suffixes; test with fixed clock + restore + plan.
6. **Stale session-resident wording + key-format duplication (bundle).**
   (a) The `APP_PACK_TOO_LARGE` detail still says "file persistence is
   future work" — false since the journal landed; reword to the true
   rationale (RAM serve map bounded by the cap). (b) The provider builds
   `'z/x/y'` by hand instead of calling engine
   `AtlasPackDownloader.keyOf` — format coupling; call the static.
   (c) Diagnostics `active` counts inert planned/refused records —
   rename/rescope to unfinished-vs-running honestly. All trivial; one
   pass with tests.

## ALREADY CLOSED (verified, do not rework)

7. Double-start guarded (`_downloads` check); pause-then-delete,
   cancel-then-resume, quota-then-delete all terminate sanely by
   construction + host tests.
8. Refused/blocked records cannot start downloads (null-plan + appBlock
   guards; buttons hidden accordingly).
9. Corrupt-journal entries skipped with log lines, never half-loaded
   (host-proven: missing files, oversized key lists, unparseable index).
10. `MapController` undisposed is a non-issue while the map is the app
    root (app-lifetime object); revisit ONLY if the map stops being root.
11. Engine boundary clean: no URL building, no enumeration, no checksum,
    no policy logic in app code (chunk source uses engine `resolveUrl` +
    engine `httpTransport`; `flutter analyze` + engine SELF-arch-leakage
    green). `dart:io`/`path_provider` confined to the repository.
12. Diagnostics static claims verified: app version `0.1.0+1` matches
    pubspec; wired-package list matches path deps exactly; provider
    counts computed live (only the test hardcodes 7).
13. Prism-enumeration behavior is engine-owned and app-tolerated (form
    requires all six bounds; observation recorded, correctly NOT
    "fixed" app-side).
14. Test-side `HttpOverrides.createHttpClient` rename handled at the
    current SDK name (source-verified); production code unaffected.

## DEFER (real, but productization-scope or framework-owned)

15. **Background downloads.** No foreground service; a killed app loses
    RAM-held partial progress (consistent with documented
    session-resident semantics — resume restarts the pack). Real fix =
    foreground-service + resumable journal writes: Phase 13 territory.
16. **Corrupt tile bytes at render.** Garbage bytes → framework codec
    error → blank tile, no crash (Flutter-owned degradation). Revisit
    only with a decode-validation requirement from product UX.
17. **Retry-after-recovery affordance.** After transport returns, cached
    transparent tiles persist until eviction/rebuild; no user-facing
    retry exists. Small but UX-design-shaped — bundle with the failure-UX
    pass, not with contract hardening.

## DECISION REQUIRED

18. **On-device failure-branch proof vs. host proof.** The
    `BlockedTransport` rig CAN drive a real download to the `failed`
    terminal on device (currently host-only via throwing sources). Cost:
    one more integration phase (~2 min device time). Value: closes the
    last unproven terminal on hardware. Recommend DO IT in the hardening
    milestone — needs architect sign-off only because it spends scarce
    emulator lifetime, not because it is architecturally risky.
