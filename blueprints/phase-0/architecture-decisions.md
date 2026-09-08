# Phase 0.2 — Architecture Decisions (Index)

ADR records live in `docs/architecture/`. This file indexes them and tracks
pending decisions. Decisions, not hallway consensus, change the architecture.

## Accepted

| ADR | Title | Date | Effect |
|---|---|---|---|
| ADR-001 | Atlas package and workspace architecture | 2026-09-08 | Canonical 15-package map; 11 Phase 0 packages + 4 reserved; `atlas_provider_api` is contracts-only; `atlas_geo` absorbs coordinates; `atlas_location` absorbs compass; no renames in Phase 0. |

## Pending (TBD — each needs its own ADR before implementation)

1. **Workspace harness:** Dart/Flutter SDK constraint, per-package `pubspec`, Melos (or alternative), `analysis_options.yaml`, import-lint enforcing `dependency-map.md`. (Blocks all production code.)
2. **CI:** Format + analyze + test workflow, golden-fixture runner, branch policy. Explicitly deferred in this change per architect directive.
3. **`atlas_security/lib/src/secrets/`:** Confirm as secret-**policy** contract, relocate, or delete. No dependents until resolved.
4. **Renderer-adapter scope:** Exact `atlas_map` interface surface (init, camera, gestures, layer/raster/vector/marker registration, hit-test, style lifecycle, invalidation) — spec before any MapLibre/flutter_map code.
5. **Provider-descriptor schema:** Required fields (id, scheme, zoom range, attribution, license, caching/prefetch policy, auth, version) — spec before any provider implementation.
6. **Offline-manifest schema:** Pack id, integrity hash, bounds, zoom range, timestamps, source versions, resumable/cancellable/progress/deletion semantics.
7. **License posture:** Repository license file (currently MISSING) and per-dataset license carriage.
8. **Golden-fixture format:** Location (`test/golden/`), serialization, tolerance policy for floating-point asserts.

## Rejected / superseded

- None yet.

## Rules

- New package, rename, deletion, merge, or dependency-edge change → new ADR.
- Master blueprint `v1.0 Draft 2026-09-07` substantive changes → explicit architect directive + ADR.
- Mark unresolved items `TBD`; do not implement around them silently.
