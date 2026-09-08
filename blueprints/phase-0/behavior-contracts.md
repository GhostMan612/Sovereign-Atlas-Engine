# Phase 0.1 — Behavior Contracts (Proposed, Not Implemented)

Every contract below is **Proposed** status derived from forensics
(`capability-forensics.md`). None is implemented. No package code may claim
these until a workspace ADR, golden fixtures, and a per-contract spec land.
Naming is provisional (`ATLAS-*`); package homes follow ADR-001.

## Contract states

- **Proposed** — everything in this file.
- Future states (not used yet): **Accepted** (ADR/spec approved), **Implemented** (code + tests pass), **Deprecated**.

## ATLAS-GEO-DIST-001 — Great-circle distance (proposed)

- **Source:** SRC-A `haversineKm:1725` (R6371), `fmtDistKm:1595` (M<1km else KM).
- **Input:** origin lat/lon, target lat/lon (WGS84 decimal degrees).
- **Output:** kilometers (float), display string per formatting rule.
- **Requirements:** deterministic; pure; antimeridian-safe (Planned — not observed); invalid-coordinate rejection (Planned — extend `decodeCamera`-style guards).
- **Test vectors:** TBD (fixture ADR). Must include known-distance pair + antimeridian + polar + zero-distance.
- **Home:** `atlas_geo` measurements/geometry.

## ATLAS-GEO-BRG-001 — Initial bearing (proposed)

- **Source:** SRC-A `bearingDeg:1733` (atan2, normalized 0-359).
- **Input:** 2× WGS84 points. **Output:** degrees 0-359 + display `BRG 042°`.
- **Requirements:** deterministic; pure. Edge cases TBD.
- **Home:** `atlas_geo`.

## ATLAS-GEO-RING-001 — Range rings (proposed)

- **Source:** SRC-A `rangeRingFeatureCollection:1542`; steps `0.1-5.0`, 4/step (`:249-258`); 65-pt circles, flat m/deg approx sub-pixel <10km (`:1537-1541`); N/E/S/W spokes (`:1555-1564`).
- **Input:** center (fix-preferred else crosshair per `:779-787`), step index. **Output:** 8 `LineString` (4 rings + 4 spokes), serializable.
- **Requirements:** deterministic; WGS84 semantics; configurable segment resolution; antimeridian-safe (Planned); null/negative → empty (observed `:782`).
- **Home:** `atlas_geo` measurements.

## ATLAS-GEO-LINK-001 — Smooth-earth radio estimate (proposed)

- **Source:** SRC-A `linkReadout:1584`; `K4.12`, antenna 1.5m, 2.437GHz (`:260-266`); horizon + Fresnel `8.657·√(km/freq)`; verdict `✓IN/✗BEYOND`; terrain disclaimer (`:1591`).
- **Input:** 2× points + antenna heights + frequency. **Output:** horizon km, verdict, Fresnel radius, model tag `SMOOTH-EARTH-NO-TERRAIN`, timestamp.
- **Requirements:** never imply terrain clearance; result enum (CLEAR/OBSTRUCTED/BEYOND_HORIZON/INSUFFICIENT_DATA/LOW_RESOLUTION/OFFLINE_UNAVAILABLE per blueprint Phase 9) is Planned — current source returns string verdict only.
- **Home:** `atlas_tactical` radio (terrain half reserved `atlas_terrain`/`atlas_analysis`).

## ATLAS-CORE-CAM-001 — Camera state serde (proposed)

- **Source:** SRC-A `CameraFraming:1603`, `encodeCamera:1612` (`lat|lng|zoom|bearing|tilt`), `decodeCamera:1620` with guards (`:1615-1630`), HOME fallback (`:307-309`).
- **Input/Output:** camera struct ↔ versioned string (versioning Planned; current wire has none).
- **Requirements:** round-trip; malformed→safe default (HOME); range guards preserved; disk persistence Planned (current: rotation-only).
- **Home:** `atlas_core` state; applied by `atlas_map`.

