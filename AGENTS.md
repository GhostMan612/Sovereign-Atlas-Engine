# AGENTS.md — Sovereign Atlas Engine Operating Constitution

This file is the authoritative operating constitution for every coding agent
(human or automated) that touches this repository. It takes precedence over
ad-hoc instructions when there is a conflict. Where this file is silent,
`blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md` and
`docs/architecture/` govern. Where neither establishes an answer, mark the
item `TBD` rather than inventing one.

## 1. Project

**Sovereign Atlas Engine** is a reusable, modular, portable geospatial mapping
and atlas engine derived initially from the tactical mapping capabilities
developed in Sovereign Mantle and ported into Recovery for All.

## 2. Architecture

- Atlas Engine is the reusable core.
- Sovereign Mantle and Recovery for All are integrations/adapters and must
  not become dependencies of Atlas Core.
- `integrations/` and `apps/` depend on `packages/` contracts. Engine
  packages must never import application screens, host-app SDKs, or
  single-vendor map SDK types in their public models.
- Platform-specific rendering remains behind renderer abstractions.
- Map and data providers remain replaceable behind provider interfaces.
- There is no monolithic map controller. Layer composition, camera state,
  data normalization, caching, and rendering are separate concerns.
- Offline operation is first-class. Basic map startup must not require
  network access.
- See `docs/architecture/ADR-001-atlas-package-and-workspace-architecture.md`
  for the canonical package map and `blueprints/phase-0/dependency-map.md`
  for allowed dependency directions.

## 3. Core principles

- No monolithic map controller.
- Platform-specific rendering must remain behind renderer abstractions.
- Map providers must remain replaceable.
- Offline operation is first-class.
- External data requires provenance and licensing metadata.
- Preserve working behavior during extraction.
- Do not silently remove existing capabilities.
- Separate domain logic from UI and rendering.
- Prefer deterministic pure geospatial logic.
- Golden fixtures are required for numerical/geospatial behavior.
- Sensitive or restricted geographic datasets require explicit
  access/provenance handling.
- Historical and Indigenous geographic data must preserve source context
  rather than being flattened into generic POIs.
- Failure modes are designed before a network/API feature is called
  complete (see master blueprint Phase 0 acceptance checklist).
- Provenance, licensing, confidence, and source metadata travel with
  spatial data (see `blueprints/phase-0/security-baseline.md`).

## 4. Development rules

- NO PIECEMEALING: when replacing or creating a complete source file,
  provide the complete resulting file, not fragments requiring manual
  reconstruction.
- Do not make speculative architectural changes without documenting the
  decision in `docs/architecture/` as an ADR.
- Do not add dependencies without documenting why they are required.
- Do not duplicate functionality that belongs in an existing Atlas package.
- Do not couple Atlas Core to Flutter UI.
- Do not couple Atlas Core to Android-specific APIs.
- Do not hard-code a single map provider.
- Do not treat network access as a requirement for basic map startup.
- Do not claim a capability is implemented until tests or acceptance
  criteria demonstrate it.
- Do not delete or rename existing packages without an ADR.
- Do not fabricate source paths, line numbers, or citations. If the source
  is not vendored in this repository, cite it as described in
  `docs/architecture/SOURCE-MATERIAL.md` and mark unverified details `TBD`.
- Do not modify the substantive content of
  `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md` without an explicit
  architect directive. It is versioned as v1.0 Draft dated 2026-09-07.

## 5. Workflow

1. Inspect before modifying. Read the affected files, the relevant
   `blueprints/phase-0/` doc, and any applicable ADR.
2. Identify affected packages. Name them explicitly and respect
   `dependency-map.md`.
3. Preserve existing behavior. Record the current capability in
   `capability-inventory.md` / `extraction-matrix.md` terms before changing it.
4. Implement the smallest coherent architectural unit. Do not bundle
   unrelated behavior.
5. Add tests/fixtures. Pure geospatial logic requires golden fixtures in
   `test/golden/` (see `blueprints/phase-0/acceptance-criteria.md`).
6. Run formatting, analysis, and tests. Do not claim completion without
   evidence.
7. Review the diff (`git status`, `git diff`). Ensure only intended files
   changed.
8. Commit with an explicit message (e.g. `docs: …`, `feat(atlas_geo): …`,
   `test: …`). Do not push unless explicitly instructed.

## 6. Current repository state (Phase 0)

- There are currently no production implementation files. `packages/*/lib/`
  contains only directory placeholders.
- Documentation-only tasks must not create production code, add
  dependencies, create CI, or modify the master blueprint's substantive
  content unless the task directive explicitly allows it.
- `atlas_analysis`, `atlas_terrain`, `atlas_history`, and `atlas_plugins`
  are reserved forward-architecture packages, not Phase 0 implementation
  scope (see ADR-001).
- `atlas_security/lib/src/secrets/` is an unapproved placeholder pending an
  ADR. Do not build on it until its status is resolved.

## 7. Change control (before coding a feature)

Answer, in the change description or ADR:

1. Is this core geospatial capability, provider, application feature, or UI?
2. Does it belong in Atlas Engine or an adapter?
3. New interface or existing interface?
4. Offline behavior?
5. Provenance model?
6. Sensitivity/sharing model?
7. Test fixtures?
8. Provider-failure behavior?
9. Max-zoom / max-density behavior?
10. Can another Atlas consumer use it without importing app-specific code?

A feature should not enter the core merely because it appears visually on
the map.
