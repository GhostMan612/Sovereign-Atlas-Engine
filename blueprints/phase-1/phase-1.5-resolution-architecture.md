# Phase 1.5 — Resolution Architecture (Normative)

- **Status:** Normative for `atlas_provider_api` resolution. Stops at the
  Resolution Result; acquisition, caching, and rendering are downstream and
  never authoritative over these semantics.

## 1. Request doctrine (1.5-B)

`AtlasResolutionRequest{kind, latitude, longitude, zoom, scheme,
preferredProviders}` is the entire vocabulary: what class, where, at what
semantic zoom, under which tile scheme, with what explicit preference order.
Validation (finite/in-range position, finite non-negative zoom) rejects without
coercion; coordinate bounds cite ATLAS-COORD-001 (no geo dependency added).

## 2. Addressing doctrine (1.5-C)

Standard web-mercator slippy grid (the tile grid the observed schemes share).
Meridian identity (±180 explicit), mercator-bound rejection (no polar clamp),
[0,1] float hygiene, floor tile-zoom (PROVISIONAL standard). Address errors
surface as rejections, with polar cases classified unsupported (nothing to
address) rather than noMatch.

## 3. Matching doctrine (1.5-D/E)

Eligibility = kind membership + (tile kinds only) tileServing capability +
(tile kinds only) declared native-range containment. Undeclared ranges never
exclude. Geographic polygons stay deferred (zoom range is the only spatial
gate). Claimed coverage ≠ acquired/decoded/rendered — eligibility is declared
capability, never proven obtainability; no network verification exists.

## 4. Result doctrine (1.5-F)

Five statuses, each justified: resolved (sole eligible or explicit preference
applied), noMatch (kind served, none eligible, reason names the block),
unsupported (kind unserved, or addressing unsupported), invalidRequest (follows
the request rejection), ambiguous (several eligible, all listed, none chosen).
`Deferred` absent (nothing defers). Results carry request echo + provider id +
tile ADDRESS (coordinate/scheme — layer binding is downstream, so provider
identity stays separate by construction) + catalog-ordered eligible set +
deterministic reason. No bytes/paths/entries/objects.

## 5. Selection doctrine (1.5-G)

Sole-eligible resolves; explicit preference order applies against the eligible
set (unknown ids ignored); otherwise ambiguity stands. No relevance ranking,
no "best provider" invention.

## 6. Non-tile doctrine (1.5-H)

Tile kinds are exactly {rasterTiles, vectorTiles}; every other kind resolves
without tile addressing (tile null) and without tileServing requirements.
Elevation/boundary/parcel/historical/structure/local/geojson are first-class.

## 7. Boundary restatement (1.5-I/J/K)

Resolution performs no acquisition (no fetch interface exists), no caching
(key≠entry intact; no hits/TTL/paths), no rendering (no pixels/widgets/IDs).
`atlas_tiles`/`atlas_offline` remain empty. Dependency direction holds
(provider_api → core only). Zero production dependencies.

## 8. Verification snapshot (at closure)

- Runner: total=178, pass=138, fail=0, blocked=8, notApplicable=32 (two-run identical).
- 22 new fixtures (RSLV/ADDR/MATCH/SELECT/COVERAGE + ADV-038..041), zero dup IDs.
- `dart analyze`: clean. `dart format --check`: clean. Arch-leakage self-check
  green (no transport/renderer/storage markers in resolution sources).
- DEC-001..019 all open; no new DECs (floor-zoom and meridian-identity are
  documented sub-details owned by contract text, cf. ring-fraction precedent).