## ATLAS-TAC-PIN-001 — Pin/waypoint codec + hit-test (proposed)

- **Source:** SRC-A `parsePins:1638` (drop malformed), `appendPin:1647` (`lat|lng;…`), `liftPinNear:1656` (56px screen-space, zoom/rotation-invariant), `describeShareNear:1688`.
- **Requirements:** codec pure + versioned (Planned); hit-test behind projection interface (tolerance in px, not degrees); local vs remote (WPT cyan) distinction preserved; SOS halo semantics preserved.
- **Home:** codec `atlas_tactical` (+ `atlas_core` if shared); hit-test `atlas_map` interaction.

## ATLAS-MAP-ORDER-001 — Layer z-order (proposed)

- **Source:** SRC-A `:1747-1750`, `:1752-2044`: rasters < grid < heritage < real-parcel < blueprint < flows < rings (beneath markers) < markers < measure-topmost; blueprint `minZoom 17`; `setLayerVisibility:2077`.
- **Requirements:** explicit order table; visibility/opacity/min-max-zoom independent; renderer rebuild must not mutate core state (blueprint Phase 2).
- **Home:** `atlas_layers` (+ `atlas_map` applies).

## ATLAS-PROV-DESC-001 — Provider descriptor (proposed)

- **Source:** SRC-A zoom ceilings (`:318-336`, per-`TileSet.maxZoom`), virtual `hybrid-*://` scheme, imagery-no-fallback rule (`:174-176`); SRC-B/C template table + gaps (OSM-missing, attribution gaps).
- **Requirements:** id, scheme, zoom range, attribution, license, caching/prefetch policy, auth, version (per blueprint §0.5/Phase 2). Concrete URLs/keys never in core.
- **Home:** `atlas_provider_api`.

## ATLAS-TILE-CACHE-001 — Cache-first delivery (proposed)

- **Source:** SRC-C `:148-179`: lookup → decode-validate → delete+refetch on corrupt → fetch on miss → transparent on fail; UA + 12s timeout + 200/non-empty gate; non-atomic write (defect); no TTL/LRU (gap).
- **Requirements:** atomic tmp+rename; TTL + LRU/size cap (Planned); corruption self-recovery (observed, keep); provider-policy enforcement (bulk-download guards per blueprint §0.6); never throw on tile path.
- **Home:** `atlas_tiles` cache.

## ATLAS-TILE-PREFETCH-001 — Prefetch packs (proposed)

- **Source:** SRC-C `:206-269` (slippy ranges, `maxTiles=800`, sequential + 12ms gap, skip-exists, cancel) + SRC-A `packRegion:850-874` (zoom 3-14, TOPO always + SAT-if-on, exclusions) + SRC-B `:872-949` (zooms 11-15, first-layer-only defect, indeterminate progress defect).
- **Requirements:** bounded concurrency (Planned); per-provider politeness/ToS (Planned); multi-layer; honest progress/cancel/resume; resumable via skip-exists (observed, keep); estimated size before download (blueprint Phase 3).
- **Home:** `atlas_tiles` prefetch + `atlas_offline` manifest.

## ATLAS-LOC-RES-001 — Location resolution (proposed)

- **Source:** SRC-B cascade (`:126-201`): service-off → Twin Cities fallback; deny → same; <5min last-known; medium 20s timeout; stale fallback; SRC-A `LocationManager` GPS+NETWORK 2s, no-fused, provider guard.
- **Requirements:** explicit fallback chain with named defaults (no silent Minneapolis in Atlas core — defaults belong to adapters); permission-denied + GPS-unavailable + stale states enumerated; local-audit hook (per security baseline).
- **Home:** `atlas_location` services.

## Non-contracts (explicitly not proposed from this evidence)

Terrain LOS with occlusion, DEM/contours, routing, POI search, meeting workflows, weather products, Hub/mesh wire protocols — all **Planned** future phases or adapter-resident. No contract text is offered for them here.
