# ADR-004 — Activate Reserved Packages for GIS Phases

- **Status:** Accepted (GIS phase track prerequisite)
- **Date:** 2026-09-10
- **Deciders:** Operator directive (blueprint order) + execution agent
- **Scope:** Move 4 placeholder packages into implementation scope. No
  renames, no deletions, no new packages.

## 1. Context

ADR-001 §2.2 reserved `atlas_terrain` (Phase 8), `atlas_analysis`
(Phases 9/11), `atlas_history` (Phase 7), `atlas_plugins` (Phase 12) with no
approved edges, requiring a new ADR to enter implementation scope. The GIS
track (blueprint Phases 4–12, engine side) needs exactly these four.

## 2. Decision

All four enter implementation scope with these allowed edges (additive to
dependency-map.md; all other hard rules stand):

```text
atlas_terrain ──► atlas_core, atlas_geo
atlas_analysis ─► atlas_core, atlas_geo
atlas_history ──► atlas_core, atlas_data
atlas_plugins ──► atlas_core
```

- `atlas_analysis` does NOT depend on `atlas_terrain`: elevation enters
  analysis through an injected sampler function (same pattern as injected
  transports/readers — no edge, no coupling, testable).
- `atlas_history` bridges data + time only (no geo edge: positions travel
  inside feature payloads, never as geometry deps).
- `atlas_plugins` stays minimal on purpose (manifest/registry/permissions
  are admin data; capability grants reference package names as strings, not
  imports — plugins must never become a backdoor dependency hub).
- Each gets a minimal pubspec (offline-safe, `publish_to: none`, path deps
  per edges above) + barrel; the root workspace package gains the four path
  deps. Reserved-scope NA fixture dirs (`h3`, `structures`, `radio`) STAY
  untouched (their subjects remain deferred as documented).

## 3. Consequences

- GIS implementations may import only along the edges above (verified by
  `dart analyze` + import review; a future lint rule can enforce).
- MGRS-001 and related blocked items stay blocked (hooks only, no crypto/
  grid-precision claims).
- No 3D rendering, no terrain tiles fetching, no live radio hardware, no UI:
  engine models + pure math only (app/host bindings are the app-host track).
