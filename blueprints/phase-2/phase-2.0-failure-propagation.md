# Phase 2.0-L — Failure Propagation Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Answers: how failures cross into execution without semantic distortion.
- **Depends on:** 1.8-G/H (closed taxonomy + advisory retry), 1.9-G
  (provenance), 2.0-E/F/G (terminals, cancel, carried outcomes).

## 1. Two failure kinds, never mixed (normative — the demonstrated need)

- **Semantic failures** use the CLOSED 1.8 taxonomy (`invalidTarget,
  unsupported, unavailable, timeout, cancelled, policyRejected,
  integrityFailure, unknown`) and travel INSIDE carried acquisition results.
  No second semantic taxonomy is invented (architect guard): the operation
  reports these categories; the substrate preserves them byte-for-byte.
- **Mechanical malfunctions** (the serving act itself broke) use the closed
  3-member substrate set below. This set exists because mechanical facts
  CANNOT be expressed in the acquisition taxonomy without lying (an unknown
  binding is not `unavailable`; a thrown error is not `unknown`-semantic):

| Malfunction | Meaning |
|---|---|
| `unsupportedBinding` | no operation bound for the identity (incl. resourceless entries); the 2.0-H `unsupported` at execution level |
| `operationThrown` | the operation threw; runtime-type + message preserved in reason, never reinterpreted (a thrown timeout-ish error is NOT semantic `timeout` — truthfulness over convenience) |
| `contractViolation` | the operation returned a non-terminal result, a structurally invalid entry, or otherwise broke the operation protocol |

## 2. Translation table (total — no raw throw escapes, normative)

| Event at the boundary | Execution terminal | Carries |
|---|---|---|
| operation returns terminal acquisition result (any) | `succeeded` (even if inner failed/cancelled/timedOut) | the result verbatim |
| operation returns pending result | `failed` | `contractViolation` |
| operation returns invalid entry (serve/store) | `failed` | `contractViolation` |
| operation throws (any Object incl. Errors) | `failed` | `operationThrown` + type/message (NO stack trace in results — stacks leak internals and vary across runs) |
| no binding for identity | `failed` | `unsupportedBinding` + identity |
| cancel requested, operation uncontacted/contacted-then-cancelled | `cancelled` | command + produced outcome if any (2.0-G §2.4) |
| operation cooperates via `ExecutionCancelled` signal | `cancelled` | command intact (2.0-F F-007) |

## 3. Retry posture (normative)

Translation never retries, never annotates retryability beyond what the
carried result already declares (1.8-H advisory flags pass through
untouched). A `failed` execution is never retried by the substrate; callers
issue new commands.
