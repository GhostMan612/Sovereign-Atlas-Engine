# Blueprint Phase 3 — Offline Engine Contract Note (Normative)

- **Status:** Engine-side Phase 3 (3.1/3.2/3.3/3.4-engine). UX (3.5) stays
  app-track. One note (not five docs): these are direct blueprint
  implementations with small, recorded decisions.
- **Placement:** retention machinery → `atlas_tiles/src/store/` (owns cache
  semantics; the 1.7 no-retention rule was semantic-level and never bound the
  engine); packs/downloads/policy → `atlas_offline/src/{packs,downloads,
  policy}/` (ADR-001 charter). No new package.

## Decisions (all PROPOSED → proven by fixtures below)

1. **Memory store** (`AtlasMemoryStore.ok`): insertion-ordered map keyed by
   `namespace/value`, explicit capacity (required, >0), LRU on get/put
   (remove+reinsert — deterministic), oldest-first eviction with the evicted
   entry REPORTED, explicit `remove`/`clear`/`invalidate`, stats value.
   Refuses structurally invalid entries (stores knowledge, not garbage).
2. **Store-backed operation**: serve runs the NAMED entry against the store
   (no re-lookup of freshness — 2.0-I); store MISS ⇒ `AtlasStoreMissException`
   ⇒ executor `operationThrown` (3-set kept: absence-at-serve is mechanical,
   documented, fixture-proven); handoff `put`s (acknowledged = resident);
   `runAcquisition` throws the Phase-3-inverse seam (stores don't acquire).
3. **Pack manifest**: `AtlasPackManifest` (id, provider, zoom range, epoch
   createdAt, source version, attribution, entries with per-entry FNV-1a/64
   checksums + aggregate seal). FNV is NON-cryptographic (documented;
   cryptographic seal is a later upgrade, not silent strength). JSON
   round-trip (export path without IO).
4. **Planner enforcement** (declarations become refusals HERE, not Phase 4):
   bulk-guarded provider + unapproved bulk ⇒ `BULK_GUARD`; count > maxTiles
   ⇒ `PACK_TOO_LARGE`; prefetch-disallowed + prefetch ⇒ `PREFETCH_REFUSED`;
   tile enumeration is pure address math (inclusive ranges, explicit zooms).
   Estimates require explicit bytes-per-tile (no invented averages).
5. **Downloader**: sequential chunk loop over an INJECTED chunk source (no
   concurrency — no scheduler), polled `progress` value (no callbacks, 2.0-D
   precedent), resume from received-set, cancel via `ExecutionCancellation`
   (continuity with Phase 2), quota via pure token-bucket limiter
   (`quotaPaused` terminal, same resume path). Terminals: complete (seal
   verified) / failed (checksum or chunk throw) / cancelled / quotaPaused.
6. **Rate limiter**: pure token bucket, integer-second refill, explicit time;
   standalone primitive + downloader hook. No timers, no background refill.
