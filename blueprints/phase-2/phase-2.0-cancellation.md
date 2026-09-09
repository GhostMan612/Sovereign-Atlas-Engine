# Phase 2.0-F — Cancellation Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Answers: what cooperative cancellation means under the held-handle rule.
- **Depends on:** 2.0-C (held handle, no traded tokens), 2.0-E (cancelled is
  a lifecycle terminal, distinct from failed).

## 1. Central invariant (normative, architect-directed)

> **Cancellation is cooperative observation of an explicitly held handle; it
> is not an execution-control subsystem.**

## 2. Ownership (belongs to the execution — closed refusals)

- No global cancellation registry; no lookup by execution ID; no ambient
  singleton; no traded cancellation token; no parent/child cancellation tree
  (a tree requires its own future contract — absent here).
- The handle is created per execution by the caller, held in that execution's
  context (2.0-C), and observed — never commanded — by the operation.
- Requesting cancellation is an act on the held handle (`requestCancel()`);
  there is no `cancel(executionId)` authority anywhere in the substrate.

## 3. Cooperativeness (normative)

- The operation defines its own cooperation points; at each point it observes
  `isCancelled` and decides how to respond (stop and report cancelled, finish
  the atomic step then report, etc.). The substrate never forcibly terminates
  work — forced termination would imply isolates/threads/schedulers, all
  refused by 2.0-C.
- Request ≠ completion: the handle records that cancellation was REQUESTED;
  the lifecycle records what ACTUALLY happened (`cancelled` only when serving
  stopped as cancelled). A request arriving after truthful completion changes
  nothing (late requests are observable facts, not retroactive edits).
- Cancellation cannot alter command meaning: a cancelled serving reports
  `cancelled` with its command intact — never a reinterpreted command, never
  a substituted outcome.

## 4. Distinctions (preserved from 1.8, extended)

- `cancelled ≠ failed` (execution level, 2.0-E).
- `cancelled ≠ acquisition failure` (layer separation: a cancelled serving
  carries no acquisition-failure category).
- Cancellation state is observed lifecycle output, never caller-mutated
  lifecycle input (2.0-E §3: lifecycles are observed values).
- No implicit deadline: no `cancelAfter`, no `context.timeout`, no duration
  field anywhere near cancellation. The only authorized time value remains
  the explicit epoch-int anchor (2.0-C).

## 5. Fixture obligations (→ 2.0-N, architect-specified F-001…F-009)

- F-001 execution owns its cancellation handle (per-execution identity; two
  executions never share observation).
- F-002 cancellation is cooperative (scripted operation with defined
  cooperation points; substrate performs no forced termination).
- F-003 cancelled execution ≠ failed execution (distinct terminals).
- F-004 cancellation requires no token (initiation is an act on the held
  handle; nothing is passed to cancel).
- F-005 no global cancellation lookup exists (arch-scan: no registry/
  singleton/ID-keyed lookup tokens in `src/execution/`).
- F-006 no implicit deadline exists (arch-scan: no timeout/duration/timer
  tokens near cancellation; epoch-int anchor only).
- F-007 cancellation cannot alter command meaning (cancelled result carries
  the original command unchanged).
- F-008 cancellation state is not caller-mutated lifecycle state (no public
  transition API on lifecycle values).
- F-009 deterministic under identical context (same handle-state sequence +
  same scripted operation → identical terminal, two-run identical).
