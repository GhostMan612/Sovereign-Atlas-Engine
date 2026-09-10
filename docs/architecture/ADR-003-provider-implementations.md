# ADR-003 — Provider Implementations Package (`atlas_providers`)

- **Status:** Accepted (Blueprint Phase 2 engine completion)
- **Date:** 2026-09-10
- **Deciders:** Operator directive (blueprint realignment) + execution agent
- **Scope:** One new package; endpoint/policy/registry/operation types.

## 1. Context

ADR-001 requires provider IMPLEMENTATIONS to live outside
`atlas_provider_api` (contracts-only), in "dedicated provider packages,
integrations/, or later SDK plugins". Blueprint 2.1/2.2 need a registry and
seven working provider definitions. Descriptors are endpoint-free by
normative rule (1.4), so implementations need their own endpoint type.

## 2. Decision

- New package `atlas_providers` (lib/src layout, same conventions):
  `AtlasProviderEndpoint{descriptor, urlTemplate, scheme, params, headers,
  policy}` (implementation-side; descriptors untouched) +
  `AtlasProviderPolicy{onlineAllowed, cacheAllowed, prefetchAllowed,
  maxTiles?, maxRequestsPerSecond?, requiresKey, bulkGuard?, userAgent?}`
  (blueprint 2.1/3.4 declarations as data; enforcement is Phase 3) +
  `AtlasProviderRegistry` (explicit register/exact-lookup/providersFor) +
  `AtlasTileFetchOperation` (executor operation: template→fetch→carried
  result; bytes discarded after fetch, payloadId = resource address) +
  `AtlasLocalBundleOperation` (file-tree `{z}/{x}/{y}.{ext}` bundles, no
  sqlite) + `AtlasAttribution.compose(providerIds, registry)` (deduped `; `
  join).
- Allowed dependencies: `atlas_core`, `atlas_provider_api`, `atlas_tiles`
  (operation interface). Transport via `dart:io` HttpClient behind an
  INJECTED `AtlasTransport` function type (tests inject fakes; production
  wires HttpClient; no hosted `http` dependency — zero network surface in
  manifests).
- serveEntry/storeHandoff on provider operations throw `UnsupportedError`
  with a Phase-3-store message (documented seam, fixture-proven): serving
  without a store would conflate cache/acquisition (2.0-I/J guard). Phase 3
  replaces these with store-backed operation COMPOSITION; bindings stay.
- Seven definitions: `osm-standard` (0–19, bulk guard + UA per OSM Tile Usage
  Policy), `esri-imagery` (0–19), `esri-light-gray` (0–16),
  `esri-dark-gray` (0–16), `opentopomap` (0–17, `{s}` a/b/c via explicit
  params — no rotation invented), `usgs-topo` (0–16), `local-bundle`
  (file-tree, no URL). Zoom ceilings are DECLARED source claims
  (PROVISIONAL; correct with source, don’t silently "fix").

## 3. Consequences

- Blueprint 2.1 (registration) + 2.2 (implementations) complete engine-side;
  2.3 completes via registry wiring + attribution; 2.4 (UX) stays app-track.
- Foreseen evolution (not scope): store-backed operation composition,
  prefetch/rate-limit engines, MBTiles/sqlite (own dependency ADR).
