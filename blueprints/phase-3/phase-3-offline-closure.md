# Blueprint Phase 3 — Offline Atlas, Engine Completion (PASS / CLOSED)

- **Status:** PASS / CLOSED 2026-09-10, end-to-end in one controlled pass.
  Engine side only (3.1/3.2/3.3/3.4-engine). UX (3.5) stays app-track.
- **Contract note:** `blueprints/phase-3/phase-3-offline-engine.md` (single
  note — direct blueprint implementation, six recorded decisions).

## 1. What was built (production)

- `atlas_tiles/src/store/`: `AtlasMemoryStore` (explicit capacity, LRU with
  reported eviction, remove/clear/invalidate, stats; refuses invalid
  entries) + `AtlasStoreOperation` (serve/store real; miss ⇒
  `AtlasStoreMissException` ⇒ `operationThrown`, 3-set kept; acquire throws
  the inverse seam).
- `atlas_offline/src/packs/manifest.dart`: manifest + FNV-1a/64 checksums
  (non-crypto, documented) + aggregate seal + JSON round-trip.
- `atlas_offline/src/downloads/downloader.dart`: sequential loop over
  injected chunks, polled progress, resume/pause/discard, cancel via
  `ExecutionCancellation`, quota via limiter. Terminals complete/failed/
  cancelled/quotaPaused (+planned).
- `atlas_offline/src/policy/rate_limiter.dart`: pure token bucket, explicit
  integer-second time.
- `atlas_providers/src/pack_planner.dart`: pure enumeration + enforcement
  (BULK_GUARD / PACK_TOO_LARGE / PREFETCH_REFUSED / INVALID_RANGE as
  values, never throws; estimates need explicit bytes-per-tile).

## 2. Workspace correction (ADR-002 amendment, load-bearing)

Relative cross-package imports proved incompatible with `package_config`:
first `pub get` broke analysis repo-wide (`uri_does_not_exist`, found
empirically, stray `.dart_tool` removed). Fixed for real: path deps for
used edges + `publish_to: none` + full `package:` migration (57 imports) +
root workspace package (offline `pub get` verified, locks gitignored).
`test/` + `tools/` resolve through the root package.

## 3. Verification snapshot (at closure)

- Runner: total=388, pass=348, fail=0, blocked=8, notApplicable=32 —
  two-run byte-identical. All 311 prior passes preserved.
- 37 new fixtures (STO-001..012, PAK-001..017, ADV-093..100), zero dup IDs
  across 386 files. Real bug caught by fixtures: FNV signedness (negative
  hex seals) — fixed in production, not in test.
- `tools/atlas_tool.dart all` clean (analyze incl. strict set,
  format-check, regression). Leakage green; offline transport-free scan
  green; semantics-keep-no-verbs scan green.
- DEC-001..019 all OPEN (DEC-016: Melos + CI-green remain); no new DECs.
  Blueprint checkboxes true: 3.1 engine (lookup/validation/atomic-put/
  expiration-via-decisions/LRU/corrupt-refusal/stats), 3.2 format+flags
  (id/hash/resumable/cancellable/progress/deletion/export-model), 3.3
  (kind-agnostic packs carry any address family), 3.4 declarations enforced
  at plan time (rate primitive + refusals; centralized live enforcement is
  downstream runtime work).
