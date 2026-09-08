# Phase 1.2-A — Layer Contract Inventory (Expansion Scoping)

- **Status:** Inventory only. Existing column = Phase 0/1.0 behavior; verdict column
  states stabilize / extend / defer for each directive demand (§4–§38).
- **Predecessor:** `blueprints/phase-1/phase-1.0-core-api-inventory.md` (layers section).

## Existing surface (stable unless noted)

| API | Standing | Verdict |
|---|---|---|
| `AtlasLayerKind` (9 data categories) | ATLAS-NORMATIVE shape (DESC families) | STABILIZE: keep as-is; already minimal, no enum growth |
| `AtlasLayerDefinition` (id/kind/providerId/title/zoom/attribution/isPrivate) | ATLAS-NORMATIVE structure | EXTEND (see below); FIX: title must leave `==` (§11) |
| `AtlasLayerState` (visible/opacity) | ATLAS-NORMATIVE split (definition≠state) | STABILIZE: no `enabled` dimension (§22 — unneeded); opacity stays as PROVISIONAL display hint in STATE (never definition) |
| `AtlasLayerStack` + `orderedVisible` | ATLAS-NORMATIVE | EXTEND: duplicate-id validation; tie rule documented |
| `conformsToBaseline` flag | ATLAS-NORMATIVE (0.5A Ruling 2) | STABILIZE: flag-not-veto untouched (ADV-016) |
| `AtlasBaselineRanks` 9 ranks | SOURCE-VERIFIED sequence, ATLAS-NORMATIVE baseline | STABILIZE: no rank changes |
| `AtlasAttribution.forVisible` set rule | ATLAS-NORMATIVE | STABILIZE: single-string form kept (§23 — no contract demands multiples) |

## Demands → verdict

| Directive demand | Verdict | Classification |
|---|---|---|
| Three-level identity provider/dataset/layer (§5) | EXTEND: optional opaque `datasetId` beside required `providerId` | PROPOSED → PROVISIONAL (contract-owned identity text) |
| Semantic categories BASEMAP/OVERLAY/LIVE/HISTORICAL/PERSONAL (§6) | EXTEND: optional `AtlasLayerCategory?` (null = unclassified; no invented default) | PROPOSED → PROVISIONAL |
| Subtype growth (§7) | DEFER: existing 9-kind taxonomy already covers candidates | — (no change) |
| Capability advertisement (§8/9) | EXTEND: `Set<AtlasLayerCapability>` default empty (no claim); values limited to contract-supported: `offlineCapable, timeAware, queryable, selectable` | PROPOSED → PROVISIONAL (advertised only; owning subsystems implement) |
| Source-identity hook (§26) | Covered by `datasetId` + existing `providerId` (+ attribution/privacy kept separate, never collapsed) | PROPOSED → PROVISIONAL |
| Title excluded from equality (§11/32) | FIX: `==` drops `title`; three levels documented (identity/id-only, definition/structural-minus-title, state/full) | ATLAS-NORMATIVE correction (presentation≠identity) |
| State transitions (§14) | EXTEND: `toggled()`, `withOpacity` (throw outside [0,1]), `copyWith` (validated) | PROPOSED → PROVISIONAL |
| Tie-break rule (§18) | DOCUMENT: stable insertion order (List semantics); equal ranks conform | ATLAS-NORMATIVE (determinism, already true) |
| Dependency graph (§20/21) | DEFER: no implementing contract exists; cycles unrepresentable (no dep model) | — (no code, no fixtures) |
| Temporal hook (§28) | Covered by `timeAware` flag only; valid/observation/publication split recorded-future | PROPOSED → PROVISIONAL |
| Restricted-data representation (§27) | Covered by existing `isPrivate`; no mechanisms (correctly absent) | STABILIZE |
| Authority non-inference (§25) | No category→authority mapping exists or is added | STABILIZE (absence preserved) |
| Serialization (§31) | None exists, none required → none added | — |
| Untyped metadata bag (§10) | REJECTED: extension metadata stays out; normative fields only | — |
| Opacity as semantic property (§13) | UNDECIDED: stays PROVISIONAL display hint in state; never definition | — |
| Renderer/provider knowledge (§35–37) | Continue to forbid; no URL/endpoint/widget types | ATLAS-NORMATIVE (ban-list) |

## Explicit non-goals (no code, no fixtures)

Dependency graphs/cycles, multi-record attribution, authority engine, temporal
engine, encryption/auth, serialization formats, renderer/provider adapters.
