# Phase 2.0-M — Deterministic Execution Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Answers: what makes execution deterministic and testable.
- **Depends on:** 2.0-C (engine test, explicit time), 2.0-H (scripted
  doubles), all boundary contracts (scriptable behavior).

## 1. Determinism rule (normative)

Same (command value, context values, bound operation script) ⇒ identical
`ExecutionResult` value, bit-for-bit, across runs, isolates, and machines.
Determinism inputs are exhaustive: no clock, no randomness, no ambient
state, no iteration over unordered collections, no stack traces in results,
no timing-dependent branching anywhere in `src/execution/` or conforming
operations.

## 2. Scripted operations (test scope, normative shape)

- Test doubles live in `test/` (never production): scripted outcome
  sequences + a CONTACT LOG (ordered record of method entries with
  arguments). The log is the proof instrument for no-touch paths
  (cancel-before-start, unknown binding) and cooperation points.
- The contact log records arguments as semantic values (identities, not
  representations) and NEVER timestamps from a clock (sequence indices give
  order; explicit context time gives time).
- Production operations MUST satisfy the same protocol (2.0-H §3) but are
  downstream; Phase 2 ships no production operation beyond test doubles.

## 3. Two-run rule (normative, enforced 2.0-Q)

Every execution fixture runs twice from identical inputs; results must be
`==` AND the runner's textual report byte-identical across full-suite runs.
Flakiness anywhere (ordering, timing, hashing) fails the phase.

## 4. Arch-scan determinism (normative, enforced 2.0-P)

`src/execution/` must contain no `DateTime.now`, no `Random(`, no `Uuid`,
no clock imports, no unordered-iteration-to-output patterns over operation
registries (binding lookup is single-key exact match — order-free by
construction).
