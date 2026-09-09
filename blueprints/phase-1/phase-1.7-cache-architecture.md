# Phase 1.7 — Cache Architecture (Normative, Storage-Independent)

- **Status:** Normative for `atlas_tiles` semantics. Storage engines (memory/
  disk/DB) consume these meanings; they never redefine them (Law 8).
- **Placement (1.7-B ruling):** `atlas_tiles` owns cache semantics — ADR-001
  chartered it for cache contracts, so no new package and no ADR were needed.
  `atlas_offline` (packs/durability) and `atlas_provider_api` (meaning only)
  are untouched. Depends: core + provider_api; zero new dependencies.

## The 17 questions (1.7-O)

1. **What is cached?** Knowledge about resources (entries), never bytes in this layer.
2. **Cache key?** `AtlasCacheKey{namespace, value}` — tile scope (TileKey shape)
   or resource scope (URL-free string). Canonical = validated value string.
3. **Cache entry?** `AtlasCacheEntry{key, resource?, storedAt, maxAgeSeconds?,
   payloadId?, revoked}` — knowledge + explicit time anchor, no storage fields.
4. **Hit?** Present, valid, unrevoked, age ≤ maxAge (inclusive), explicit now.
5. **Miss?** Absent entry. Nothing implied about upstream.
6. **Stale?** Present but revalidation-advised: unknown policy, or (reserved)
   future stale windows. Never auto-delete, never auto-miss.
7. **Expired?** Present, valid, age > maxAge. Still present — not deletion.
8. **Invalid?** Structurally invalid, revoked, or time-anomalous entry.
9. **Invalidation?** Pure `invalidate()` copy setting revoked (original untouched).
10. **Retention?** Decisions (hit/stale/expired/invalid) + replacement-by-
    construction; no remove/expire machinery (storage verbs, not semantics).
11. **Intentionally undefined?** Eviction/LRU (DEC-009), TTL values per provider,
    pack mechanics (DEC-011), ranking, coverage polygons, subdomain rotation.
12. **SOURCE-VERIFIED?** Lookup→validate→recover flow; corrupt self-recovery;
    exclusions/maxTiles/cancel mechanics (as requirements, not code).
13. **ATLAS-NORMATIVE?** Five-way split, key/entry/payload separation, no silent
    hits, expiration≠deletion, stale≠absent, invalid≠miss, explicit time.
14. **PROPOSED (provisional)?** Unknown→stale, future→invalid, inclusive
    boundary, invalidation marker, replacement-by-construction.
15. **Blocked?** Nothing new (eviction/TTL stay DEC-009-open as before).
16. **Where?** `packages/atlas_tiles/lib/src/{keys,entries,lookup}/`.
17. **Deferred?** All engines: memory/disk/DB/SQLite/filesystem/HTTP/decode/
    renderer/offline-packs/GPS/terrain/tactical.

## Verification snapshot (at closure)

- Runner: total=216, pass=176, fail=0, blocked=8, notApplicable=32 (two-run identical).
- 24 new fixtures (KEY/ENTRY/LOOKUP/FRESH/RET + ADV-044/046–050), zero dup IDs
  across 214 files. CACHE-001..007 stay NA (flow engine deferred).
- `dart analyze`: clean. `dart format --check`: clean. Leakage self-check green
  (transport/renderer/storage tokens incl. DateTime.now/Random/SQLite/Hive).
- DEC-001..019 all open; no new DECs (all sub-details owned by contract text).
