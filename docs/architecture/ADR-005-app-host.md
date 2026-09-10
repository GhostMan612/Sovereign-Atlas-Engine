# ADR-005 — Atlas Application Host Architecture (App Track)

- **Status:** Accepted (app-track foundation; engine unaffected)
- **Date:** 2026-09-10
- **Deciders:** Operator directive (app-host track after GIS phases)
- **Scope:** Host/app boundary + first-host selection + adapter placement.
  No engine package changes; no renderer choice smuggled in.

## 1. Context

The engine is now buildable (workspace), executable (operations), retentive
(stores/packs), GIS-capable (measure/data/terrain/analysis/history/tactical/
location/plugins), and provider-fed (basemap + live). Nothing can open yet:
`apps/` holds placeholders. Blueprint 2.4/3.5 UX and Phase 13
productization need a host.

## 2. Decision

- **Layering (normative):** Flutter app(s) in `apps/` depend on engine
  contracts; engine packages NEVER depend on `apps/`, Flutter SDK widgets,
  or platform channels (dependency-map hard rule 5 extended to the SDK:
  `flutter/*` imports are banned in `packages/*/lib` — enforced by a
  2.0-P-style scan when the shell lands).
- **First host:** `apps/atlas` (Flutter; Android first, iOS second —
  Android-first because the source extractions are Android/field-originated;
  desktop/web explicitly deferred, not denied).
- **Renderer adapters** live in `apps/atlas/lib/adapters/` (initially ONE:
  chosen at scaffold time between flutter_map/MapLibre against the
  `atlas_map` renderer-abstraction contracts — the choice is adapter detail,
  never engine API).
- **Platform channels** (GPS, storage paths, permissions) live in
  `apps/atlas/` behind the `atlas_location`/`atlas_offline` abstractions
  (engine sees values, never channels).
- **First screens (ordered):** map view (basemap stack + camera) →
  coordinates/zoom readout → provider picker (2.4 casual) → offline areas
  (3.5) → advanced diagnostics (2.4 expert). Tactical UI stays behind Phase
  10 completion + its own host review.
- **Scaffold prerequisites (all must hold before `flutter create` lands):**
  engine `atlas_tool all` green (holds now); renderer-adapter decision
  recorded; Android SDK + acceptance device/emulator available to the
  scaffolding session (no unverifiable tree — the Phase 2/3 precedent).

## 3. Consequences

- App work proceeds WITHOUT engine changes until a host need demonstrates
  otherwise (then: contract → fixture → implementation, same discipline).
- Phase 13 productization (signing, release lanes, store metadata) starts
  only after the shell assembles and launches (verified, not declared).
- Secrets/key material: platform keystores via host-side channels ONLY;
  the secrets-ADR placeholder stays unapproved until then.
