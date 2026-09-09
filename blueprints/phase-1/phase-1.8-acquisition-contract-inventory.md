# Phase 1.8-A — Acquisition Contract Inventory (Evidence)

- **Status:** Inventory only. Answers "what does Atlas currently mean by acquire"
  from evidence alone: very little — and that sparseness IS the finding. The
  acquisition contract is almost entirely new semantic territory, which is why
  the minimal-scope discipline (1.8-E/H) governs what follows.

## 1. Phase 0 provider/acquisition evidence

- Recovery `fetchAndCache` (SRC-C F-10): 12s timeout, 200+non-empty gate,
  UA header, write-then-serve — all TRANSPORT facts, none taken (deferred).
- Recovery per-tile null-swallowing + cancel flag + snackbar counts — execution
  facts, not semantic contracts; cancellation/timeout SEMANTICS (not mechanisms)
  are the only carry-forward.
- Mantle `TilePack.prefetch` call site (SRC-A F-09): progress-shaped status
  strings (`PACKING n/m → COMPLETE`) — execution UX, not taken.
- Mantle Hub-first wiring + `runCatching → empty/false` posture — transport +
  fail-quiet behavior; the semantic residue (failure must be explicit, not
  silent) is already ATLAS-NORMATIVE elsewhere.

## 2. Contracts consumed (unchanged, not rewritten)

- ATLAS-PROV-DESC-001 (descriptors/kinds/capabilities/ranges), ATLAS-TILE-ID-001
  (identity/key/scheme), 1.5 request/result/addressing/matching/selection,
  1.6 resource identity/binding/materialization-statuses, 1.7 key/entry/lookup/
  freshness/retention semantics.

## 3. Existing acquisition assumptions found: essentially none (verified)

- No `AtlasProvider` fetch interface exists (1.4 refusal held).
- No transport/client/credential/socket/fs type exists in any package (ban-list
  greps green through 1.7).
- `AtlasMaterializationStatus.{ready,deferred,invalid}` describes representation
  availability, NOT acquisition outcome — no conflation to unwind.
- `AtlasTileRequest.resolveUrl` derives representation strings; it has never
  executed anything (no behavioral change needed, only a boundary restated).

## 4. Minimum new contract (classified)

| Element | Class | Ownership |
|---|---|---|
| `AcquisitionRequest{resource, policy?}` (target + representation intent + retry/cancel/timeout policy declarations) | PROPOSED → PROVISIONAL | Contract-owned (1.8-C) |
| Attempt identity: NO separate attempt id (1.8-D ruling: request identity + deterministic derivation suffices; random IDs banned, counters are engine state) | Rejected as unnecessary | 1.8-D inventory verdict |
| Lifecycle: `notStarted/inProgress/succeeded/failed/cancelled/timedOut` (PARTIAL/DEFERRED/UNAVAILABLE rejected as unjustified) | PROPOSED → PROVISIONAL states | 1.8-E minimal set |
| `AcquisitionResult{request, state, failure?, payloadRef?}` where payloadRef is an opaque payload ID (never bytes) | PROPOSED → PROVISIONAL | 1.8-F (materialization link below) |
| Failure taxonomy: `invalidTarget/unsupported/unavailable/timeout/cancelled/policyRejected/integrityFailure/unknown` (no HTTP codes; FAILED≠UNAVAILABLE, CANCELLED≠FAILED) | PROPOSED → PROVISIONAL | 1.8-G |
| Retry eligibility: per-failure `retryable` flag (no backoff/counters/timers) | PROPOSED → PROVISIONAL | 1.8-H |
| Cancellation vs timeout distinct; time explicit (deadline/duration inputs, never now()) | ATLAS-NORMATIVE (determinism law) | 1.8-I |
| Success binds to 1.6 materialization: acquired payload ID satisfies the materialization representation step (no decoding) | PROPOSED → PROVISIONAL link | 1.8-F/J |
| Cache relationship: contract-only — acquisition produces payload IDs a FUTURE cache may store; no lookup inside acquisition (1.8-K) | Boundary restatement | 1.7 contracts |

## 5. Explicitly deferred (not in 1.8)

PARTIAL/DEFERRED/UNAVAILABLE acquisition states, backoff/counters/queues/timers,
health/auth/credentials, status-code mapping, decoding, ranking, coverage
polygons, registry/discovery, attempt counters/IDs.

## 6. Placement ruling (1.8-B, recorded before implementation)

- **Owner: `atlas_provider_api`, new `src/acquisition/` subdir.** Acquisition is
  the execution semantics OF provider contracts (request→attempt→result over
  declared providers/resources); the package charter ("provider contracts")
  covers it without stretch, and the dependency stays core-only.
- **Not `atlas_tiles`:** acquisition ≠ caching (Law 6) — sharing a package
  would blur the exact line this phase draws.
- **Not `atlas_offline`:** durability/sync is a different concern (Law 7).
- **Not a new `atlas_acquisition` package:** unjustified by the hard rule —
  one small coherent model, no charter gap, so no ADR is necessary.
- **Not core/geo/layers/map:** wrong domain in each case (primitives/math/
  composition/viewing, none of which acquire).
