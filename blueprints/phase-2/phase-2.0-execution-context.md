# Phase 2.0-C — Execution Context Contract (Normative)

- **Status:** Contract text (PROPOSED → fixture confirmation in 2.0-N).
  Resolves the first 2.0-A open point: the execution/async shape.
- **Depends on:** 2.0-A (demand rows 6–8), 2.0-B (lives in
  `atlas_tiles/src/execution/`).

## 1. Async-shape ruling (normative)

- Commands are VALUES. Serving a command is expressed as a caller-awaited
  `Future` — Dart's baseline value-level asynchrony, NOT an execution engine.
- The engine test (normative, enforced by 2.0-P + 2.0-M): any substrate path
  must be replaceable by a synchronous scripted double without changing its
  meaning. Anything failing that test (scheduler, queue, pool, isolate,
  worker, retry timer) is forbidden machinery, not substrate.
- Consequences: no `Isolate`, no `Timer`-driven retries, no work queues, no
  connection pools, no background loops in `src/execution/`. Concurrency
  policy (if ever needed) belongs to a downstream adapter, never the
  substrate.

## 2. Context contents (closed set, all explicit values)

`ExecutionContext` carries exactly three things — the 2.0-A rows 6–8:

1. **Time anchor** (`nowSeconds`, epoch int; 2.0-A open point RESOLVED toward
   epoch ints per 1.9-L extension — no clock VALUE object, no `DateTime`).
   Every timestamp the substrate records comes from here or from an explicit
   caller-supplied successor value, never from ambient time.
2. **Cancellation handle** (one `ExecutionCancellation` per execution, created
   by the caller and shared into the context). Cooperative flag semantics:
   `requestCancel()` records the request; `isCancelled` reports it.
   Initiation is an act on the held handle — no capability token is traded to
   outside parties (1.8 token-free philosophy, execution side). Checking is
   the operation's duty; truthful cancelled-reporting is the substrate's.
3. **Operation binding** (explicit identity→operation map value — see 2.0-H
   for the binding contract). Lookup miss → declared `unsupported`; the
   substrate never probes, discovers, or sniffs.

## 3. Non-contents (refusals, not gaps)

No globals, no service locator, no ambient config, no logger handle, no
metrics sink, no retry policy, no timeout scheduler (deadlines stay declared
durations evaluated against explicit time, 1.8-I precedent), no credentials,
no transport handles. Each refusal keeps Semantics≠Execution checkable: the
context is constructible in a test with three literals and no world.

## 4. Fixture obligations (→ 2.0-N)

- Context constructs from three explicit values; absent time/cancel/binding
  is a construction-time refusal (not a silent default) — EXCEPT where a
  contract justifies a default (none yet; 1.9 precedent: no invented
  defaults).
- Cancellation before start → cancelled without touching the operation.
- Unknown identity → unsupported without touching any operation.
- Same context values + same scripted operation → byte-identical result
  across runs (2.0-M determinism preview).
