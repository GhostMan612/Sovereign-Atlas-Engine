# DEC-023 — Camera Policy + Layer Host Integration (Supersedes Part of DEC-021)

- **Status:** Accepted (Camera + Layers sprint implementation prompt;
  architect-issued execution order with all 8 open decisions resolved).
- **Date:** 2026-09-13.
- **Baseline:** `785ec4a` (clean HEAD verified pre-flight; sprint work
  uncommitted at start, committed locally at close — no push).
- **Scope:** Host application only (`apps/atlas` Dart). No file under
  `packages/*/lib/` is modified (zero engine diff verified). No new
  engine contract is created. No hosted/platform dependency is added.
- **Relation to DEC-021 (preserved, NOT edited):** DEC-021 §H and its
  rejected alternative "Forcing a recenter zoom (e.g. 14)" stay exactly
  as written. This record supersedes ONLY the explicit My Location
  camera-zoom behavior: valid fix + zoom 15.0 replaces zoom preservation
  for the explicit My Location / recenter action. Go-To (Slice 4C)
  keeps preserving the current zoom; manual/pinch zoom is untouched.

## 1. Context

The sprint proposal (`blueprints/camera-layers-sprint.md`) proved by
source that the engine already owns the full vocabulary the host was
bypassing: `AtlasCameraState` + validation (`atlas_map`),
`AtlasLayerDefinition` / `AtlasLayerState` / `AtlasLayerStack` /
`AtlasBaselineRanks` / `AtlasAttribution.forVisible` (`atlas_layers`),
`AtlasGrids.graticuleFor` + `intervalForZoom` and
`AtlasRangeRings.generate` (`atlas_geo`), provider ceilings +
attribution (`atlas_providers` / `atlas_provider_api`). The host
rendered flutter_map layers directly with zero engine-layer references.
The operator asks resolved by this record: app opens on a local close
default view; center button locates the user at ~zoom 15; more map
layers with honest provider ceilings.

## 2. Decision

**A. Startup cascade (RES-002 holds).** Cold start performs a
query-only status read and starts acquisition only when permission is
already granted (never prompts at startup): valid current fix → local
view at zoom 13.0; else valid last-known seed → local view at zoom
13.0; else the existing honest world overview (0,0 / zoom 2.0,
unchanged — an overview, never labeled as user location). One-shot:
once the user gestures, startup never moves the camera again. No
0,0 fallback, no stale-as-current, no fabricated coordinate.

**B. Explicit My Location camera.** Valid current fix → move to fix at
zoom exactly 15.0, preserving the current bearing (map never rotates)
and pitch where the renderer supports it (flutter_map is 2D: pitch
stays 0.0). Stale / acquiring / denied / error / missing fix → no
camera jump, no zoom change, no fabricated coordinate; the acquiring
case keeps the existing pending-recenter behavior. No follow mode.

**C. Camera state retention.** Host-side widget/controller state only
(center/zoom/bearing/pitch, all engine-validated before any move).
No new persistence store. Malformed serialized state is rejected by
the existing `AtlasCameraState.parse` guard, never applied.

**D. Layer architecture.** The engine layer vocabulary is reused
verbatim; the host adds a thin adapter (`apps/atlas/lib/map/`) that
builds an `AtlasLayerStack` per render from the active endpoint +
visibility flags and translates it into flutter_map widgets. Engine
baseline ids are reused (`raster-sources`, `offline-graticule`,
`range-rings`, `markers`, `measurement-overlay-topmost`); the two
host-only ids (`track-line`, `position-fix`) are skipped — never
rejected — by `conformsToBaseline`, which the suite pins. Renderer
rebuilds never mutate engine state (fresh stack per build).
No Flutter types enter engine packages.

**E. Default stack (implemented capabilities only).** Base raster →
graticule → range rings → waypoints → active track → measurement →
position fix. Casual picker keeps Standard / Satellite / Topographic /
Dark; the same sheet gains toggles for exactly the five implemented
overlays (Graticule, Range Rings, Waypoints, Track, Measurement).
No toggle exists for any unimplemented capability. Visibility is
independent of provider availability; a provider failure cannot
corrupt unrelated layer state.

**F. Graticule + range rings.** Host renders only engine-generated
geometry: `AtlasGrids.graticuleFor` with `intervalForZoom(current
zoom)` over the live viewport bounds (240-line renderer guard, no
second zoom law), and `AtlasRangeRings.generate` at step index 3
(1.0 km) centered on the live valid fix only (null / invalid fix →
nothing rendered, never 0,0). Rings default OFF with graticule.
Neither is persisted as field data; measurement semantics untouched.

**G. Attribution.** Derived per render from the visible stack via
`AtlasAttribution.forVisible` — never a hard-coded string. Switching
providers changes the bar; hidden layers contribute nothing; provider
legal text is unaltered.

**H. Offline / zoom-15 safety.** My Location at z15 is a camera
intent, not a download order: no pack is created, no prefetch
expands, the 4096 session-tile cap and all provider bulk guards are
untouched (suite-pinned by existing offline tests + a new no-pack
widget test). Missing tiles fail independently; camera and layer
state stay valid without network. Provider ceilings stay
authoritative: `TileLayer.maxNativeZoom`/`minNativeZoom` are set from
the active endpoint descriptor (OSM/Esri 19, OTM 17, Esri gray/USGS
16 and below); the engine camera law is NOT clamped to the lowest
ceiling; overzoom rendering follows existing flutter_map behavior.

## 3. Dependencies (path-only, justified)

`apps/atlas/pubspec.yaml` gains two in-repo `path:` entries and no
hosted package: `atlas_map` (camera intents validated against
`AtlasCameraState` before driving the renderer) and `atlas_layers`
(stack composition + `AtlasAttribution.forVisible`). Both are
load-bearing (named engine contracts consumed by the adapter) with
zero supply-chain surface — the same pattern as the existing
`atlas_location` / `atlas_geo` / `atlas_tactical` path wiring.

## 4. Verification

`flutter analyze --no-pub` clean; host suite 240/240 (208 baseline +
32 sprint: 10 camera-policy unit + 8 layer-stack unit + 14 widget);
engine fixture suite byte-identical 453/413/0/8/32; zero `packages/`
diff. Physical smoke on Moto G 2025 is PENDING (human Android Studio
run; 40-item matrix in the sprint order).
