# Phase 1.9 — Orchestration Architecture (Normative)

- **Status:** Normative for pipeline coordination semantics. Execution
  (transport/storage/offline/decode/render) consumes directives; it never
  redefines them (the hard line of directive §21 holds).
- **Placement (1.9-B ruling):** `atlas_tiles/src/pipeline/` — the only existing
  package whose approved edges cover every coordinated type (resolution/
  acquisition/materialization via provider_api; entry/key/lookup owned here;
  primitives via core). No new package, no ADR, zero new dependencies. The
  resource-generality-vs-package-name tension is documented in the inventory,
  not hidden; no rename proposed.

## 1. Decision doctrine (stateless, 1.9-E)

`AtlasPipeline.decide({resolution, cacheEntry?, acquisition?, materialization?,
nowSeconds, policy, acquisitionPolicy}) → AtlasPipelineOutcome`. No
`PipelineState` type exists by ruling: retention implies an engine. The outcome
carries decision + provenance + payloads; the execution layer threads steps.

## 2. Order doctrine (1.9-C/D/H)

Resolution terminals → descriptor-free identity binding → materialization
short-circuit (ready OR deferred; deferred is success-at-boundary) → cache
evaluation (ALWAYS; null entry = miss) → usable-hit short-circuit (hit outranks
a supplied acquisition result) → acquisition routing → policy routing. Single
acquire directive (provider fixed at resolution); handoff ENTRY CONSTRUCTION
only (maxAge undeclared → honestly stale downstream); fallback to present
entries only with declared consent; cancellation/timeout never fall back.

## 3. Provenance doctrine (1.9-G/I/J)

Four failures stay four; miss is a directive; failed/cancelled/timedOut are
three terminals carrying their categories; materialization-invalid terminates
after success. `source` (resolution/cache/acquisition/materialization) marks
every outcome. Outcomes carry values only — no bytes/paths/handles/views.

## 4. Generality doctrine (1.9-K/L)

Kind-agnostic (tile fields never read; local/geojson/elevation flow as tiles);
all time explicit ints; no clock/UUID/random/env. Binding needs no descriptor
(identity triple fully determined; hooks reattach downstream).

## 5. Verification snapshot (at closure)

- Runner: total=288, pass=248, fail=0, blocked=8, notApplicable=32 (two-run identical).
- 45 new fixtures (ORCH-001..032, ADV-063..075), zero dup IDs across 286 files.
- `dart analyze`: clean. `dart format --check`: clean. Leakage self-check green
  (tiles-wide scan incl. pipeline/); pipeline-specific scans green (no
  resolver/transport/path/renderer/clock tokens; every pipeline fixture carries
  explicit now; handoff keys twice-derived identical).
- DEC-001..019 all open; no new DECs (policy flags, statelessness, hit
  authority, fallback consent are owned by fixture contract text).
