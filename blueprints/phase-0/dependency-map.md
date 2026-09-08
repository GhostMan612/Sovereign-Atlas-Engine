# Phase 0.5 — Dependency Map

Allowed dependency directions for Atlas Engine packages.
Authority: ADR-001. Violations require a new ADR.

## Legend

```text
A → B  means A may depend on B.
No arrow means no dependency is approved.
```

## Allowed directions (Phase 0)

```text
apps/* ──────────────────────► packages/* contracts
integrations/* ───────────────► packages/* contracts
examples/* ───────────────────► packages/* contracts

atlas_map ────────► atlas_core, atlas_geo, atlas_layers, atlas_provider_api, atlas_tiles, atlas_offline, atlas_data, atlas_location
atlas_layers ─────► atlas_core, atlas_geo, atlas_provider_api, atlas_data
atlas_tiles ──────► atlas_core, atlas_geo, atlas_provider_api
atlas_offline ────► atlas_core, atlas_tiles, atlas_provider_api, atlas_data
atlas_data ───────► atlas_core, atlas_geo, atlas_provider_api
atlas_tactical ───► atlas_core, atlas_geo, atlas_data
atlas_location ───► atlas_core, atlas_geo
atlas_security ───► atlas_core, atlas_data            (policy metadata; not an implementation dependency of core)
atlas_provider_api ► atlas_core
atlas_geo ────────► atlas_core
atlas_core ───────► (nothing; zero engine dependencies)
```

## Hard rules

1. `atlas_core` depends on nothing in the engine. No Flutter, no Android, no map SDK, no provider SDK in its public models.
2. `atlas_geo` imports no UI and no renderer. Pure deterministic logic only.
3. `atlas_provider_api` contains contracts only. Implementations depend on it, never the reverse.
4. Renderer adapters (MapLibre, flutter_map, future) translate engine models; engine packages never import renderer types.
5. `integrations/` and `apps/` depend on `packages/` contracts. `packages/` never depend on `integrations/` or `apps/`.
6. Reserved packages (`atlas_analysis`, `atlas_terrain`, `atlas_history`, `atlas_plugins`) have **no approved edges** in Phase 0 — neither inbound nor outbound. Wiring them early is forbidden without an ADR.
7. `atlas_security/lib/src/secrets/` has no approved edges. Do not depend on it.
8. Cross-cutting metadata (provenance, license, confidence, sensitivity) travels as data (see `security-baseline.md`), not as package dependencies.

## Near-term layering (first contracts)

```text
atlas_core (primitives, state, config, errors)
   ▲
atlas_geo (math, coords, grids, measurements)
   ▲
atlas_provider_api (descriptors) + atlas_data (feature model)
   ▲
atlas_tiles (keys, cache, policy) + atlas_layers (composition)
   ▲
atlas_offline (packs) + atlas_location (services) + atlas_tactical (domain models)
   ▲
atlas_map (orchestration + renderer abstraction)
   ▲
apps / integrations (adapters, UI)
```

## Verification (TBD harness)

- Once a workspace harness exists, add an import-lint rule enforcing this map.
- Until then, every change description must name affected packages and cite this file.
- Test fixtures (`test/golden/`) must not import renderers or app code.
