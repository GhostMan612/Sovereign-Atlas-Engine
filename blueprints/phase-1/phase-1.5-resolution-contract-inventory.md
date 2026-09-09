# Phase 1.5-A — Resolution Contract Inventory (Expansion Scoping)

- **Status:** Inventory only. Resolution answers "what resource satisfies this
  request" — never acquisition, caching, or rendering (hard boundary 1.5-I/J/K).
- **Consumed contracts:** ATLAS-PROV-DESC-001 (descriptors/kinds/capabilities/
  native range), ATLAS-TILE-ID-001 (identity/ Hansen-shape/schemes),
  ATLAS-TILE-RET-001 area (request semantics), ATLAS-GEO-DIST/BRG area (no new
  geodesy), ATLAS-CORE-CAM-001 (zoom is a semantic float; tile-zoom derivation
  is new here).

## Minimum resolution contract (new, classified)

| Element | Class | Ownership / notes |
|---|---|---|
| `AtlasResolutionRequest{kind, location, zoom, scheme, preferredProviders}` | PROPOSED → PROVISIONAL | Contract-owned (1.5-B dimensions, nothing more) |
| Web-mercator slippy addressing (lon→x, mercator→y, floor) | ATLAS-NORMATIVE definitional standard (the tile grid OSM/Esri/OTM share; forensically observed as z/x/y schemes) | ATLAS-TILE-ID-001 area |
| lon ≡ −180/180 meridian identity for addressing | ATLAS-NORMATIVE math identity (same meridian), explicit named handling | Not DEC-004 policy (validation still rejects) |
| Mercator latitude clamp [0,1] float hygiene | ATLAS-NORMATIVE hygiene (prevents float dust; policy-neutral) | Verified by maxlat-z5 → y=0 case |
| Polar latitudes beyond ±85.05112878 | Explicit rejection (no silent clamp) | DEC-005 edge; category OUT_OF_RANGE |
| Fractional zoom → floor tile-zoom | PROPOSED → PROVISIONAL (web-map standard; overzoom is renderer business) | Contract-owned sub-detail (no new DEC, cf. ring-fraction precedent) |
| Capability matching (kind ∈ kinds; tile kinds additionally require tileServing; native range gates tile resolution) | PROPOSED → PROVISIONAL rules | ATLAS-PROV-DESC-001 area |
| Geographic coverage filtering | DEFERRED (descriptors carry no polygons; zoom-range is the only spatial gate) | Needs geo-dep ADR (1.4 ruling stands) |
| Result statuses resolved/noMatch/unsupported/invalidRequest/ambiguous | Implemented set (Deferred dropped as unjustified) | 1.5-F |
| Selection = explicit preference order, else single-or-ambiguous | No "best provider" invention (1.5-G) | Caller decides; engine applies |
| Non-tile resolution (provider + kind, tile null) | ATLAS-NORMATIVE shape (1.5-H test) | No tile-forcing |
| `AtlasTileCoordinate` reuse for addresses | Existing type (no duplicate addressing model) | ATLAS-TILE-ID-001 |

## Statuses NOT implemented (unjustified)

`Deferred` (nothing defers inside resolution), retry/progress/health/latency/
auth/renderer-compat signals (later layers), relevance ranking, coverage
polygons, subdomain rotation, TMS-beyond-flip schemes.

## Explicit non-goals (no code, no fixtures beyond shape asserts)

HTTP/sockets/downloads/auth/credentials/retry/status-handling, cache
storage/TTL/eviction/paths/entries-as-instructions, offline packs, tile
decoding, renderer calls/IDs/pixels, GPS, DEM/viewshed/RF, MGRS, registry/
discovery, QUADKEY, subdomain rotation policy.
