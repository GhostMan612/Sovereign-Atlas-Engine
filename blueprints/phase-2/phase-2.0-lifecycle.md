# Phase 2.0-E — Lifecycle Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Answers: what states describe the *execution of a command* — nothing more.
- **Depends on:** 2.0-C (async shape, context), 2.0-D (command values).

## 1. Ownership rule (normative, architect-directed)

> **Execution lifecycle describes the serving act. Acquisition lifecycle
> remains the acquisition contract's vocabulary.**

There is exactly ONE execution state machine and it never overlaps the
acquisition machine: the 1.8 states (`notStarted/inProgress/succeeded/failed/
cancelled/timedOut`) continue to describe acquisition attempts; execution
states describe whether SERVING the command completed, malfunctioned, or was
cancelled. Semantic outcomes (including `timedOut` acquisitions and failed
acquisitions) travel INSIDE the execution result (2.0-G) — they are never
execution states.

## 2. States (closed, five members)

| State | Meaning (serving act only) |
|---|---|
| `pending` | Command exists; serving has not begun; no operation touched |
| `running` | Serving begun; operation engaged (or about to be) |
| `succeeded` | Serving completed and reported truthfully — REGARDLESS of the semantic outcome carried (a faithfully reported failed acquisition is execution-`succeeded`; cf. 1.9-G layer separation: materialization-invalid ≠ acquisition-failed precedent) |
| `failed` | The serving act itself malfunctioned: operation threw, violated its contract, returned a non-conforming outcome, or binding was unservable |
| `cancelled` | Serving stopped by cancellation (2.0-F); never a failure, never retried by the substrate |

Deliberately ABSENT: `timedOut` (deadlines are declared acquisition bounds
evaluated against explicit time — 1.8-I; a timed-out acquisition arrives as a
REPORTED acquisition outcome via 2.0-L translation, never as an execution
state, because the substrate owns no scheduler — 2.0-C refusal). Also absent:
paused/suspended/resumed (no mechanism), partial/deferred (no contract),
expired/stale (cache decisions, already made upstream).

## 3. Transitions (closed, four members)

- `pending → running` (serving begins; the only entry into activity).
- `running → succeeded` (truthful report produced).
- `running → failed` (serving malfunction, see §2).
- `pending → cancelled` (cancelled before any operation contact — the
  operation is never touched) and `running → cancelled` (cooperative stop,
  2.0-F).

Anything else is unrepresentable (no resume, no restart, no reset — a new
serving is a new lifecycle; lifecycles are observed values in results, never
mutable objects callers drive).

## 4. The anti-overlap guarantee (normative)

The following pairs MUST remain simultaneously representable without
contradiction (fixtures in 2.0-N prove each row):

| Execution state | Carried semantic outcome | Reads as |
|---|---|---|
| `succeeded` | acquisition `succeeded` | full success |
| `succeeded` | acquisition `failed` | truthfully reported semantic failure (NOT execution failure) |
| `succeeded` | acquisition `timedOut` | truthfully reported deadline outcome (timeout lives in acquisition vocabulary only) |
| `succeeded` | acquisition `cancelled` | operation-level cancel reported; execution serving completed |
| `failed` | none (malfunction detail instead) | substrate/operation malfunction |
| `cancelled` | none | serving stopped; cancellation ≠ failure |

If any implementation merges these two columns into one status, it violates
this contract (enforced by 2.0-P: no acquisition-state tokens as execution
states in `src/execution/`).

## 5. Fixture obligations (→ 2.0-N)

- All four transitions reachable with scripted operations; illegal
  transitions unrepresentable (constructor/transition refusal, not silent
  no-op).
- `succeeded`-with-`failed`-inside and `succeeded`-with-`timedOut`-inside are
  distinct passing cases (the core anti-collapse proof).
- Cancel-before-start never contacts the operation (observable via scripted
  operation contact log — 2.0-M preview).
