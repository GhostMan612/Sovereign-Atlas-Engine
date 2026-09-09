# Phase 1.9 — Orchestration Contract Inventory (1.9-A) + Placement Ruling (1.9-B)

- **Status:** Evidence before contract. No production code may cite this file
  until fixtures exist (execution order: Evidence → Contract → Placement →
  Fixture → Implementation).
- **Method:** every claim below is TRACEABLE to a closed-phase contract or is
  marked PROPOSED (needs fixture) / OPEN (needs decision). Source-verified
  behavior is thin here by finding: neither SRC-A nor SRC-B/SRC-C exhibits a
  resource pipeline as a separable semantic — their fetch/cache/render steps
  are interleaved inside view/controller code, which is exactly what Atlas
  refuses to reproduce. The pipeline is therefore ATLAS-NORMATIVE construction
  over closed contracts, and every normative choice below names the contract
  that justifies it.

## 1. Stage inventory — what each stage consumes and produces

| # | Stage (owner) | Consumes | Produces | Contract |
|---|---|---|---|---|
| 1 | Resolution (provider_api, 1.5) | `AtlasResolutionRequest` + catalog | `AtlasResolutionResult` (5 statuses; provider id + tile ADDRESS on resolved) | 1.5 arch §§1–5 |
| 2 | Resource binding (provider_api, 1.6) | resolved result | `AtlasResolvedResource` / identity triple provider/kind/address | 1.6 arch §1; `fromResolution` throws unless resolved |
| 3 | Cache evaluation (tiles, 1.7) | `AtlasCacheEntry?` + explicit now | `AtlasCacheDecision` (hit/miss/stale/expired/invalid) | 1.7 arch 17-Q §§4–8; `AtlasCache.lookup` |
| 4 | Acquisition decision (provider_api, 1.8) | `AtlasAcquisitionRequest` + lifecycle events | `AtlasAcquisitionResult` (6 states; closed 8-failure taxonomy; opaque payloadId) | 1.8 arch §§1–4 |
| 5 | Materialization (provider_api, 1.6) | resource + caller template | `AtlasMaterialization` (ready/deferred/invalid) | 1.6 arch §2; deferred ≠ failure |

## 2. Handoff answers (1.9-A required questions)

- **Each stage consumes/produces:** table above. All stage results already exist
  as value types; no new stage-result semantics are needed.
- **Conditions permitting the next stage:** resolution.status == resolved (else
  terminal — binding throws otherwise, 1.6); cache evaluated always (null entry
  evaluates to miss, 1.7 rule 1); acquisition routed only on non-hit decisions;
  materialization only on usable-cache directive or succeeded acquisition.
- **Conditions terminating the pipeline:** any non-resolved resolution status;
  invalid bound identity; policy-declined stale/expired/invalid; failed/
  cancelled/timed-out acquisition without fallback; invalid materialization.
- **Existing stage-result semantics reused unchanged:** all five rows. The
  pipeline introduces NO new result type for any stage.

## 3. 1.9-C candidate-model rulings (normative for fixtures)

1. **Cache evaluation precedes acquisition: YES, always.** Null entry → miss →
   acquire directive. There is no path that acquires without evaluating cache
   (this is what makes "acquisition never occurs after usable cache hit"
   structurally true rather than conventionally true).
2. **Acquisition without cache evaluation: NO** (follows from 1).
3. **Materialization before cache insertion: YES.** Order is acquire →
   materialize-directive (+ pure handoff-entry construction) → caller stores →
   caller materializes → materialized terminal. The handoff entry is
   replacement-by-construction (1.7 precedent §10), never a storage verb.
4. **Cache insertion inside orchestration: CONSTRUCTION ONLY.** The pipeline
   builds the candidate `AtlasCacheEntry` (key + identity + storedAt + payloadId,
   maxAge null = undeclared → reads stale per 1.7 rule 5, honestly); no store/
   remove/expire verb exists anywhere in the pipeline.
5. **Failed acquisition falls back to stale cache: ONLY IF DECLARED.** New
   `AtlasPipelinePolicy.fallbackToStaleOnFailure` (required bool, no default —
   no invented defaults); fallback target must be a PRESENT entry (stale/
   expired). Miss/invalid entries cannot back anything.
6. **Local datasets participate: YES, by kind-agnosticism.** No kind switch
   exists in the pipeline; `local` flows exactly like `geojson` (1.5-H/1.6-D).
7. **Multiple acquisition paths: NO.** Provider choice is fixed at resolution
   (1.5-G); the pipeline emits one acquire directive carrying one request.

