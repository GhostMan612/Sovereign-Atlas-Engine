# Phase 0.5A — Contract Reconciliation Amendment

- **Status:** Architect-directed amendment to the Phase 0.5 slice (`8fd6f1f`, intact).
- **Scope:** Documentation + comment-only. No production architecture changes.
  No blocked fixture is turned green. No decision is resolved.
- **Rule applied:** SOURCE-VERIFIED vs ATLAS-NORMATIVE vs PROPOSED vs DECISION
REQUIRED (Phase 0.2 review). A PROPOSED behavior executed in code is
explicitly provisional until its owning contract/decision closes it.

## Ruling 1 — DEC-014: Dart is the provisional vehicle, decision stays open

- **Ownership:** existing DEC-014 (no new DEC; the question is unchanged).
- **Record:** `DEC-014: OPEN — provisional implementation vehicle = Dart`
  (dependency-free pure-Dart slice matching the repo's evident shape; zero
  external dependencies; no manifests; no workspace init).
- **Consequence:** "the slice vehicle is Dart" MUST NOT be read as "Atlas
  Engine's core language is Dart." Fixtures remain language-neutral; a future
  implementation in another language still validates against the same suite.

## Ruling 2 — ADV-016: baseline is not a validity law (implementation stands)

- **Tension:** ADV-016 expects mandatory rejection of non-canonical layer order;
  the Phase 0.2 review holds the canonical baseline must not become a universal
  validity law (plugins, tactical/historical compositions, specialized
  renderers may legitimately order differently).
- **Resolution:** the implementation is CORRECT and unchanged:
  `AtlasLayerStack` accepts explicit orders and reports
  `conformsToBaseline` as a flag, not a veto.
- **Fixture posture:** ADV-016 remains BLOCKED pending contract clarification
  of ATLAS-MAP-ORDER-001 (whether conformance ever becomes a veto, and in
  which composition contexts). The implementation MUST NOT be weakened to
  chase the blocked fixture.
- **Distinction recorded:** CANONICAL BASELINE ≠ VALIDITY CONSTRAINT.

## Ruling 3 — PROPOSED behaviors executed in code (ownership, all provisional)

No new DEC entries are created: each item is owned by an existing open DEC or
by PROPOSED contract text, and each is marked PROVISIONAL — NOT ATLAS-NORMATIVE
in code comments. Per the register rules, new DECs are for new architectural
questions; these are sub-details of already-open ones.

| # | Behavior | Owner | Code mark | Fixture posture |
|---|---|---|---|---|
| 3a | Zero-length flow-segment rejection (FLOW-003/ADV-022) | DEC-007 (geometry strictness: validity rules) — still open | PROVISIONAL in `polygon.dart` | Executed against provisional; re-verdict on DEC-007 close |
| 3b | Empty-collection acceptance (ADV-013) | DEC-007 ("Empty geometry legal?") — still open | PROVISIONAL in `polygon.dart` | Executed against provisional; re-verdict on DEC-007 close |
| 3c | Ring-fraction layout [0.25, 0.5, 0.75, 1.0] | ATLAS-GEO-RING-001 contract text (PROPOSED; no DEC assigned — a measurement-construction sub-detail, not a new architectural question) | PROVISIONAL in `rings.dart` | RING-001 checks table/counts/radii-within-1%, never exact vertices |
| 3d | Coincident-bearing throw (COINCIDENT_POINTS) | ATLAS-GEO-BRG-001 contract area; BRG-004 remains the open item (no new DEC: the question "what should bearing return" IS BRG-004) | PROVISIONAL in `distance.dart` | BRG-004 stays BLOCKED; throw is implementation behavior, not contract |

## Ruling 4 — Coincident bearing (record)

```text
Coincident bearing behavior: PROVISIONAL
Status: NOT ATLAS-NORMATIVE
BRG-004: remains unresolved (BLOCKED)
```

Throwing is retained as the defensible implementation (no fake `0°`
bearing is invented). The contract choice belongs to BRG-004's future.

## What did NOT change

- Production architecture, APIs, semantics: unchanged.
- Fixture expectations, tolerances, statuses: unchanged (one prior
  traceability correction to ORDER-002 vocabulary predates this amendment
  and is recorded in the 0.5 return block).
- Blocked fixtures: still blocked (GEO-007, BRG-004, MGRS-001, ADV-008,
  ADV-010, ADV-011, ADV-014, ADV-016).
- DEC-001..019: all still open (DEC-014 annotated, not resolved).
- 0.5 gate posture: applicable fixtures pass; the rest are explicitly
  BLOCKED with honest architectural reasons — the correct target per review.
