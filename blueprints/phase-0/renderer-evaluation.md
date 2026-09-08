# Phase 0.3 — Renderer Evaluation (Engine-Independent)

- **Status:** Evaluation only. No renderer selected; no implementation.
- **ATLAS-NORMATIVE principle (preserved):** Atlas Core MUST NOT depend on a renderer.
  The renderer is an adapter/implementation boundary behind `atlas_map` contracts
  (init, camera, gestures, layer/source registration, markers, hit-test, style lifecycle, invalidation).

## 1. What the sources prove (SOURCE-VERIFIED)

- SRC-A drives MapLibre SDK directly (MapView, Style, RasterSource, GeoJsonSource, layers `:92-113/:470-530/:1752-2044`);
  layer add-order = draw order; `minZoom`/`maxZoomPreference` implement policy.
- SRC-B drives flutter_map (`MapController`, `TileLayer`, `MarkerLayer`, cluster `:51/:1116-1169`);
  one live `TileLayer` per active id; cache provider exists but is unwired (dead code F-10).
- Both confirm the Recovery-port lesson: renderer-native code is the non-portable part;
  ladders, prefetch, toggles-as-ordered-list, overlay concepts are portable.

## 2. Candidate comparison (PROPOSED)

| Renderer | Strengths | Constraints / risks | Atlas fit |
|---|---|---|---|
| MapLibre (native + web) | Mature vector/raster styling; observed in Mantle; style-driven; offline-capable; deep-zoom/overzoom semantics match CAP-005/006 | Native SDK per platform; style-spec coupling; glyph/sprite pipeline needs offline care (SRC-A avoids glyph fetch `:1951-1952`) | Strong adapter candidate where native performance + style reuse matter |
| flutter_map | Fast Flutter iteration; observed in Recovery; plugin ecosystem (cluster); adequate for raster + markers | Widget-layer coupling; performance ceiling for massive vectors/3D; cache/attribution discipline is app-authored (gaps observed) | Strong adapter candidate for Atlas-app/Recovery surfaces |
| Native platform renderers (Android Views/Canvas, iOS MapKit/Metal) | Best platform integration, sensors, backgrounding | One implementation per platform; highest maintenance; divergence risk | Adapter-only; never core |
| WebGL/WebGPU-compatible approaches (web map libs, custom GL) | Web/desktop reach; 3D/terrain future (Phase 8) | Heavy investment; toolchain churn; offline story must be rebuilt | Adapter-only; evaluate at terrain phase, not Phase 0 |

## 3. Abstraction requirements (ATLAS-NORMATIVE, from `layer-contract.md`/`camera-contract.md`)

- Engine emits: ordered render graph + camera state + marker/feature intents + attribution strings.
- Adapters own: SDK setup, style lifecycle, glyph/sprite fetching (or avoidance), gesture→command translation,
  projection services for hit-testing (56px-style tolerance precedent F-06 stays adapter-side).
- Renderer swap MUST NOT require `atlas_geo`/`atlas_core` edits (portability-matrix boundary rule).
- Renderer choice: DECISION REQUIRED (DEC-015) per surface (Atlas-app vs Mantle vs Recovery may differ).
