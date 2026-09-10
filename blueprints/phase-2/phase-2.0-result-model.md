# Phase 2.0-G — Result Model Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Answers: what constitutes an execution result?
- **Depends on:** 2.0-D (command values), 2.0-E (terminal lifecycle states),
  2.0-F (cancelled reporting).

## 1. Dominant principle (normative, architect-directed)

> **An execution result reports what execution did with the command; it does
> not reinterpret the semantic outcome carried through that execution.**

## 2. Result shape (closed, four members)

`ExecutionResult` carries exactly:

1. **Command echo** — the command value actually served (echoed, never
   reconstructed: `command → execution → result` stays comparable in
   deterministic tests).
2. **Terminal lifecycle** — `succeeded | failed | cancelled` ONLY. Pending/
   running are execution observations, never result content; no second
   result-state vocabulary is invented.
3. **Carried semantic outcome** — the operation's outcome value, preserved
   verbatim: a carried acquisition `failed` (or `timedOut`, or `cancelled`)
   inside an execution-`succeeded` result is LEGAL and required (2.0-E §4
   anti-overlap table). Execution never translates an inner failure into
   `ExecutionResult.failed`, and cancellation never mutates a produced
   semantic outcome.
4. **Preserved failure** — for execution-`failed` results, the malfunction
   detail (contract violation / thrown error mapped per 2.0-L / unservable
   binding); for execution-`succeeded`-with-inner-failure, the inner
   category travels inside the carried outcome, NOT as the execution failure.

## 3. Anti-collapse invariant (normative, four layers)

```text
Command ≠ Execution lifecycle ≠ Semantic/domain outcome ≠ Payload
   what to execute → what execution did → what was produced → representation
```

Each layer is a distinct type; no layer's vocabulary leaks into another's.
 Collapsing any two (e.g. inner-failed ⇒ execution-failed, or
 outcome-contains-bytes) violates this contract.

## 4. Bytes boundary (hard line)

No raw bytes in the contract surface: no byte arrays, no decoded payloads,
no serialized forms, no memory/ownership handles, no lifetime semantics.
Results may carry opaque identities and semantic/materialization outcome
values only:

> **Execution reports semantic results; materialized payload bytes belong to
> a downstream implementation boundary.**

Anything byte-shaped (representation, decoding, storage, transport, payload
lifetime) is a downstream provider/adapter concern (2.0-A §3 refusals).

## 5. Fixture obligations (→ 2.0-N)

- Echo identity: result.command equals the served command value.
- Terminal-only: no result carries pending/running.
- Inner-failure preservation: execution-`succeeded` + acquisition-`failed`
  (category intact) passes; execution-`failed` for the same input fails.
- Cancelled result carries the unmutated command and whichever semantic
  outcome (if any) was actually produced — never a synthesized one.
- Byte-shaped fields are unrepresentable (arch-scan: byte/buffer/decode/
  serialize tokens absent from `src/execution/` result types).
