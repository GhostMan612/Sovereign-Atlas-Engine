# Phase 0.2 — Contract Specification (Master Index)

- **Status:** Normative index. Each entry traces to Phase 0.1 evidence or is
  explicitly marked `Source: none / Origin: architectural proposal / Status: PROPOSED`.
- **Detail specs:** `coordinate-contract.md`, `geometry-contract.md`, `camera-contract.md`,
  `layer-contract.md`, `provider-contract.md`, `tile-contract.md`, `offline-contract.md`,
  `location-contract.md`, `provenance-contract.md`. Forensic record: `behavior-contracts.md` (unchanged).
- **Vocabulary:** VERIFIED / PROPOSED / INFERRED / PLANNED / DECISION REQUIRED (see directive).
- **Normativity vocabulary (architect clarification, Phase 0.2 review):**
  `SOURCE-VERIFIED` = observed implementation behavior in SRC-A/B/C (a historical fact).
  `ATLAS-NORMATIVE` = a rule intentionally established for the new engine (an architectural
  decision derived from evidence, not proven universally by the source). A contract entry
  SHOULD carry both where applicable, e.g. `Evidence: SOURCE-VERIFIED` +
  `Requirement: ATLAS-NORMATIVE`. Pre-existing bare `VERIFIED`/`NORMATIVE` wording in
  Phase 0–0.2 docs is to be read through this lens; detail files below state both explicitly
  for the six conflation-sensitive areas (authority, layer order, cache-first, offline
  hierarchy, attribution, invalid-coordinate handling).

## Hierarchy

```text
CORE
├── ATLAS-COORD-001  Coordinate ............ coordinate-contract.md
├── ATLAS-GEOM-001   Geometry .............. geometry-contract.md
├── ATLAS-GEO-DIST-001 Distance ............ behavior-contracts.md → coordinate/geometry specs
├── ATLAS-GEO-BRG-001  Bearing ............. behavior-contracts.md → geometry spec
└── ATLAS-VALID-001  Validation ............ coordinate/geometry/camera specs

MAP
├── ATLAS-CORE-CAM-001 Camera .............. camera-contract.md
├── ATLAS-MAP-ORDER-001 Layer ordering ..... layer-contract.md
├── ATLAS-LAYER-001  Layer definition ...... layer-contract.md
└── ATLAS-ATTR-001   Attribution ........... layer-contract.md + provenance-contract.md

PROVIDER
├── ATLAS-PROV-DESC-001 Descriptor ......... provider-contract.md
├── ATLAS-PROV-REQ-001  Request ............ provider-contract.md
├── ATLAS-PROV-RESP-001 Response ........... provider-contract.md
├── ATLAS-PROV-FAIL-001 Failure ............ provider-contract.md
└── ATLAS-PROV-DISC-001 Capability discovery  provider-contract.md (PROPOSED)

TILES
├── ATLAS-TILE-ID-001   Addressing ......... tile-contract.md
├── ATLAS-TILE-RET-001  Retrieval .......... tile-contract.md
├── ATLAS-TILE-CACHE-001 Cache ............. tile-contract.md
└── ATLAS-TILE-PRE-001  Prefetch ........... tile-contract.md + offline-contract.md

OFFLINE
├── ATLAS-OFF-001  Hierarchy ............... offline-contract.md
├── ATLAS-OFF-002  Region/Pack/Manifest .... offline-contract.md
└── ATLAS-OFF-003  Progress/Failure ........ offline-contract.md

LOCATION
├── ATLAS-LOC-001  Resolution .............. location-contract.md
├── ATLAS-LOC-002  Fix ..................... location-contract.md
└── ATLAS-LOC-003  Freshness/Failure ....... location-contract.md

DATA
├── ATLAS-DATA-001  Source ................. provenance-contract.md
├── ATLAS-PROV-001  Provenance ............. provenance-contract.md
├── ATLAS-AUTH-001  Authority .............. provenance-contract.md + geometry-contract.md
└── ATLAS-VER-001   Version ................ provenance-contract.md (PROPOSED)

TACTICAL
├── ATLAS-TAC-PIN-001  Pin/waypoint ........ behavior-contracts.md → domain-model.md
├── ATLAS-GEO-RING-001 Range rings ......... behavior-contracts.md → geometry spec
└── ATLAS-GEO-LINK-001 Radio (smooth-earth)  behavior-contracts.md → location/provenance specs
```

