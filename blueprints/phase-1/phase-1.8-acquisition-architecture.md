# Phase 1.8 — Acquisition Boundary Architecture (Normative)

- **Status:** Normative for `atlas_provider_api` acquisition semantics. The
  acquisition engine (transport/storage/concurrency/decoding) is downstream and
  never authoritative over these semantics.
- **Placement (1.8-B ruling):** `atlas_provider_api/src/acquisition/` — the
  execution semantics of provider contracts; no new package (hard rule holds),
  no ADR needed, dependency stays core-only. `atlas_tiles` (cache) and
  `atlas_offline` (durability) untouched.

## 1. Request doctrine (1.8-C)

`AtlasAcquisitionRequest{resource, provider?, policy}`: target identity +
optional explicit binding (mismatch is structural INVALID_REQUEST) + declared
bounds. No URLs/methods/headers/credentials/cache-directives/attempt-ids/
timestamps. Unknown JSON keys never reach constructors (network independence).

## 2. Attempt doctrine (1.8-D)

No attempt identity exists (random IDs banned; counters are engine state).
The acquisition object IS the attempt record: request + state + optional
startedAt/updatedAt/failure/payloadId, all explicit.

## 3. Lifecycle doctrine (1.8-E/I)

States: notStarted/inProgress/succeeded/failed/cancelled/timedOut (PARTIAL/
DEFERRED/UNAVAILABLE-as-state refused). Pure transitions with INVALID_STATE on
misuse; cancel from notStarted/inProgress (no tokens); timeout as pure
deadline compare (strictly-greater, mirrors freshness); complete binds an
opaque payload ID; fail records a taxonomy category. Time is always an
explicit int epoch parameter.

## 4. Taxonomy doctrine (1.8-G/H)

Closed 8-value enum (no HTTP codes — closure-tested): invalidTarget,
unsupported, unavailable, timeout, cancelled, policyRejected, integrityFailure,
unknown. FAILED≠UNAVAILABLE, CANCELLED≠FAILED. Advisory retry flags only
(timeout/unavailable/unknown); no backoff/counters/timers anywhere.

## 5. Boundary restatement (1.8-J/K + 1.6 link)

Results carry request/state/failure/payloadId only (shape-audited). Success
preserves the resource identity for downstream materialization; bytes never
required (ADV-054). No cache lookup/entry inside acquisition; no renderer
objects; key≠entry intact beside it. URL==identity collapse structurally
refused at every layer (identity validation → request validation).

## 6. Verification snapshot (at closure)

- Runner: total=243, pass=203, fail=0, blocked=8, notApplicable=32 (two-run identical).
- 27 new fixtures (ACQ-001..015, ADV-051..062), zero dup IDs across 241 files.
- `dart analyze`: clean. `dart format --check`: clean. Leakage self-check green
  (transport/renderer/storage/credential/UUID/bytes tokens over resolution,
  resources, acquisition, tiles sources).
- DEC-001..019 all open; no new DECs (all sub-details owned by contract text).
