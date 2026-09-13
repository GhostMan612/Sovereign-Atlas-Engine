# Camera + Layers Sprint — Proposal for Architect Review

- **Status:** PROPOSED. Not approved, not scheduled, no code changed.
- **Audience:** project architect — disposition each open decision
  (approve / change / reject) before any implementation prompt is written.
- **Frozen blueprint:** untouched. This document proposes; it does not amend
  `ATLAS_ENGINE_MASTER_BLUEPRINT.md`.
- **Operator intent (verbatim asks):** more map layers; deeper zoom levels;
  app opens on a local, close default view; center button locates the user
  and zooms to ~15.

## 1. Source-verified baseline

**Camera today** (`apps/atlas/lib/main.dart`):
- Cold open is a world view: `_center = LatLng(0.0, 0.0)`, `_zoom = 2.0`
  (line 138–139). No permission is requested at startup; permission flows
  only from the my-location button (`_locate` → `ensureActive`, line
  590–591).
- My Location centers via `_controller.move(target, _zoom)` (lines
  288, 596) — zoom is deliberately preserved (Slice 1 behavior).
- `MapOptions` sets `initialCenter/initialZoom/initialRotation` only; no
  `minZoom`/`maxZoom` clamps (lines 777–778). `TileLayer` sets no
  `maxZoom`/`maxNativeZoom` (lines 798–807), so flutter_map 8.3.2 SDK
  defaults apply (`maxNativeZoom` default 19).

**Provider ceilings** (`packages/atlas_providers/lib/src/builtin_providers.dart`,
`live_providers.dart:21-22`): OSM 0–19, Esri imagery 0–19, Esri light/dark
gray 0–16, OpenTopoMap 0–17, USGS topo 0–16, USGS live 0–15. The basemap
picker exposes 4 casual entries (`main.dart:31-36`); the registry holds 7
(diagnostics). Per-provider `maxTiles` + `bulkGuard` policies are declared
and surfaced in the offline UI.

**Engine zoom law** (`packages/atlas_map/lib/src/camera/camera_state.dart:17-18,40`):
camera zoom valid on [0, 24]; tile addressing allows z ≤ 32
(`tile_coordinate.dart:22`).

**Engine layer model exists and is fully bypassed:** `AtlasLayerDefinition`
(kind/category/capabilities/minZoom/maxZoom/attribution/isPrivate),
`AtlasLayerState` (visible/opacity/toggled), `AtlasLayerStack`
(unique-id validation, `orderedVisible`, `conformsToBaseline`),
`AtlasBaselineRanks` (slots include `offline-graticule`, `range-rings`,
`markers`, `measurement-overlay-topmost`), `AtlasAttribution.forVisible`
— all in `packages/atlas_layers/lib/src/definition/layer_definition.dart`.
Zero references anywhere under `apps/` (verified by grep). The app renders
flutter_map layers directly: TileLayer + MarkerLayer (center pin, position,
measure A/B, waypoints) + CircleLayer (accuracy) + PolylineLayers (measure
amber, track cyan) + overlays (go-to card, compass, REC badge).

**Overlay-ready engine math, unused by any UI:** `AtlasGrids.graticuleFor`
+ `intervalForZoom` (zoom steps to 16+, `atlas_geo/.../graticule.dart:36-73`),
`AtlasRangeRings.generate` → `AtlasRingSet` (`rings.dart:35-51`).

**Offline pack guards** (`offline_repository.dart:106-121,186-203`):
prism tile counting capped at `kMaxSessionTiles = 4096` (line 22),
oversize plans blocked with `APP_PACK_TOO_LARGE`, bulk downloads gated
behind explicit operator `approvedBulk`.

## 2. Hard constraints the spec must respect

