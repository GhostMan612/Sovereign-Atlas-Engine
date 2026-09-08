# Phase 0.3 — Workspace Architecture (Spec, No Initialization)

- **Status:** PROPOSED. No workspace tool installed; no files initialized.
- **Scaffold preserved:** existing 15-package layout unchanged (see `package-boundary-spec.md`).
- **Vocabulary:** SOURCE-VERIFIED (observed) / ATLAS-NORMATIVE (engine rule) per Phase 0.2 review.

## 1. Monorepo shape (PROPOSED)

```text
Sovereign-Atlas-Engine/
├── packages/            engine contracts + future implementation (15 dirs, unchanged)
├── integrations/        sovereign_mantle / recovery_for_all adapters (depend on packages/)
├── apps/                atlas (reference) / atlas_devtools (depend on packages/)
├── examples/            atlas_demo (depend on packages/)
├── datasets/            manifests + schemas (data, not code)
├── docs/                architecture (ADRs) / providers / security / guides
├── blueprints/          master (frozen v1.0 Draft 2026-09-07) + phase-0 specs
└── test/                architecture / golden / integration (fixtures + tests land in 0.4)
```

ATLAS-NORMATIVE: dependency direction is `apps/integrations/examples → packages/`;
`packages/ → apps/integrations` is forbidden (AGENTS.md, dependency-map.md).

## 2. Package isolation (PROPOSED)

- One responsibility per package (ADR-001). Public API = explicitly exported contract surface;
  `src/` internals are private by convention until a language mechanism is chosen (DEC-016).
- Reserved packages (`atlas_analysis`, `atlas_terrain`, `atlas_history`, `atlas_plugins`)
  remain placeholders with no inbound/outbound edges in Phase 0 (ADR-001 §2.2).
- `atlas_security/lib/src/secrets/` remains unapproved; no dependents (AGENTS.md §6).

## 3. Applications vs libraries vs integrations (ATLAS-NORMATIVE)

- `packages/`: reusable engine. MUST NOT import app screens, host SDKs, or renderer SDK types in public models.
- `integrations/`: host-specific adapters (Mantle mesh/Hub/ancestry; Recovery meetings/workflows).
  Depend on package contracts; host SDKs live here, never in `packages/`.
- `apps/`: reference Atlas app + DevTools. Consume packages like any third party.
- `examples/`: minimal consumption demos; MUST NOT become second implementations of engine logic.

## 4. Datasets and docs (PROPOSED)

- `datasets/manifests|schemas`: versioned dataset descriptors with provenance/license/sensitivity;
  large payloads are NOT vendored without a licensing + sensitivity ADR.
- `docs/architecture/`: ADRs are the only mechanism for structural change.
- Master blueprint stays frozen; phase specs evolve under it.

## 5. SDK distribution (PLANNED)

- Future: versioned package distribution per language ecosystem (registry TBD in DEC-016/DEC-019).
- No publishing configuration in Phase 0.3 (explicitly deferred per directive).

## 6. Versioning strategy (PROPOSED)

- Engine packages versioned independently with semantic versioning (PROPOSED);
  contract-breaking changes require ADR + migration note (PROPOSED).
- Monorepo tag policy and changelog mechanics: DECISION REQUIRED (DEC-019).
- Camera/pin/manifest wire schemas carry their own `version` fields once defined (PLANNED;
  current forensic wires are unversioned SOURCE-VERIFIED facts).

## 7. Explicit non-actions (Phase 0.3)

No `pubspec.yaml`, no Melos/workspace config, no `package.json`/`Cargo.toml`, no lockfiles,
no CI workflows, no generated code. Workspace initialization belongs to a later phase
after DEC-014/015/016 resolve.
