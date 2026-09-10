# Phase 2.0-J — Acquisition Execution Boundary (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Second of the I/J/K trio: running attempts without owning acquisition.
- **Depends on:** 1.8 (lifecycle/taxonomy), 2.0-D (`RunAcquisition`), 2.0-E
  (execution states), 2.0-H (binding), 2.0-L (translation).

## 1. Attempt rule (normative)

- ONE command = ONE attempt delegation. The substrate binds the operation by
  `request.resource`, checks cancellation, delegates, validates terminality,
  and wraps the reported `AtlasAcquisitionResult`. It performs NO lifecycle
  transition itself: no begin/complete/fail/cancel/checkTimeout calls inside
  the substrate (those are acquisition-vocabulary acts; the operation, as
  attempt performer, owns them — 2.0-E ownership rule, operation side).
- The delegated operation MUST return a terminal acquisition result
  (`succeeded/failed/cancelled/timedOut`). A pending result
  (`notStarted/inProgress`) is a contract violation → execution `failed`
  (2.0-L: OPERATION_CONTRACT_VIOLATION), never waited upon (waiting would be
  scheduler semantics).
- Cancel-before-start: cancelled WITHOUT contacting the operation
  (contact-log proof, 2.0-F F-002/F-009). Cancel-during: the operation
  observes the handle (2.0-F cooperation protocol) and reports; requested +
  reported-cancelled ⇒ execution `cancelled`; unrequested reported-cancelled
  ⇒ execution `succeeded` carrying it (2.0-E §4 row 4).

## 2. Deadline rule (normative)

- The substrate schedules nothing. Declared `timeoutSeconds` travel in the
  request policy; the OPERATION evaluates them against explicit context time
  (pure `checkTimeout`-style comparison). A timed-out attempt arrives as a
  REPORTED `timedOut` result → execution `succeeded` carrying it (2.0-E §4
  row 3). No timer, no watchdog, no `cancelAfter` (2.0-F §4).

## 3. Retry rule (normative refusal)

- The substrate performs NO retries — not on retryable failures, not ever
  (1.8-H advisory-only extended). `allowRetry` and per-category retryability
  remain ADVISORY data for downstream engines. One command, one attempt,
  one report. A retrying caller issues a NEW command (new lifecycle, 2.0-E
  §3: no restart).