1. **DEC-021 explicitly rejected a forced recenter zoom** ("zoom
   preservation is the neutral choice"). A center-button zoom-to-15 is
   therefore a *new explicit camera policy*, not a tweak — it needs its
   own decision record and regression tests, exactly as the 4C closure
   already recorded.
2. **RES-002 (no engine-global fallback coordinates).** A "local on open"
   default must never become an engine default or a fabricated position.
   Legal sources at cold start, in priority order: live valid fix (rare
   at launch) → last-known fix (permission-gated; `lastKnownFix` exists
   but is only consumed at service start) → honest world view (current
   behavior, must remain the fallback, never labeled as user location).
3. **ToS/bulk law.** OSM forbids heavy prefetch without approval
   (`bulkGuard`); OTM similar. Deep-zoom OFFLINE packs multiply tile
   counts 4× per level — the existing estimate + approval gates must
   stay in front of any "download this area deep" UX.
4. **Renderer independence.** Any layer work goes through the existing
   `AtlasLayerStack` contracts (adapter in app), never new engine
   rendering code.

## 3. Proposed workstreams

**A. Layer control on the existing stack (no new engine model).**
Register one `AtlasLayerDefinition` per rendered thing (each provider
basemap; waypoint markers; active+completed tracks; measure overlay;
position/accuracy; graticule and rings when built), hold an
`AtlasLayerStack` in app state, drive flutter_map layer visibility /
opacity from `orderedVisible()`, compose the attribution bar from
`AtlasAttribution.forVisible`. UI: extend the Basemap picker into a
base + overlay sheet (toggles + opacity where meaningful). Acceptance:
toggling hides/shows exactly that layer; attribution follows visible
set; golden vectors for stack validate/order/attribution already exist
(`test/golden/layers/`).

**B. Zoom depth with per-provider honesty.**
Clamp map `maxZoom` per active provider ceiling; set TileLayer
`maxNativeZoom` from the endpoint descriptor so z17+ on 0–16 providers
renders as explicit overzoom (upscaled, labeled where UI shows zoom
provenance) rather than blank tiles. Engine allows to 24; SDK default
native is 19. Acceptance: each provider zoomable to ceiling + declared
overzoom band; no blank-tile dead zones; no invented data claims.
Requires a small camera-policy decision (overzoom band width + labeling)
— architect to set bounds.

**C. Local default view on open (no fallback coordinates).**
On cold start: if a valid fix is already available, open centered near
it at close zoom; else if permission grants, attempt one last-known
read with a short bounded wait; else keep the honest world view.
Never persist a stale position as "home"; never show (0,0) as the
user. The "close" default zoom is itself a decision (propose z13–15
band; architect sets the number). Acceptance: three cold-start paths
tested (fix present / last-known / nothing), world-view fallback
preserved, permission dialog only via explicit user action as today.

**D. Center-button zoom-to-15 camera policy.**
My Location centers AND sets zoom exactly 15.0; Go-To keeps preserving
zoom; manual/pinch zoom untouched; heading/compass/measure untouched.
Needs the DEC-class decision DEC-021 deferred (this reverses part of
it — say so openly). Acceptance: regression tests for all six behaviors
the 4C closure already enumerated.

**E. Overlay candidates (each independently shippable, in this order).**
1. Graticule (`AtlasGrids`, math exists; needs viewport-bounds feed +
   polyline rendering + zoom-step switching). 2. Range rings
   (`AtlasRangeRings.generate`, needs center-fix feed). 3. Offline pack
   footprints (manifest has zoomMin/zoomMax + tile entries but NO
   geographic bbox — footprint needs tile-address→bounds derivation;
   verify `tile_addressing.dart` covers it or scope a manifest addition
   through the offline contract owners). 4. Completed-track lines on
   the map (currently list-only by 4D design — deliberate scope
   decision for the architect: map clutter vs. discoverability).

## 4. Zoom math the architect should sanity-check

Tiles quadruple per level (`countTiles` prism sum,
`offline_repository.dart:106-121`, capped at 4096/session). Worked
example at the equator: z15 tiles span ~0.011° (~1.2 km); a 0.1°×0.1°
(~11 km) bbox needs ~9×9 = 81 tiles at z15 alone, ~100–110 across
z10–15. A city-scale deep pack therefore fits the session cap only for
small areas or with explicit bulk approval — the spec proposes NO cap
change; instead the UI must show the estimate BEFORE download (already
the pattern) and refuse silently-never.

## 5. Open decisions for the architect (explicit)

1. Approve workstream order A→E as sliced, or re-slice.
2. Set the overzoom band + labeling rule (B).
3. Set the "close" default zoom number + last-known wait bound (C).
4. Reverse-or-confirm DEC-021 for the zoom-15 center policy (D) — if
   reversed, mint the superseding decision record; do not edit DEC-021.
5. Completed tracks on map: yes/no (E4).
6. Pack-footprint overlay: derive bounds from tile addresses, or extend
   the manifest schema (E3; schema change needs offline-contract sign-off).
7. Layer sheet UX shape (extend picker vs. dedicated control).
8. Whether any of this belongs in the frozen master blueprint as a
   Phase entry, or stays app-track only.

## 6. Verification sketch (for implementation prompts later)

Golden vectors for stack/attribution/zoom math (extend `test/golden/`,
never mint converter-style vectors); widget tests with semantic
finders per workstream (toggle → layer presence; zoom clamps per
provider; the six zoom-15 regressions; cold-start triple); full suite
+ analyze gates; Moto G smoke per workstream; no pushes without
authorization. Estimated shape (NOT a commitment): A ~2 sessions,
B ~1, C ~1–2 (permission matrix), D ~1, E ~1 each.

## 7. Non-goals

No new providers, no ToS changes, no cap changes, no engine rendering
code, no background location, no follow mode, no 3D/terrain, no GPX
changes, no CARTO, no MGRS, no new dependencies without their own
justification records.
