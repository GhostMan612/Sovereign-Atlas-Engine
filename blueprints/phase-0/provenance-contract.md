# ATLAS-DATA/PROV/AUTH/VER-001 — Provenance Contract (Normative)

- **Status:** PROPOSED (authority pattern + attribution rule VERIFIED; classes/schema PROPOSED).
- **Trace:** CAP-007>008/F-04 SRC-A `:1884-1885/:2117-2130/:2090-2107`;
  CAP-009/F-05 SRC-A `:2136-2152`; CAP-022/F-07 SRC-A `:1125-1150/:1012-1013`;
  security-baseline.md; `AGENTS.md` §3.
- **Normative keywords:** MUST / MUST NOT / SHOULD as written.

## 1. Provenance questions (NORMATIVE — every significant object SHOULD answer)

```text
Where did this come from? (provider + dataset)
When was it obtained? (retrievedAt)
What produced it, at what version? (source + sourceVersion + sourceDate)
What authority stands behind it? (authority class)
What transformation occurred? (method + parameters + input ref)
Is it authoritative, derived, approximate, or user-created?
What license/terms govern it? What sensitivity class?
```

Carriage of `retrievedAt/sourceDate/license/confidence/accuracy/sensitivity` is required by
the security baseline; schema below makes it contractual.

## 2. Source classes (PROPOSED vocabulary — not inherited facts)

`AUTHORITATIVE` (surveyed/cadastral/official), `DERIVED` (computed from authoritative),
`APPROXIMATE` (H3-style index/aggregate/visualization), `USER_CREATED` (pins/tracks),
`HISTORICAL` (time-scoped sources), `LIVE` (refreshed feeds), `UNKNOWN` (default when unmarked).

Missing authority MUST be treated as `UNKNOWN`, never as authoritative (PROPOSED, from geometry-contract §3).

## 3. Authority invariant ATLAS-AUTH-001 (ATLAS-NORMATIVE; pattern SOURCE-VERIFIED F-04)

> **Approximation MUST NOT silently supersede authoritative geometry.**

Precedent: surveyed parcels converted directly and drawn above H3-derived overlays (F-04).
H3 remains legitimate for indexing/aggregation/visualization/heritage — it MUST carry
`APPROXIMATE`/`DERIVED` provenance and MUST NOT outrank `AUTHORITATIVE` geometry for the same object.

## 4. Transformation history (PROPOSED)

Material geometry changes SHOULD record: source geometry ref, transformation + parameters,
output geometry ref, confidence/accuracy effect (from Master Provenance Model via security-baseline).
Observed code does not record this (e.g. `0.02°` floor, acreage squares, ring approx) — Atlas
imposes the requirement prospectively; it is NOT a claim about sources.

## 5. Versioning ATLAS-VER-001 (PROPOSED; informative in Phase 0.2)

Dataset/source-version pinning and migration semantics: DECISION REQUIRED (DEC-013).
No observed versioning scheme is enshrined.

## 6. Evidentiary note

This contract supports future court-ready/evidentiary ambitions WITHOUT importing
application-specific legal claims into Atlas: Atlas guarantees provenance carriage and
authority ordering; applications argue weight. No legal conclusion is encoded here.
