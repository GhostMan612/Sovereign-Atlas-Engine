# Phase 0.4 — Contract Test Matrix (Normative Index)

- **Status:** PROPOSED. Classification per contract: SOURCE-VERIFIED / ATLAS-NORMATIVE / PROPOSED / DECISION REQUIRED.
- **Rule:** nothing is upgraded to normative for test convenience. Gaps name their DEC.

| Contract | Fixtures | Category | Source evidence | Normative status | Impl phase |
|---|---|---|---|---|---|
| ATLAS-COORD-001 | GEO-001..008, ADV-001..008, ADV-019 | golden + adversarial | CAP-002/F-03; CAP-016/F-08 | ATLAS-NORMATIVE (values partially SOURCE-VERIFIED) | 0.5 |
| ATLAS-GEOM-001 | geometry/*, ADV-010..013, DEC-007 items | golden + adversarial | CAP-007..010/F-04/F-05 | ATLAS-NORMATIVE shape; strictness DECISION REQUIRED | 0.5 |
| ATLAS-GEO-DIST-001 | DIST-001..006 | golden | CAP-017/F-02 | ATLAS-NORMATIVE | 0.5 |
| ATLAS-GEO-BRG-001 | BRG-001..004 | golden + adversarial(shape) | CAP-017/F-02 | ATLAS-NORMATIVE; coincident DECISION REQUIRED | 0.5 |
| ATLAS-VALID-001 | ADV-* full set | adversarial | CAP-026 + skip precedents | ATLAS-NORMATIVE (channel DEC-006) | 0.5 |
| ATLAS-CORE-CAM-001 | CAM-001..006, ADV-020 | golden + adversarial | CAP-002/005/F-03 | ATLAS-NORMATIVE distinction; defaults DEC-008 | 0.5 |
| ATLAS-MAP-ORDER-001 | ORDER-001/002, ADV-016 | golden + adversarial | CAP-027/F-07 | ATLAS-NORMATIVE baseline | 0.5 |
| ATLAS-LAYER-001 | layers/descriptors, ATTR-* | golden | CAP-004/027; CAP-R01/03 | ATLAS-NORMATIVE (toggles adapter-side) | 0.5 |
| ATLAS-ATTR-001 | ATTR-001/002 | golden (negative included) | CAP-022/F-07 | ATLAS-NORMATIVE | 0.5 |
| ATLAS-PROV-DESC-001 | layers/descriptors/* | golden | CAP-004/005; CAP-R02 | ATLAS-NORMATIVE ban; fields PROPOSED | 1+ (spec 0.5) |
| ATLAS-PROV-REQ/RESP-001 | — (intentionally unrepresented: no wire observed) | — | Source: none | PROPOSED | 1+ |
| ATLAS-PROV-FAIL-001 | cache/offline failure fixtures (partial) | golden | partial (never-paint, transparent-fail) | 4 states normative; 4 PROPOSED (DEC-010) | 1+ |
| ATLAS-PROV-DISC-001 | — (PLANNED slot) | — | Source: none | PLANNED | 12 |
| ATLAS-TILE-ID-001 | TILE-001..005, ADV-017 | golden + adversarial | CAP-R06/F-10 | ATLAS-NORMATIVE | 0.5 |
| ATLAS-TILE-RET-001 | cache/HIT-MISS-INVALID | golden | CAP-R06/F-10 | ATLAS-NORMATIVE | 0.5 |
| ATLAS-TILE-CACHE-001 | cache/ATOMIC + BOUNDED requirements | golden (requirements) | defects as negative findings | ATLAS-NORMATIVE requirements; algorithm DEC-009 | 0.5+ |
| ATLAS-TILE-PRE-001 | offline/prefetch-scope, cache resume | golden | CAP-020/F-09; CAP-R07/F-10 | math normative; runner REBUILD | 0.5+ |
| ATLAS-OFF-001/002/003 | offline/RES-*, pack-scope, progress-shape | golden | CAP-003/020; SRC-C resume | ATLAS-NORMATIVE order; manifest DEC-011 | 0.5+ |
| ATLAS-LOC-001/002/003 | location/* freshness/state shape | golden (shape) | CAP-014; CAP-R04 | ATLAS-NORMATIVE correction; thresholds DEC-012 | 0.5+ |
| ATLAS-DATA/PROV-001 | provenance/* (7 questions) | golden | CAP-007..009 + baseline | ATLAS-NORMATIVE carriage | 0.5 |
| ATLAS-AUTH-001 | parcels-paired/*, ADV-023 | golden + adversarial | CAP-007>008/F-04 | ATLAS-NORMATIVE | 0.5 |
| ATLAS-VER-001 | — (intentionally unrepresented) | — | Source: none | PROPOSED (DEC-013) | 1+ |
| ATLAS-TAC-PIN-001 | tactical/pins* (codec + state) | golden | CAP-011/025/F-06 | ATLAS-NORMATIVE | 0.5 |
| ATLAS-GEO-RING-001 | tactical/rings* | golden | CAP-018/F-02 | ATLAS-NORMATIVE | 0.5 |
| ATLAS-GEO-LINK-001 | radio/* (incl. scope-negative) | golden (incl. negative) | CAP-019/F-01 | ATLAS-NORMATIVE boundary | 0.5 (smooth half) |
| MGRS/grid | tactical/mgrs-shape only | schema | CAP-016 (readout only) | DECISION REQUIRED (no vectors) | future |

Intentionally unrepresented (with reason): PROV-REQ/RESP wire (no source; proposal only),
PROV-DISC (PLANNED Phase 12), ATLAS-VER-001 (DEC-013), MGRS conversions (no library selected).
