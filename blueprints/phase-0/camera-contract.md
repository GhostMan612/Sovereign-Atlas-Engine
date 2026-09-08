# ATLAS-CORE-CAM-001 — Camera Contract (Normative)

- **Status:** PROPOSED (wire, HOME, ceilings, guards VERIFIED).
- **Trace:** CAP-002/CAP-005/F-03 SRC-A `:1603/1612/1620/307-309/496-524/1615-1630`;
  CAP-005 ceilings `:318-336/:1758-1831`; max-zoom `:336/:493`; CAP-R01/R04 SRC-B `:1122-1125/:320`.
- **Normative keywords:** MUST / MUST NOT as written.

## 1. State (conceptual)

`CameraState`: `center` (AtlasCoordinate), `zoom` (float), `bearing` (degrees),
`pitch/tilt` (degrees). No language type defined.

## 2. Verified values (normative precedent)

- HOME: `39.83 / -98.58 / z3.0` (SRC-A `:307-309`) — recorded as **observed Mantle fallback**, NOT as Atlas-global default (Atlas default selection DECISION REQUIRED DEC-008; Recovery uses Twin Cities/zoom 9-12 SRC-B — same caveat).
- Provider native ceilings (observed Mantle policy): `TOPO 17 / SAT 19 / OSM 19 / DARK 20 / USGS 16 / IMAGERY 22` with `TileSet.maxZoom` set so renderers overzoom instead of 404ing (`:314-317`). These are **source data points**, not Atlas requirements; Atlas providers declare their own ceilings (see `provider-contract.md`).
- Camera maximum `24` (SRC-A `:336`), `setMaxZoomPreference` (`:493`); blueprint vectors gated `minZoom 17` (`:1908-1922`).

## 3. Cardinal distinction (ATLAS-NORMATIVE; ceilings SOURCE-VERIFIED)

```text
camera maximum zoom ≠ provider native resolution
```

Atlas MUST permit the camera to reach deep-vector levels without implying raster
data has additional resolution. Overzoom indication is PLANNED (blueprint Phase 8);
until then renderers MUST NOT fabricate detail.

## 4. Validation and failure (normative precedent VERIFIED)

- Guards: lat ±90, lng ±180, zoom 0-24, tilt 0-85; malformed → null → HOME (`:1615-1630`, `:496-507`).
- Atlas generalization PROPOSED: camera parsers MUST reject out-of-range values with reasons; the choice of fallback (HOME vs last-good vs error) belongs to the adapter/spec DEC-008, not silent core behavior.
- Bearing/pitch wrap semantics: DECISION REQUIRED (DEC-008). Bearing normalization observed for display (`%03d°`) and geometry (0-359) but camera-bearing wrap rule is not separately established.
- Persistence: rotation-surviving string state VERIFIED (`rememberSaveable:442`); disk/versioned schema PLANNED (no `SharedPreferences`/`DataStore` observed; `onCreate(null):485`).

## 5. Movement (PROPOSED)

- `recenter` (fix→z14 else HOME z3, SRC-A `:810-812`) and `move(…,14)` (SRC-B `:320`) are **adapter behaviors**, not core-camera semantics. Core defines state + validation; adapters define gestures/animation.
