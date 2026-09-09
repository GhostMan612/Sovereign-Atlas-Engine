# Phase 1.6-A — Resource Contract Inventory (Expansion Scoping)

- **Status:** Inventory only. The gap between `AtlasResolutionResult` (1.5) and
  acquisition is: a named, referenceable, equality-bearing RESOURCE object plus
  the smallest honest materialization step. Everything past that is deferred.
- **Consumed:** ATLAS-PROV-DESC-001 (descriptors), ATLAS-TILE-ID-001 (identity/
  key/scheme), 1.5 resolution request/result/addressing/matching.

## Minimum contract (new, classified)

| Element | Class | Ownership / notes |
|---|---|---|
| `AtlasResourceIdentity{provider, kind, address}` (opaque address string; canonical tile form documented, never a URL) | PROPOSED → PROVISIONAL | Contract-owned (1.6-C identity chain) |
| `AtlasResolvedResource{identity, provider, kind, tile?, scheme?, attribution?, license?}` + `fromResolution(result, provider)` (throws unless resolved) | PROPOSED → PROVISIONAL | 1.6-B/H (tile ref optional; non-tile first-class) |
| Address canonical tile form `z=<z>/x=<x>/y=<y>@<scheme>` (documented rendering of the tile address, not a locator protocol) | PROPOSED → PROVISIONAL | Owned by resource-identity text (no DEC; sub-detail precedent) |
| `AtlasMaterialization{resource, status, representation?}` + `AtlasMaterializer.materialize(resource, {template, params})` | PROPOSED → PROVISIONAL (the justified smaller equivalent of a lifecycle) | 1.6-E (boundary marker with executable content, not theater) |
| Statuses ready/deferred/invalid only (`Available/Unavailable/Failed` and acquisition-failure NOT implemented — unjustified here) | Implemented subset | 1.6-F |
| No `ResourceRepresentation` enum (kind + optional tile IS the representation story; enum would duplicate kind) | Rejected as redundant | 1.6-T smaller-equivalent ruling |
| No serialization, no lifecycle machine, no equality beyond identity struct | Deferred/absent | 1.6-N/P/O (defer-not-invent) |

## Statuses NOT implemented (unjustified)

`Available`, `Unavailable`, acquisition-`Failed`, retry/progress/health, ranking,
coverage polygons, subdomain rotation, QUADKEY, registry/discovery.

## Explicit non-goals (1.6-X refusal list honored)

HTTP/sockets/downloads/retry/health, filesystem/cache/offline, credentials/auth,
decoding (raster/vector/DEM/image, compression, MIME, byte validation),
renderer/MapLibre/flutter/WebGL/pixels, platform APIs. `atlas_tiles` stays empty.
