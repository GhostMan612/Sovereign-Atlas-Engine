# Phase 0.8 — Acceptance Criteria and Phase 0 Gate

All boxes are unchecked: Phase 0 is in progress. Check a box only with
evidence (doc, ADR, test, or reviewed diff).

## Exit checklist (derived from master blueprint Phase 0 + §§0.1–0.2)

- [ ] Reference behavior captured (`capability-inventory.md` source-confirmed or waived per entry).
- [ ] Pure-calculation targets listed (`extraction-matrix.md` order §1–2; implementations deferred).
- [ ] Core interfaces specified (renderer, provider, offline, layer registry — specs, not code).
- [ ] Layer registry design exists (`atlas_layers` contract spec TBD).
- [ ] Provider registry design exists (`atlas_provider_api` descriptor schema TBD).
- [ ] Renderer adapter design exists (`atlas_map` interface spec TBD).
- [ ] Offline contract documented (boot, missing-tile, corruption, failure, manifest, bulk-download rules — `security-baseline.md` + specs TBD).
- [ ] Security / data-sensitivity model defined (`security-baseline.md` accepted; `secrets/` placeholder resolved by ADR).
- [ ] Provenance model defined (`security-baseline.md` §3 accepted; transformation-history carriage in `atlas_data` spec TBD).
- [ ] Dependency directions enforced (`dependency-map.md` accepted; import-lint TBD with workspace harness).
- [ ] Workspace harness decided (SDK, `pubspec`, lint, CI — all TBD, explicitly deferred in this change).
- [ ] Golden-fixture plan defined (`test/golden/` format + tolerances TBD; no fixtures in this change).
- [ ] Atlas reference application boots with no provider response (deferred to Sprint E; architectural invariant recorded: **no network → Atlas still starts**).
- [ ] No silent capability loss (inventory maintenance rule upheld).
- [ ] No production code claimed complete without tests (vacuously true: no production code exists).

## Fail-secure invariant (architectural)

```text
NO NETWORK
     ↓
ATLAS STILL STARTS
     ↓
LOCAL MAP / GRID / DATA
     ↓
NETWORK IS ENHANCEMENT, NOT A REQUIREMENT
```

Any future design that makes basic startup depend on network access fails
the gate automatically.

## Gate procedure (§0.11)

1. Architect reviews `AGENTS.md`, ADR-001, `SOURCE-MATERIAL.md`, and all eight `phase-0/` docs.
2. `git status` / `git diff` confirm only intended documentation files changed; no `packages/*/lib/` code, no dependencies, no CI, no blueprint substantive edits.
3. Each unchecked box above is either satisfied with evidence or explicitly deferred with a dated waiver + follow-up ADR number.
4. Commit as `docs: establish Atlas Engine Phase 0 foundation` (single coherent commit per architect directive).
5. Push only on explicit instruction after review.

## Deferred to post-gate (not Phase 0 failures)

- Workspace/CI foundation (§0.9), golden fixtures (§0.10), Sprint B–E code.
- Concrete provider implementations, renderer implementations, reference app.