## 4. 1.9-D…K further rulings (fixture-normative)

- **Stateless decision function (answers 1.9-E):** NO `PipelineState` type. The
  pipeline is `decide({...stage results...}) → outcome`; retention is an
  execution-layer concern (a state object would imply an engine). The outcome
  carries everything contractually meaningful (decision + provenance + handoff).
- **Policy flags explicit (answers 1.9-F):** `AtlasPipelinePolicy{acquireOnStale,
  acquireOnExpired, acquireOnInvalid, fallbackToStaleOnFailure}`, all required
  bools. Stale/expired/invalid NEVER imply acquire by themselves (1.9-F
  prohibitions hold: stale≠acquire, expired≠delete, miss≠provider-fetch —
  miss routes to the acquire *directive*, which the execution layer serves by
  any means: network, local dataset, pack, memory).
- **Failure provenance (1.9-G):** four failures stay four: resolution terminals
  preserved distinctly (invalidRequest/unsupported/noMatch/ambiguous→
  needsDisambiguation — ambiguity is not failure); cache miss is a DIRECTIVE,
  never failure; acquisition failure/cancel/timeout are three distinct
  terminals (FAILED≠CANCELLED per 1.8); materialization-invalid is terminal
  even after acquisition success (no silent conversion).
- **Short-circuit (1.9-H):** materialization input (ready OR deferred) wins
  over everything (boundary already reached); usable hit wins over a supplied
  acquisition result (cache authoritative for usability — acquisition data is
  then stale information, not an error).
- **Cache≠Acquisition (1.9-I):** outcome carries `source` (resolution/cache/
  acquisition/materialization); decisions and results never mix types.
- **Materialization≠rendering (1.9-J):** outcomes carry materialization values
  only; no pixel/widget/view type may appear (arch-grep enforced).
- **Non-tile (1.9-K):** pipeline touches identity/kind only, never tile fields;
  deferred materialization is success-at-boundary (1.6), so non-tiles terminate
  `materialized` without representation.
- **Determinism (1.9-L):** every `now` explicit int; no clock/UUID/random/env.

## 5. Binding without descriptors (normative construction detail)

The pipeline binds `AtlasResolvedResource` from the resolution result WITHOUT a
provider descriptor (identity triple is fully determined: provider id,
request.kind, tile rendering or ''). Attribution/license/sensitivity hooks stay
null in the pipeline-bound resource and reattach downstream (equality is
identity-based per 1.6, so routing is unaffected). This keeps catalogs/
descriptors out of orchestration semantics.

## 6. Placement ruling (1.9-B): `atlas_tiles/src/pipeline/`

- **Ruled owner: `atlas_tiles`.** It is the ONLY existing package whose approved
  edges (dependency-map.md: tiles → core, geo, provider_api) cover every type
  the pipeline coordinates (resolution/acquisition/materialization from
  provider_api; entry/key/lookup from tiles; primitives from core). No new
  edge, therefore no ADR (same precedent as 1.7-B/1.8-B).
- **Rejected:** new `atlas_orchestration/pipeline/engine_runtime` package (hard
  rule + directive §4: no package merely for a convenient name); `atlas_map`
  (renderer-facing orchestration — camera/gestures/markers per ADR-001; the
  data-plane pipeline sits BELOW map); `atlas_provider_api` (may not see tiles
  — provider_api → core only); `atlas_data` (cannot see tiles); `atlas_core`
  (sees nothing); reserved packages (no approved edges, Phase 0 hard rule 6).
- **Naming tension (documented, not hidden):** the pipeline is resource-general
  but lives in a package named "tiles". Justified: the cache↔acquisition branch
  is its center; tiles already handles resource-scope keys/entries (1.7); all
  pipeline logic is kind-agnostic (1.9-K). A rename is NOT proposed (needs ADR;
  no semantic need).
- **Dependency:** core + provider_api only (geo unused). Zero new dependencies.

## 7. Unresolved / open (honestly tracked, NOT decided here)

- DEC-001..019 stay OPEN (eviction/TTL/ranking/pack mechanics remain engine
  concerns and stay out of routing).
- No new DECs: policy-flag semantics and statelessness are owned by the fixture
  contract text below (same precedent as 1.5/1.8 sub-details).
- Blocked set unchanged (8): GEO-007/ADV-008, ADV-010/011, ADV-014, ADV-016,
  BRG-004, MGRS-001, BOX-003/ADV-025.
