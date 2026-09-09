# Phase 2.0 — Execution Contract Inventory (2.0-A)

- **Status:** Evidence before contract. No production code may cite this file
  until fixtures exist (execution order holds for Phase 2 exactly as Phase 1).
- **Method:** every row below traces to a CLOSED Phase 0/1 contract. Anything
  without such a trace is marked PROPOSED (needs fixture) or OPEN (needs a
  Phase 2 contract checkpoint). Source-verified behavior: neither SRC-A nor
  SRC-B/SRC-C separates execution from decision (fetch/render/cache calls are
  inline in view/controller code) — so the execution substrate is
  ATLAS-NORMATIVE construction whose entire purpose is to keep that
  non-separation out of Atlas.
- **Environment note (architect directive on OpenCode config):** the repository
  contains NO `opencode.json`/`opencode.jsonc` (glob-verified 2026-09-09) and no
  `.opencode/` directory. There is therefore no repo-side OpenCode
  configuration to preserve, migrate, or overwrite. LSP/MCP/plugin enablement
  is environment-level (user scope), not repository content: no such change is
  made here, per least privilege. If environment setup is wanted later, the
  authorized order stands — LSP first (diagnostics/symbols only), MCP second
  (allowlisted local servers only, no `npx <unknown>` / `curl|bash` / remote
  endpoints), plugins third (compatibility + capability audit first).

## 1. What Phase 1 hands to execution (the complete demand list)

| # | Phase 1 directive/terminal (owner) | Executor duty | Needs (→ checkpoint) |
|---|---|---|---|
| 1 | `useCache{entry}` / `useStaleCache{entry}` (1.9) | Serve the NAMED entry's payload through the operation bound to its identity | operation binding (2.0-H), read path of cache boundary (2.0-I) |
| 2 | `acquire{request}` (1.9 over 1.8) | Run ONE acquisition attempt for the carried request against a bound operation; report the lifecycle truthfully | command model (2.0-D), lifecycle (2.0-E), acquisition boundary (2.0-J) |
| 3 | `materialize{acquisition, handoff}` (1.9 over 1.6) | Derive the representation (pure, exists) + offer the handoff entry to the store path | materialization boundary (2.0-K), write path of cache boundary (2.0-I) |
| 4 | `materialized{…}` (1.9) | Report terminal success with provenance | result model (2.0-G) |
| 5 | All failure terminals (1.5/1.6/1.8/1.9) | Propagate WITHOUT conversion or merging (1.9-G survives execution) | error propagation (2.0-L) |
| 6 | `acquire` обслуживается "any means" (1.9-C ruling 7: network, local dataset, pack, memory) | The operation behind a command MUST be abstract: execution never branches on provider/transport kind | context + binding (2.0-C/H) |
| 7 | Cancellation (1.8 cancel, no tokens; 1.9 cancelled terminal) | Cooperative cancellation a command can honor and truthfully report | cancellation (2.0-F) |
| 8 | Explicit time everywhere (1.7-J … 1.9-L) | Time stays an explicit parameter across the semantic→execution handoff; no implicit clock in the substrate | context (2.0-C), test execution (2.0-M) |

## 2. Required primitives (nothing more until fixtures say so)

- **Execution context (2.0-C, PROPOSED):** the explicit inputs an execution
  needs — time anchor(s), cancellation handle, operation registry/binding —
  carried as a value, never ambient state. No globals, no service locator.
- **Command model (2.0-D, PROPOSED):** one command = one directive served
  (`serveEntry`, `runAcquisition`, `storeHandoff`, plus reporting). Commands
  carry their semantic payload (entry/request/handoff) so provenance survives.
- **Lifecycle (2.0-E, PROPOSED):** queued → running → terminal
  (succeeded/failed/cancelled), mirroring the acquisition lifecycle vocabulary
  (1.8-E) without redefining it. Terminal states map 1:1 onto reportable
  acquisition states.
- **Cancellation (2.0-F):** cooperative, token-free initiation (1.8 precedent:
  cancel takes pool time, not a token object); terminal truthfulness
  (cancelled ≠ failed) enforced by the result model.
- **Result model (2.0-G, PROPOSED):** command outcome + the semantic result it
  serves (acquisition result / served entry / stored handoff) + preserved
  failure category. No bytes in the substrate's contract surface (opaque
  references only, 1.8-J extended).
- **Binding (2.0-H, PROPOSED):** resource identity → operation, declared
  explicitly (a map/registry value, not discovery, not sniffing). Unknown
  identity → declared `unsupported`, never probing.
- **Cache/acquisition/materialization boundaries (2.0-I/J/K):** the substrate
  touches cache ONLY via entry-in/entry-out values (lookup stays 1.7-pure),
  runs acquisition ONLY as lifecycle transitions over an abstract operation,
  and materializes ONLY via the existing pure materializer + handoff offer.
- **Errors (2.0-L):** transport/operation errors TRANSLATE into the closed
  1.8 taxonomy at the boundary (unknown/unavailable/timeout/…); translation
  is explicit and total (no unmapped throw escapes as a mystery).
- **Deterministic test execution (2.0-M):** scripted in-memory operations
  (test scope only), explicit time, pre-declared outcomes; the SAME substrate
  code path as production binding, different operation values.

## 3. Explicitly NOT required (stays out unless a Phase 2 contract demands it)

HTTP clients, URL fetching, filesystem, databases, offline packs, payload
decoders (image/vector/terrain), renderers, Flutter/Android/GPS, isolates/
workers/queues, retry schedulers/backoff, connection pools, caches that store
(any retention machinery), implicit clocks, random IDs, telemetry. Each is a
downstream provider/adapter concern; the substrate must be fully describable
(and testable) with all of them removed.

## 4. Boundary laws (operationalized for execution)

1. Execution never re-resolves, re-ranks, reinterprets, or re-decides.
2. Execution never converts failures (report, don't merge).
3. Execution never invents identity (no IDs, no timestamps-as-identity).
4. Execution never stores silently (handoff offered, never auto-persisted).
5. Execution never blocks the semantic layer (directives are values the
   substrate serves; semantics never awaits machinery to MEAN something).

## 5. Open design points (owned by 2.0-C…M contracts, NOT decided here)

- Sync-vs-async command shape (PROPOSED lean: caller-awaited futures as
  Dart's baseline value, NOT an engine — no isolates/queues/pools; to be fixed
  by 2.0-D/E fixtures).
- Whether context carries a clock VALUE vs pure epoch ints (lean: epoch ints,
  1.9-L extended; to be fixed by 2.0-C).
- New decision needs: none identified yet. If execution exposes one, it gets
  documented → checked against DEC-001..019 → created only if uncovered.
- DEC-001..019 stay OPEN. Blocked set unchanged (8). No silent closure.
