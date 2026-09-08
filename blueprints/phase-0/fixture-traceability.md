# Phase 0.4 — Fixture Traceability (Normative, Bidirectional)

- **Status:** PROPOSED. No orphaned normative fixture; no silently untested contract.

## Contract → fixtures (condensed; detail in contract-test-matrix.md)

```text
ATLAS-COORD-001 .... geo/GEO-*, adversarial/ADV-001..008+019 .... CAP-002/F-03, CAP-016/F-08
ATLAS-GEOM-001 ..... geometry/*, adversarial/ADV-010..013 ....... CAP-007..010/F-04/F-05, CAP-023/F-07
ATLAS-GEO-DIST-001 . distance/DIST-* ............................ CAP-017/F-02
ATLAS-GEO-BRG-001 .. bearing/BRG-* .............................. CAP-017/F-02
ATLAS-VALID-001 .... adversarial/ADV-* .......................... CAP-026 + skip precedents
ATLAS-CORE-CAM-001 . camera/CAM-*, adversarial/ADV-020 .......... CAP-002/005/F-03
ATLAS-MAP-ORDER-001  layers/ORDER-*, adversarial/ADV-016 ........ CAP-027/F-07
ATLAS-LAYER-001 .... layers/* descriptors ....................... CAP-004/027, CAP-R01/03
ATLAS-ATTR-001 ..... layers/ATTR-* .............................. CAP-022/F-07
ATLAS-PROV-DESC-001  layers/descriptors/* ....................... CAP-004/005, CAP-R02
ATLAS-TILE-ID-001 .. tiles/TILE-*, adversarial/ADV-017 .......... CAP-R06/F-10
ATLAS-TILE-RET-001 . cache/HIT-MISS-INVALID ..................... CAP-R06/F-10
ATLAS-TILE-CACHE-001 cache/ATOMIC+BOUNDED ....................... defects (negative findings)
ATLAS-TILE-PRE-001 . offline/prefetch-scope, cache resume ....... CAP-020/F-09, CAP-R07/F-10
ATLAS-OFF-001/2/3 .. offline/RES-*, pack-scope .................. CAP-003/020, SRC-C resume
ATLAS-LOC-* ........ location/* shape ........................... CAP-014/F-08, CAP-R04/F-11
ATLAS-DATA/PROV-001  provenance/* ............................... CAP-007..009 + baseline
ATLAS-AUTH-001 ..... parcels/paired-*, adversarial/ADV-023 ...... CAP-007>008/F-04
ATLAS-TAC-PIN-001 .. tactical/pins-* ............................ CAP-011/025/F-06
ATLAS-GEO-RING-001 . tactical/rings-* ........................... CAP-018/F-02
ATLAS-GEO-LINK-001 . radio/* (+ scope-negative) ................. CAP-019/F-01
MGRS ............... tactical/mgrs-shape (schema only) .......... CAP-016 (DECISION REQUIRED)
```

## Fixture → contract/evidence (rule)

Every file under `test/golden/` carries `contract` + `trace` fields pointing back to the rows
above (or `Source: none / Origin: architectural proposal` where applicable). Files lacking both
are orphans and FAIL the gate.

## Untested contracts (explicit, allowed)

ATLAS-PROV-REQ/RESP-001, ATLAS-PROV-DISC-001, ATLAS-VER-001, MGRS conversions — see matrix for
reasons + owning DECs. All other Phase 0.2 contracts have ≥1 fixture.
