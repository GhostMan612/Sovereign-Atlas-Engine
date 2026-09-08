# Phase 0.4 — Extraction Matrix

Maps each `capability-inventory.md` entry to its Atlas disposition.
See ADR-001 for package responsibilities.

## Disposition definitions

| Disposition | Meaning | Test expectation |
|---|---|---|
| `KEEP` | Design already satisfies Atlas principles. Preserve behavior and architecture; rehost behind contracts. | Regression test pins current behavior before any move. |
| `EXTRACT` | Portable domain logic belongs in Atlas packages. Move it, generalize app-specific bits into adapters. | Golden/unit tests on the pure logic; no renderer/UI imports. |
| `REBUILD` | Concept is valid but the implementation is platform-bound (Compose/MapLibre, Android APIs, flutter_map widgets). Reimplement against `atlas_map` / provider / location contracts. | Contract tests + adapter tests; core stays renderer-free. |
| `REJECT / REPLACE` | Correct for the original app, wrong for a general engine (app workflows, single-vendor wiring, host transports). Stays in `integrations/` or is replaced by a provider/plugin slot. | No core code; adapter or deferred-phase slot only. |

## Matrix (condensed; authority is the inventory)

| IDs | Disposition | Destination | Notes |
|---|---|---|---|
| CAP-003 (offline boot concept), CAP-006 (deep-vector concept), CAP-019 scope boundary (smooth-earth only on-device), CAP-R06 behavior | KEEP | `atlas_offline`, `atlas_layers`, `atlas_tactical` contracts | Preserve the fail-secure invariant: no network → Atlas still starts. |
| CAP-002, CAP-004 (model), CAP-005, CAP-007, CAP-008, CAP-009, CAP-010, CAP-011, CAP-012 (model), CAP-013, CAP-014 (model), CAP-016, CAP-017, CAP-018, CAP-020, CAP-022, CAP-023, CAP-024, CAP-025, CAP-026, CAP-027 (model), CAP-R02 (model), CAP-R03, CAP-R04-location, CAP-R07 | EXTRACT | `atlas_core`, `atlas_geo`, `atlas_layers`, `atlas_tiles`, `atlas_offline`, `atlas_data`, `atlas_tactical`, `atlas_location`, `atlas_provider_api` per inventory | Pure helpers first per blueprint §0.2: haversine, bearing, rings, camera serde, pin codec, GeoJSON/parcel/H3/blueprint/migration conversions, measurement transitions, validation. |
| CAP-001, CAP-003 (impl), CAP-004 (wiring), CAP-006 impl details, CAP-012 (transport), CAP-014 (impl), CAP-015, CAP-021 transport shape, CAP-R01, CAP-R06 (impl) | REBUILD | `atlas_map` renderer adapters, `atlas_location` services, `integrations/*` transports | Renderer migration must not rewrite the geo engine. |
| CAP-021 core residency, CAP-R04-meetings, CAP-R05 (Phase 0), app workflows, Hub/mesh specifics | REJECT from core / REPLACE with slot | `integrations/*` adapters; live slots deferred to Phase 6 | A feature does not enter the core merely because it appears on the map. |

## Extraction order (smallest coherent units)

1. Pure geospatial math with no I/O (`atlas_geo`): distance, bearing, destination, bbox, validation.
2. State serde with no renderer (`atlas_core` / `atlas_map` camera, pin codec, measurement transitions).
3. Conversion models with no SDK (`atlas_data` GeoJSON/parcel/H3/blueprint shapes).
4. Policy models with no network (`atlas_provider_api` descriptors, `atlas_tiles` key/policy, `atlas_offline` manifest).
5. Contracts (`atlas_map` renderer abstraction, `atlas_location` services).
6. Adapters last (`integrations/*`, renderer implementations).

## Forbidden moves

- No extraction may hard-code a single provider's URLs, keys, or SDK types into `packages/`.
- No extraction may move Mantle mesh/Hub or Recovery meeting semantics into `packages/`.
- No extraction may drop surveyed-vs-derived authority (CAP-007 > CAP-008), provenance, or attribution.
