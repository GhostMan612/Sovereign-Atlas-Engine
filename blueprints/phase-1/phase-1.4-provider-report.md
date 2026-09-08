# Phase 1.4 — Provider Contracts + Tile Model Report (Closure)

- **Status:** Closure record. Checkpoints executed in order; none skipped.
- **Predecessor:** Phase 1.3 CLOSED. Vehicle: Dart (provisional, DEC-014 open).
- **Boundary enforced:** Atlas defines provider/tile meaning; acquisition,
  transport, caching, packs, credentials, and rendering are all deferred
  (atlas_tiles/atlas_offline empty; no HTTP/network/GPS anywhere).

## Scope

`atlas_provider_api` production types only (+ runner + fixtures + phase-1 docs).
`atlas_core/geo/layers/map` untouched. Tile vocabulary lives in provider_api
requests//responses (identity/request meaning = contract concern); atlas_tiles
stays empty. No dependency-map change (provider_api → core only, verified).

## Files

- Added production (9): `provider/{data_kind,provider_descriptor}.dart`,
  `capabilities/provider_capability.dart`,
  `requests/{tile_coordinate,tile_identity,tile_request,tile_key}.dart`,
  `responses/tile_payload.dart` (entry + payload), barrel.
- Added fixtures (20): `providers/PVD-001..003`, `tiles/TILE-006..012 +
  ENTRY-001/002`, `adversarial/ADV-032..037`. Rewired: DESC-001..009,
  TILE-001..004 (previously NOT_APPLICABLE).
- Modified: `test/phase05_runner.dart` (providers/tiles dispatch, enum parsers,
  grep arch-check).

## APIs added (all PROVISIONAL except noted)

`AtlasDataKind` (9), `AtlasProviderCapability` (4, advertised-only),
`AtlasProviderDescriptor` (+ structural validate), `AtlasTileCoordinate`
(+ bounds validate, ATLAS-NORMATIVE), `AtlasTileScheme` + TMS flip
(ATLAS-NORMATIVE), `AtlasTileIdentity` (ATLAS-NORMATIVE),
`AtlasTileRequest.resolveUrl` (pure substitution; unknown→MALFORMED_TEMPLATE,
ATLAS-NORMATIVE mechanics), `AtlasTileKey` (SOURCE-VERIFIED shape + round-trip),
`AtlasTilePayload`/`AtlasTileEntry` (+ consistency rule, PROVISIONAL).
Deliberately absent: fetch interface, clients, credentials, coverage polygons
(needs geo-dep ADR — documented), registry/discovery, HTTP.

## Non-tile proof

PROV-002 (elevation descriptor, no tile fields) validates: sources are not
forced through tile-fetching. Subdomain rotation policy refused (TILE-012:
explicit params or throw).

## Fixtures / blocked / provisional

155 files, zero dup IDs (PVD- prefix avoids PROV- collisions with provenance
fixtures). TILE-005 stays NOT_APPLICABLE (discovery PLANNED; defect
structurally impossible). Blocked set unchanged (8). New PROVISIONAL surface:
taxonomy/capabilities/descriptor shapes, z≤32 bound, entry consistency,
empty-kinds/zoom sanity rules — all marked in code.

## Open decisions / gates

DEC-001..019 ALL STILL OPEN (no closure, no new DECs). `dart analyze`: no
issues. `dart format --check`: clean. Ban-list (transport/endpoint/credential):
clean incl. PVD-003 grep check. Dependencies: zero production deps; direction
holds. Two-run byte-identical. Final: total=156, pass=116, fail=0, blocked=8,
notApplicable=32. Tree clean, nothing pushed.

## Gate

PASS.
