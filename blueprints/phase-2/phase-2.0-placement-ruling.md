# Phase 2.0-B — Placement Ruling (Normative)

- **Status:** Architect-authorized 2026-09-09; recorded by execution agent.
  Binding on all Phase 2 work until a new ADR says otherwise.
- **Ruling: `atlas_tiles` owns the execution substrate** (`src/execution/`),
  as the operational continuation of the Phase 1.9 pipeline housed there.

## 1. Why `atlas_tiles`

- The package's role already expanded semantically through Phases 1.7–1.9
  (cache → pipeline) without becoming a tile-only implementation package;
  its cache entries/keys are resource-general (resource namespace, 1.7) and
  its pipeline is kind-agnostic (1.9-K). Execution coordination is the next
  semantic step, not a new domain.
- Dependency law permits nowhere else without new edges: the substrate
  coordinates provider_api types (requests/results/materializations) and
  tiles types (entries/keys/decisions). Only `atlas_tiles` (→ core,
  provider_api) and `atlas_map`/`atlas_offline` (wrong layers) can see both.
- `atlas_provider_api` stays contracts-only (dependency-map hard rule 3):
  the engine that executes operations must not live with the contracts that
  describe them.

## 2. Rejected (with reason, per architect directive)

- `atlas_core`: execution lifecycle/cancellation/binding would turn the
  dependency root into a framework (Phase 0.3 ban-list philosophy).
- `atlas_provider_api`: provider contract ≠ execution machinery; would risk
  demoting non-tile first-class status (1.4).
- New `atlas_execution`/`atlas_runtime`/`atlas_orchestration`: package-first
  architecture; no boundary evidence requires it; needs an ADR in any case.
- `atlas_offline`: offline is a downstream consumer, not the substrate.
- `atlas_data`: a future consumer of execution, not its definer.
- `atlas_map`: no renderer/map-state concern in the 2.0-A demand.

## 3. Dependency (unchanged)

```text
atlas_tiles ──► atlas_core, atlas_provider_api (geo unused)
```

No new edge, therefore no ADR (same precedent as 1.7-B/1.8-B/1.9-B).

## 4. Anti-framework boundary (normative)

The substrate produces conceptually `ExecutionContext / ExecutionCommand /
ExecutionLifecycle / Cancellation / ExecutionResult / ResourceBinding` — and
MUST NOT become `ThreadPool / WorkerQueue / ExecutorService / IsolatePool /
RetryScheduler / AsyncJobManager / NetworkClient`. Enforcement: 2.0-P
architecture verification scans `src/execution/` for framework/transport
tokens exactly as 1.9 scans pipeline sources. The abstraction stays:

```text
semantic decision → execution command → abstract operation → execution result
```
