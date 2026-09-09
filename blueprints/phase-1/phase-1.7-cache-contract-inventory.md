# Phase 1.7-A — Cache Contract Inventory (Evidence)

- **Status:** Inventory only. No implementation begins from this document.
- **Method:** every row traces to forensics (Phase 0.1), contracts (0.2/1.4–1.6),
  or is marked PROPOSED/DEFERRED/BLOCKED with ownership.

## 1. TacMap cache evidence (SRC-A, Phase 0.1 F-09)

| Observed | Standing |
|---|---|
| `TilePack.prefetch(bounds, zoom, layers, progress)` call site; zoom coerce 3–14; TOPO-always + SAT-if-on; exclusions | SOURCE-VERIFIED call shape |
| `TilePack` internals (storage, format, eviction) | INFERRED at best (not in file) — no semantics taken |
| `runCatching → 0` failure posture | SOURCE-VERIFIED (fail-quiet at call site) |

## 2. Recovery cache evidence (SRC-B/C, Phase 0.1 F-10)

| Observed | Standing |
|---|---|
| Lookup → decode-validate → delete+refetch on corrupt → fetch on miss → 1×1 transparent on failure; never throws | SOURCE-VERIFIED flow; ATLAS-NORMATIVE as lookup semantics |
| `User-Agent`, 12s timeout, 200+non-empty gate | SOURCE-VERIFIED transport facts — NOT taken (transport deferred) |
| Non-atomic write; no TTL/LRU/eviction/size-cap; `SynchronousFuture` key (stale-forever risk) | SOURCE-VERIFIED defects → ATLAS-NORMATIVE negative requirements |
| Sequential prefetch + 12ms gap + skip-exists + cancel flag + `maxTiles=800` | SOURCE-VERIFIED mechanics; concurrency/ToS/multi-layer REBUILD (not 1.7 scope) |
| `CachedTileProvider` dead code (live layers network-only) | SOURCE-VERIFIED defect — must not recur (wiring belongs to adapters) |

## 3. Phase 0.2/1.4–1.6 contracts consumed

- ATLAS-TILE-ID-001 (identity/URL/key/entry/payload five-way split), TILE-004 key
  shape, TILE-005 no-silent-fallback, CACHE-001..007 (flow + atomic/bounded
  requirements), ATLAS-TILE-CACHE/PREFETCH-001, ATLAS-OFF-001 hierarchy,
  ATLAS-PROV-DESC-001 (descriptors), 1.5 resolution (no acquisition),
  1.6 resource identity/materialization boundary (no bytes obtained).
- Unresolved/adjacent: DEC-006 (error channel), DEC-009 (eviction/TTL — the
  algorithm question stays OUT of 1.7; only the semantic hooks are in scope),
  DEC-011 (pack integrity), freshness thresholds (no DEC assigned — owned by
  freshness contract text as PROPOSED).

## 4. Minimum resolution contract (new terms)

Cache identity (what is asked), cache entry (what is known), lookup outcome
(HIT/MISS/STALE/EXPIRED/INVALID), freshness/expiration (time-explicit, never
`now()`), retention/invalidation (semantic ops, not deletion mechanics),
policy (declared thresholds/flags), decision (pure function of the above).

## 5. Explicitly deferred (not in 1.7)

Eviction algorithms/LRU (DEC-009), TTL *values* per provider (policy carries
the field; values are deployment data), pack/manifest mechanics (DEC-011),
storage engines (memory/disk/DB), transport, decoding, ranking, coverage
polygons, subdomain rotation, QUADKEY.
