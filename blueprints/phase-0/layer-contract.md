# ATLAS-LAYER-001 / ATLAS-MAP-ORDER-001 / ATLAS-ATTR-001 — Layer Contract (Normative)

- **Status:** PROPOSED (ordering + attribution rule VERIFIED; field schema PROPOSED).
- **Trace:** CAP-027/F-07 SRC-A `:1747-1750/:1752-2044/:2077/:792-805`;
  CAP-004 `:156-181/:1755-1840/:396-401`; CAP-022/F-07 `:155/:285-286/:487/:1125-1150/:1012-1013`;
  CAP-R01/R03 SRC-B `:1120-1169/:69/:711-772/:756/:1135`.
- **Normative keywords:** MUST / MUST NOT as written.

## 1. Layer graph (SOURCE-VERIFIED order; ATLAS-NORMATIVE baseline)

Evidence (SOURCE-VERIFIED) below; Requirement (ATLAS-NORMATIVE): renderers MUST preserve this
relative order as the baseline composition (additional future layer groups permitted without
violating it).

```text
Raster sources
  ↓
Offline graticule (always available; paints with no network VERIFIED)
  ↓
H3 heritage (derived)
  ↓
Surveyed parcel boundaries (authoritative; above H3 VERIFIED :1884)
  ↓
Blueprint structures (above parcels VERIFIED :1901; minZoom 17 VERIFIED)
  ↓
Migration flows
  ↓
Range rings (beneath markers VERIFIED :1937-1938)
  ↓
Markers: pins / peers / stale / waypoints / SOS / self position
  ↓
Measurement overlays (topmost VERIFIED :2024)
```

Renderers MUST preserve this relative order. Absolute numeric z-values are renderer-specific (PROPOSED).

## 2. Layer definition (conceptual fields — PROPOSED)

`identity` (unique), `type` (raster/vector/marker/measurement/graticule — PROPOSED vocabulary),
`source ref`, `visibility` (bool), `opacity` (0-1), `minZoom/maxZoom`, `group`,
`attribution` (per-layer), `availability` + `failureState` (see §4).
Observed toggles VERIFIED (SRC-A `:396-407`; SRC-B `:69`); opacity/group/zoom-constraint
semantics PROPOSED (blueprint Phase 2) except blueprint `minZoom 17` VERIFIED.

## 3. CAP-R03 correction (ATLAS-NORMATIVE; adapter widgets SOURCE-VERIFIED)

**UI toggles are adapter concerns. Core consumes an ordered layer composition.**
The engine MUST NOT know about checkboxes/chips (`FilterChip :740-762`, `length>1` guard `:756`,
`keepBuffer 2/1 :1135` are all adapter details VERIFIED as adapter-resident).
Core input is an ordered list of layer states; the adapter owns widgets, guards, and overdraw tuning.

## 4. Availability and failure (normative precedent VERIFIED)

- One layer's failure MUST NOT fail the stack (observed: offline layers "simply never paint" `:132-133`).
- Per-layer states PROPOSED (subset justified): `AVAILABLE / DISABLED (user off) / OFFLINE_EMPTY (no cached data) / FAILED (typed provider failure)`.
  Full provider failure taxonomy lives in `provider-contract.md`; only these four are normative here.
- Visibility is independent of source state; opacity independent of visibility (PROPOSED, blueprint Phase 2).

## 5. Attribution ATLAS-ATTR-001 (rule SOURCE-VERIFIED; requirement ATLAS-NORMATIVE)

- Native auto-attribution was disabled (`:487`); manual line built ONLY for visible public rasters
  (Esri/OTM/USGS `:1130-1136`, `8sp/0.55α`); private IMAGERY deliberately excluded (`:1012-1013`).
- Atlas rule NORMATIVE: attribution is generated from the active visible set; private/local-only sources MUST NOT be attributed to public providers and MUST NOT leak private endpoints into attribution strings.
- Recovery gap noted (Esri/OTM unattributed in SRC-B `:1165-1169`): Atlas MUST NOT reproduce the gap; attribution strings are descriptor data (see `provider-contract.md`).
