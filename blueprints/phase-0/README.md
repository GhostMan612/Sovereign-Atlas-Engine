# Phase 0 — Foundation & Extraction

Phase 0 establishes the architecture, extraction matrix, package boundaries,
contracts, testing strategy, repository rules, provenance model, security
baseline, and first portable implementations required before production
feature development.

See the master blueprint in `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md`
(v1.0 Draft dated 2026-09-07) and the constitution in `AGENTS.md`.

## Milestone sequence

```text
0.1  Repository Constitution ............ AGENTS.md (root)
0.2  Architecture Decisions ............. docs/architecture/ADR-001 + architecture-decisions.md
0.3  Capability Inventory ............... capability-inventory.md
0.4  Extraction Matrix .................. extraction-matrix.md
0.5  Dependency Map ..................... dependency-map.md
0.6  Portability Matrix ................. portability-matrix.md
0.7  Security Baseline .................. security-baseline.md
0.8  Acceptance Criteria ................ acceptance-criteria.md
0.9  Workspace / CI Foundation .......... TBD (no CI in this change)
0.10 Golden Test Fixtures ............... TBD (no fixtures in this change)
0.11 Phase 0 Gate ....................... acceptance-criteria.md § Gate
```

## Documents in this directory

| File | Purpose |
|---|---|
| `README.md` | This index. |
| `capability-inventory.md` | Frozen reference behavior (CAP-xxx entries). |
| `extraction-matrix.md` | KEEP / EXTRACT / REBUILD / REJECT dispositions. |
| `dependency-map.md` | Allowed package dependency directions. |
| `architecture-decisions.md` | ADR index and pending decisions. |
| `portability-matrix.md` | Portable logic vs renderer-native code. |
| `security-baseline.md` | Data classes, provenance, sharing controls. |
| `acceptance-criteria.md` | Phase 0 exit checklist and gate. |

## Source discipline

Reference sources are indexed in
`docs/architecture/SOURCE-MATERIAL.md`. No Mantle or Recovery source is
vendored yet, so blueprint-reported behaviors are marked
`needs-source-confirm`. Do not invent paths, line numbers, or zoom/policy
values. Mark unknowns `TBD`.

## Gate rule

Phase 0 is not complete until `acceptance-criteria.md` is fully satisfied
and reviewed. No production code in `packages/*/lib/` until the gate
passes, except where an ADR explicitly opens a package.
