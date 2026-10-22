# MASTER PLAN — Sovereign Atlas Production & SDK

## Product charter
Navigation-first offline sovereign atlas with optional Explorer, Tactical and Studio workspaces. Premium restrained graphite/chrome interface; compass tape and heading-target bearings; user-configurable composable basemap + overlays. Knowledge layers with temporal validity, citations, uncertainty and licensed data. Reusable Android SDK with pure domain contracts and isolated MapLibre adapter.

## Nonnegotiable design decisions
- **Current repo is native-only.** Do not resurrect Flutter or claim Dart tests are executable. Historical goldens may be selectively ported with evidence.
- Core/geo/camera/layers/field/measure/goto/track/offline-policy/tactical logic cannot import Android, Compose or MapLibre. Existing offline socket I/O is an ADR candidate, not silently reclassified.
- Map never requires network to initialize; missing basemap data is visible as missing coverage, not silently substituted. GPS never defaults to 0,0.
- Separate basemap choice from composable overlays; per-layer opacity, visibility, temporal range, min/max zoom, source/license, attribution and z-order; deterministic persistence.
- Map zoom beyond source-native detail must be labeled overscaling; high-res imagery/terrain requires licensed real data. Avoid false precision.
- Historical claims require entity ID, geometry, valid-from/to, event date where applicable, source citation, acquisition date, license, confidence and contradictory-source support. Unknown stays unknown.
- Navigation distinguishes straight-line Go-To from road routing. Offline route graph acquisition, turn restrictions, guidance, reroute, accessibility, transit and voice each require explicit proof; never advertise unimplemented features.
- SDK is local-first, stable versioned API, independent of app UI; prove from an independent sample consumer. Commercial third-party source terms and redistributability reviewed.
- Tactical work is optional and non-invasive: coordinate formats, tracks, field overlays, range/radio tools, terrain analysis when DEM exists; no unsupported precision claims.
- Security: no embedded secrets, private location retention opt-in, export/delete controls, encrypted private data when appropriate, dependency/license SBOM, no telemetry by default.

## Four phases / four consolidated work orders
| Phase | Work order | Deliverables | Hard exit |
|---|---|---|---|
| P1 | WO-01 Converge & harden | repo capability inventory, conflict/ADR ledger, offline boundary separation, flavor gates, golden inventory, SDK API design, performance baseline | existing features regress zero; ADRs resolved; no undocumented API changes |
| P2 | WO-02 Product engine | routable navigation + voice guidance, composable layers, offline catalog, temporal historical dossier/data, field/tactical toolchain, dataset provenance | deterministic tests + offline airplane-mode functional acceptance + real licensed fixtures |
| P3 | WO-03 Experience & SDK | navigation HUD, compass tape, workspace toggles, chrome design system, Blender asset pipeline, accessibility/localization, standalone SDK consumer, API docs | usability/accessibility/device matrix + consumer builds independently |
| P4 | WO-04 Certification & release | performance/security/privacy/ToS, failure injection, CI, docs, versioned artifacts, release signing, rollback, acceptance | release checklist fully evidenced; explicit operator release approval |

## Cross-cutting acceptance checklist
- [ ] No code changes before baseline inventory and ADR review
- [ ] Every capability has spec → source → test → device/fixture evidence → decision → acceptance chain
- [ ] Both flavor unit gates executed once per phase, counts recorded from XML
- [ ] Pure boundary scan passes; I/O boundary resolved by approved ADR
- [ ] Offline launch and route/tiles verified with radio disabled and local datasets
- [ ] Provider rate limits, terms, licensing and attribution validated individually
- [ ] UI readable on Moto G 2025 and BLU View 5 AVDs; performance judged on physical hardware
- [ ] SDK sample imports published artifact without private source-tree assumptions
- [ ] No builds, device operations, commits, pushes, signing, external installs without their separate authorizations
- [ ] Every deferred item named with owner, evidence gap, severity and release disposition

## No artificial completion
If any mandatory release gate remains blocked, output RELEASE BLOCKED with exact evidence and smallest next work. Never silently waive gates, invent test counts or imply one agent run guarantees full production readiness.
