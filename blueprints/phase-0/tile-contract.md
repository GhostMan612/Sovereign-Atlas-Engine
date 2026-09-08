# ATLAS-TILE-ID/RET/CACHE/PRE-001 — Tile Contract (Normative)

- **Status:** PROPOSED (identity, key, cache-first flow, prefetch math VERIFIED; hardening PROPOSED).
- **Trace:** CAP-R06/F-10 SRC-C `:32-101/:104-198/:59-68/:70-76/:79-94/:135-186`;
  CAP-R07/F-10 SRC-C `:202-270` + SRC-B `:872-949`; CAP-020/F-09 SRC-A `:83/:850-874`.
- **Normative keywords:** MUST / MUST NOT as written.

## 1. Addressing ATLAS-TILE-ID-001

- Tile identity = `(provider, layer, z, x, y, scheme)` (conceptual; scheme observed: Esri `{z}/{y}/{x}` vs OSM/OTM `{z}/{x}/{y}.png` VERIFIED).
- **Five-way distinction (ATLAS-NORMATIVE; key/flow SOURCE-VERIFIED):** `tile identity ≠ tile URL ≠ storage key ≠ cache entry ≠ tile payload`.
- Storage key precedent VERIFIED: `<docs>/mapcache/<layer>/{z}_{x}_{y}.png` (SRC-C `:59-68`); subdomain rotation absent by comment (`:39-40`).
- URL templating (`{z}/{x}/{y}` substitution, dark-fallback `:70-76`) is implementation detail, not core.

## 2. Retrieval ATLAS-TILE-RET-001 (flow SOURCE-VERIFIED; requirement ATLAS-NORMATIVE)

```text
lookup (existsSync :154)
  → validate (decode instantiateImageCodec :158)
  → corrupt → delete + refetch (:160-162)
  → miss → fetch (:168-169, UA com.recoveryforall, 12s timeout, 200+non-empty gate :86-88)
  → any failure → 1×1 transparent (:163/:170/:177/:183-184), never throw
```

Atlas rule NORMATIVE: validate cached bytes; corruption self-recovers; tile-path failures MUST NOT throw.

## 3. Cache ATLAS-TILE-CACHE-001 (requirements VERIFIED as negative findings)

- Observed defects that MUST NOT be enshrined (VERIFIED): **non-atomic writes** (`writeAsBytes flush:true :89`, no tmp+rename),
  **unbounded cache** (no TTL/LRU/eviction — grep-verified absent), **implicit provider assumptions** (`osm` missing → silent dark fallback),
  **dead provider** (`CachedTileProvider` never instantiated in SRC-B; live layers network-only).
- Atlas requirements NORMATIVE: atomic tmp+rename writes; bounded cache with eviction (algorithm DECISION REQUIRED DEC-009);
  explicit per-layer templates (no silent fallback); cache provider actually wired into render path.
- TTL semantics: DECISION REQUIRED (DEC-009). Eviction algorithm: explicitly NOT chosen in Phase 0.2 per directive.

## 4. Prefetch ATLAS-TILE-PRE-001 (math VERIFIED; runner REBUILD)

- Verified math: slippy ranges (`111.32 km/deg :219-220`, clamp `:226-229`), truncate `maxTiles=800 (:244/:255-257)`,
  skip-exists resume (`:98`), cancel flag (`:261`); Mantle precedents: zoom coerce 3-14, TOPO-always + SAT-if-on, exclusions (F-09).
- Verified defects (REBUILD, not requirements): sequential `await` + 12ms gap (`:266`), first-layer-only caller (`:924` SRC-B),
  zooms 11-15 caller scope (`:930`), progress callback never repaints (indeterminate bar).
- Atlas requirements NORMATIVE: bounded, cancellable, resumable, multi-layer-capable, honest progress; estimated size before download
  (blueprint Phase 3, PROPOSED); per-provider politeness/ToS compliance (PROPOSED, DEC-010).
