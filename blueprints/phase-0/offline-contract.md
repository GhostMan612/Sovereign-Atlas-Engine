# ATLAS-OFF-001/002/003 — Offline Contract (Normative)

- **Status:** PROPOSED (boot + resume precedents VERIFIED; hierarchy + manifest PROPOSED).
- **Trace:** CAP-003/F-07 SRC-A `:121-133/:150/:1753-1754/:1842-1843`;
  CAP-020/F-09 SRC-A `:850-874`; CAP-R06/R07/F-10 SRC-C + SRC-B `:872-949`;
  security-baseline.md; blueprint §0.6/Phase 3.
- **Normative keywords:** MUST / MUST NOT as written.

## 1. Retrieval hierarchy ATLAS-OFF-001 (ATLAS-NORMATIVE proposal; precedents SOURCE-VERIFIED)

```text
request
  ↓
local dataset (LOCAL_ONLY / user data)
  ↓
offline pack (manifested, integrity-checked)
  ↓
persistent cache (validated bytes)
  ↓
provider / network (policy-gated)
```

Rationale: preserves fail-secure boot (VERIFIED) while giving packs precedence over
opportunistic cache. Reordering requires an ADR.

## 2. Region / pack / manifest ATLAS-OFF-002 (fields PROPOSED; semantics partially VERIFIED)

- `OfflineRegion` (conceptual): bounds + zoom range + layer selection.
- `OfflinePack` (conceptual): region + payloads + `PackManifest`.
- Manifest fields PROPOSED: pack id, integrity hash, bounds, zoom range, layer list,
  creation time, source versions, attribution, storage size, expiration/version.
  Observed precedents VERIFIED: zoom scoping, layer inclusion/exclusion rules, `maxTiles`,
  skip-exists resume, cancel flag, completion counts.
- Integrity/expiration/version mechanics: DECISION REQUIRED (DEC-011). No observed manifest schema is enshrined.

## 3. Progress / cancellation / failure ATLAS-OFF-003

- VERIFIED precedents: `PACKING n/m → COMPLETE·N TILES` status strings (SRC-A); cancel flag + partial counts + snackbar (SRC-B/C).
- Recovery defects → Atlas rules NORMATIVE: dead progress reporting REBUILD (progress MUST repaint);
  single-layer sequential assumption REBUILD; missing layer templates REJECT; non-atomic storage REJECT.
- Pack download MUST be resumable, cancellable, deletable, with honest progress (blueprint Phase 3 checklist, PROPOSED as normative here).
- Provider bulk-download/rate rules MUST be enforced before download with user-visible estimates/restrictions
  (blueprint §0.6/Phase 3; enforcement point PROPOSED, DEC-010).

## 4. Offline boot invariant (ATLAS-NORMATIVE, restated; boot SOURCE-VERIFIED F-07)

```text
NO NETWORK → ATLAS STILL STARTS → LOCAL MAP / GRID / DATA.
NETWORK IS ENHANCEMENT, NOT A REQUIREMENT.
```

Basic startup MUST NOT require network access (`AGENTS.md`, acceptance-criteria.md gate).