## Contract register (index-level; details in per-contract files)

| Contract | Status | Normative? | Inputs → Outputs | Invariants / failure (summary) | Deps | Source evidence |
|---|---|---|---|---|---|---|
| ATLAS-COORD-001 | PROPOSED (wire VERIFIED) | Normative | WGS84 lat/lon + CRS → validated coordinate | Invalid MUST NOT silently validate | none | CAP-002/F-03 SRC-A:1612-1630; CAP-016/F-08 |
| ATLAS-GEOM-001 | PROPOSED (shapes VERIFIED) | Normative | coords → Point/Line/Polygon/Feature/Collection | Ring closure, validity; surveyed > derived | ATLAS-COORD-001 | CAP-007-010/F-04/F-05 SRC-A; CAP-023/F-07 |
| ATLAS-GEO-DIST-001 | PROPOSED (method VERIFIED) | Normative | 2 coords → km + display | Deterministic; meters canonical (DECISION REQUIRED) | ATLAS-COORD-001 | CAP-017/F-02 SRC-A:1725/:1595 |
| ATLAS-GEO-BRG-001 | PROPOSED (method VERIFIED) | Normative | 2 coords → [0,360) + display | 0°=true north (PROPOSED); coincident TBD | ATLAS-COORD-001 | CAP-017/F-02 SRC-A:1733 |
| ATLAS-VALID-001 | PROPOSED (instances VERIFIED) | Normative | any domain value → valid/invalid + reason | Fail-open to safe defaults where observed; parsers report skips (channel DECISION REQUIRED) | per-domain | CAP-026/F-03/F-05/F-06 SRC-A |
| ATLAS-CORE-CAM-001 | PROPOSED (wire+guards VERIFIED) | Normative | camera struct ↔ string | camera-max ≠ provider-native; malformed→safe default | ATLAS-COORD-001 | CAP-002/CAP-005/F-03 SRC-A |
| ATLAS-MAP-ORDER-001 | PROPOSED (order VERIFIED) | Normative | layer set → ordered render graph | Fixed relative order; one failure ≠ stack failure | ATLAS-LAYER-001 | CAP-027/F-07 SRC-A:1747-2044 |
| ATLAS-LAYER-001 | PROPOSED (fields partially VERIFIED) | Normative | definition + state → render intent | Toggles are adapter concerns (CAP-R03 correction) | ATLAS-PROV-DESC-001 | CAP-004/CAP-027/F-07; CAP-R01/R03 SRC-B |
| ATLAS-ATTR-001 | PROPOSED (rule VERIFIED) | Normative | visible layers → attribution string | Only for visible public rasters; private excluded | ATLAS-LAYER-001 | CAP-022/F-07 SRC-A:1125-1150 |
| ATLAS-PROV-DESC-001 | PROPOSED (fields partially VERIFIED) | Normative | — (descriptor data) | No URLs/keys in core | ATLAS-COORD-001 | CAP-004/005 SRC-A; CAP-R02 SRC-B/C |
| ATLAS-PROV-REQ/RESP-001 | PROPOSED | Normative | request → response + provenance | Explicit coverage/auth/version | ATLAS-PROV-DESC-001 | Source: none. Origin: architectural proposal. |
| ATLAS-PROV-FAIL-001 | PROPOSED | Normative | failure → typed state | 8 states listed; only justified subset normative (see provider-contract.md) | ATLAS-PROV-DESC-001 | Partial: SRC-A "never paint", SRC-C transparent-fail |
| ATLAS-PROV-DISC-001 | PLANNED | Informative | — | Deferred | — | Source: none. |
| ATLAS-TILE-ID-001 | PROPOSED (scheme VERIFIED) | Normative | (provider,layer,z,x,y,scheme) → identity/key | identity ≠ URL ≠ key ≠ entry ≠ payload | ATLAS-PROV-DESC-001 | CAP-R06/F-10 SRC-C:59-68 |
| ATLAS-TILE-RET-001 | PROPOSED (flow VERIFIED) | Normative | request → hit/validated-fetch/transparent-fail | Validate bytes; never throw on tile path | ATLAS-TILE-ID-001 | CAP-R06/F-10 SRC-C:148-179 |
| ATLAS-TILE-CACHE-001 | PROPOSED (req VERIFIED, algo DECISION REQUIRED) | Normative | entry + policy → store/evict | Atomic writes; bounded cache (algorithm TBD) | ATLAS-TILE-ID-001 | Defects VERIFIED (non-atomic, no TTL/LRU) as negative requirements |
| ATLAS-TILE-PRE-001 | PROPOSED (math VERIFIED, runner REBUILD) | Normative | region+zooms+layers → pack w/ progress | Bounded, cancellable, resumable, honest progress | ATLAS-OFF-002 | CAP-020/F-09 SRC-A; CAP-R07/F-10 SRC-B/C |
| ATLAS-OFF-001/002/003 | PROPOSED (order VERIFIED in part) | Normative | request → local→pack→cache→provider | Explicit hierarchy; integrity + cancel + progress | ATLAS-TILE-* | SRC-A offline-first; SRC-C skip-exists resume |
| ATLAS-LOC-001/002/003 | PROPOSED (cascade VERIFIED) | Normative | permission+service+fixes → fix/fallback | No engine-global adapter coords; freshness classes explicit | ATLAS-COORD-001 | CAP-014/F-08 SRC-A; CAP-R04/F-11 SRC-B |
| ATLAS-DATA-001 / ATLAS-PROV-001 | PROPOSED (carriage required VERIFIED as rule) | Normative | object → source+provenance answers | 7 provenance questions answerable | — | CAP-007-009/F-04/F-05; security-baseline.md |
| ATLAS-AUTH-001 | NORMATIVE rule (pattern VERIFIED) | Normative | two geometries, same object → authoritative wins | Approximation MUST NOT silently supersede authoritative | ATLAS-GEOM-001 | CAP-007>008/F-04 SRC-A |
| ATLAS-VER-001 | PROPOSED | Informative | dataset → version | Schema DECISION REQUIRED | ATLAS-DATA-001 | Source: none. Origin: architectural proposal. |
| ATLAS-TAC-PIN-001 | PROPOSED (codec+state VERIFIED) | Normative | taps ↔ CSV ↔ features; px hit-test | Codec versioned (PLANNED); projection iface | ATLAS-COORD-001 | CAP-011/CAP-025/F-06 SRC-A |
| ATLAS-GEO-RING-001 | PROPOSED (geometry VERIFIED) | Normative | center+step → 8 LineStrings | Deterministic; null→empty | ATLAS-COORD-001 | CAP-018/F-02 SRC-A:1542-1564 |
| ATLAS-GEO-LINK-001 | PROPOSED (half VERIFIED, half PLANNED) | Normative | 2 pts + antenna + freq → horizon/verdict/Fresnel + model tag | MUST carry `SMOOTH-EARTH-NO-TERRAIN` until DEM exists | ATLAS-GEO-DIST-001 | CAP-019/F-01 SRC-A:1584-1591 |

## Traceability rule (enforced)

Detail files use the chain `Contract → CAP → F-trace → SRC file:line`.
Contracts with no source state `Source: none / Origin: architectural proposal / Status: PROPOSED`.
