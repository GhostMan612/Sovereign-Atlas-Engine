# Phase 0.2 — Contract Decision Register

Every item below is **DECISION REQUIRED** unless marked otherwise. Nothing here is resolved by guessing.
Format: `DEC-xxx / Question / Current evidence / Possible choices / Impact / Status`.

## DEC-001 — CRS representation

- **Question:** How is a CRS identified in contracts (string code, struct, registry ref)?
- **Evidence:** WGS84 direct use VERIFIED (SRC-A F-05, SRC-B latlong2); no transformation observed.
- **Choices:** (a) opaque string code; (b) struct {authority, code, version}; (c) registry reference.
- **Impact:** Blocks coordinate/geometry/provider specs leaving draft.
- **Status:** DECISION REQUIRED.

## DEC-002 — Coordinate precision + canonical distance units

- **Question:** Required decimal precision? Are distances meters internally?
- **Evidence:** Display rules VERIFIED (M/KM, BRG); internal canonical units not observed.
- **Choices:** (a) meters canonical + double precision w/ documented tolerance; (b) unit-carrying values everywhere.
- **Impact:** Blocks fixtures + implementation.
- **Status:** DECISION REQUIRED.

## DEC-003 — Altitude / elevation / ellipsoidal / orthometric semantics

- **Question:** Are altitude and elevation distinct? Which datum? Where does terrain elevation come from?
- **Evidence:** None observed (2D sources).
- **Choices:** Defer 3D to terrain phase vs define vocabulary now (vocabulary defined PROPOSED in coordinate-contract §4; values deferred).
- **Impact:** Low for Phase 0; high for terrain.
- **Status:** DECISION REQUIRED (deferral permitted).

## DEC-004 — Longitude normalization

- **Question:** Normalize out-of-range longitudes or reject?
- **Evidence:** Camera guards reject (VERIFIED F-03); general rule unobserved.
- **Choices:** (a) normalize (document rule); (b) reject with reason.
- **Impact:** Geometry + bounds + fixtures.
- **Status:** DECISION REQUIRED.

## DEC-005 — Antimeridian / poles / crossing bounds

- **Question:** How are antimeridian-crossing bounds, polar regions, and pole singularities represented?
- **Evidence:** None observed.
- **Choices:** (a) split-polygon convention; (b) unwrapped-longitude convention; (c) reject-crossing.
- **Impact:** Geometry + camera + packs.
- **Status:** DECISION REQUIRED.

## DEC-006 — Validation error / skip-reporting channel

- **Question:** How do parsers report skips/invalids (return tuple, report object, log, callback)?
- **Evidence:** Skip-and-continue VERIFIED (blueprints, pins, levels); reporting channel unobserved.
- **Choices:** TBD in workspace phase (language-dependent).
- **Impact:** Geometry + camera + data specs.
- **Status:** DECISION REQUIRED.

## DEC-007 — Geometry strictness (closure, winding, empties, equality tolerance)

- **Question:** Auto-close rings or reject? Winding rule? Empty geometry legal? Exact vs tolerance equality?
- **Evidence:** Corrupt-skip VERIFIED; strict rules unobserved.
- **Choices:** TBD; equality tolerance blocks fixtures directly.
- **Status:** DECISION REQUIRED.

## DEC-008 — Camera defaults + bearing/pitch semantics

- **Question:** Atlas-global default camera? Bearing/pitch wrap and range rules? Fallback selection (HOME vs last-good vs error)?
- **Evidence:** Mantle HOME + guards VERIFIED as source facts; Recovery Twin Cities/zoom 9-12 VERIFIED as source facts. Neither is an Atlas default.
- **Choices:** (a) core defines no default (adapters only); (b) core defines neutral default.
- **Impact:** Camera spec + reference app.
- **Status:** DECISION REQUIRED.

## DEC-009 — Cache eviction + TTL

- **Question:** Eviction algorithm? TTL semantics per provider/layer?
- **Evidence:** Absence VERIFIED (no TTL/LRU in SRC-B/C); bounded-cache requirement VERIFIED as negative finding.
- **Choices:** Explicitly NOT chosen in Phase 0.2 per directive. Candidates deferred.
- **Impact:** Tile/Offline specs stay draft until resolved.
- **Status:** DECISION REQUIRED.

## DEC-010 — Provider auth + rate-limit / ToS model

- **Question:** How are auth, rate limits, bulk-download guards, and ToS restrictions declared and enforced?
- **Evidence:** Pinned-Hub P1 + keyless P2 + IMAGERY-no-P2 VERIFIED as transport facts; politeness gap + maxTiles VERIFIED; formal model unobserved.
- **Choices:** Descriptor-declared, centrally enforced (blueprint direction) — exact mechanism TBD.
- **Impact:** Provider/Tile/Offline specs.
- **Status:** DECISION REQUIRED.

## DEC-011 — Offline-pack integrity + expiration + versioning

- **Question:** Hash algorithm? Expiration/version semantics? Manifest schema versioning?
- **Evidence:** Zoom/layer scoping + resume/cancel VERIFIED; manifest schema unobserved.
- **Choices:** TBD.
- **Impact:** Offline spec + packs implementation.
- **Status:** DECISION REQUIRED.

## DEC-012 — Location freshness + heading truth

- **Question:** Fresh/stale thresholds? Heading-truth requirements (fix-assisted vs magnetic-only)?
- **Evidence:** 5-min last-known, 20s timeout, 15-min peer-stale, UNRELIABLE-dimming all VERIFIED as source facts, not Atlas thresholds.
- **Choices:** TBD per platform capability.
- **Impact:** Location spec + tests.
- **Status:** DECISION REQUIRED.

## DEC-013 — Dataset versioning + attribution completeness

- **Question:** Dataset version pinning/migration? Attribution string completeness rules per license?
- **Evidence:** Attribution rule VERIFIED (Mantle) with Recovery gaps noted; versioning unobserved.
- **Choices:** TBD with license catalog (Phase 2).
- **Impact:** Provenance + provider specs.
- **Status:** DECISION REQUIRED.

## Register rules

- New unresolved question → new DEC entry. No silent resolution.
- Resolving a DEC requires evidence or an ADR, then spec update. Guessing is forbidden.
