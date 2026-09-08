# Phase 0.3 — Platform Matrix (Support Model, PROPOSED)

Levels: `guaranteed` (contract-tested on that platform) / `target` (planned, resourced) /
`possible` (no blockers, unresourced) / `unsupported` / `renderer-dependent` (follows adapter choice).
No promises beyond evidence; terrain/3D/RF rows are explicitly weaker than core geo rows.

## 1. Engine portability intent (PROPOSED)

| Capability group | Core logic platform demands | Android | iOS | Web | Windows | Linux | macOS |
|---|---|---|---|---|---|---|---|
| Coordinates/geometry/measures (`atlas_geo`) | pure compute | guaranteed* | guaranteed* | guaranteed* | guaranteed* | guaranteed* | guaranteed* |
| State/camera serde, validation | pure compute | guaranteed* | guaranteed* | guaranteed* | guaranteed* | guaranteed* | guaranteed* |
| Layer composition, provider descriptors, provenance | pure data | guaranteed* | guaranteed* | guaranteed* | guaranteed* | guaranteed* | guaranteed* |
| Tile cache/prefetch/offline packs | filesystem + network policy | target | target | possible (storage-constrained) | target | target | target |
| Location/compass services | sensors + permissions | target (adapter) | target (adapter) | possible (browser APIs) | possible | possible | possible |
| Tactical (pins/rings/ruler/smooth-earth RF) | pure + location | target | target | target (no sensors) | target | target | target |
| Terrain/3D/viewshed/terrain-RF | heavy compute + GL | possible | possible | possible | possible | possible | possible |
| Historical timelines/comparison | data + rendering | possible | possible | target | possible | possible | possible |

`*guaranteed` = once DEC-014 (core language) resolves AND Phase 0.4 fixtures pass on that platform.
Until then every cell above is PROPOSED intent, not commitment.

## 2. Application surfaces (PROPOSED)

| Surface | Android | iOS | Web | Windows | Linux | macOS |
|---|---|---|---|---|---|---|
| Atlas App (reference) | target | target | target | possible | possible | possible |
| Sovereign Mantle integration | guaranteed (existing) | unsupported | unsupported | unsupported | unsupported | unsupported |
| Recovery for All integration | target (Flutter) | possible | possible | possible | possible | possible |
| Headless analysis (no renderer) | target | possible | possible | target | target | possible |

## 3. Renderer dependence (ATLAS-NORMATIVE note)

- Map display on any platform is `renderer-dependent` by definition; engine guarantees cover
  contracts + fixtures, never pixels.
- Do not promise terrain/3D/RF parity across platforms in Phase 0. Capability-specific
  matrices belong to Phases 8/9 with measured benchmarks.
