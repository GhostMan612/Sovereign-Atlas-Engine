# Blueprint Alignment Record (Normative, forward-looking)

- **Status:** Accepted 2026-09-10 (operator-authorized realignment).
  The frozen master blueprint (`blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md`
  v1.0 Draft) is NOT edited by this record; this file maps actual progress
  onto it and restores canonical numbering going forward. Git history is not
  rewritten (phases stay as committed; their blueprint position is clarified
  here).

## 1. Drift map (blueprint → actual)

| Blueprint phase | Blueprint content | Actual coverage | Verdict |
|---|---|---|---|
| 0 Foundation/extraction/architecture | scaffold, ADRs, inventories | Phase 0–0.5A as committed | DONE |
| 1.1 Core domain objects | 18 model types | geo/layers/map-state/provider descriptors (Phases 1.0–1.4) | DONE in substance; gaps → §3 |
| 1.2 Map state machine | 7 session states | Phase 1.3 map-state (camera/session contracts) | DONE; session-state hardening continues as needed |
| 1.3 Event/command model (UI) | SetCamera/ToggleLayer/… events | NOT built (our 2.0-D is execution commands — a different thing) | GAP → app track |
| 1.4 Configuration | defaults/policies/flags | NOT centralized | GAP → §3 (provider policy data lands in Phase 2; app config in app track) |
| 2.1 Provider registration | registry + 4 provider types | descriptors/capabilities only (1.4); NO registry, NO implementations | GAP → Phase 2 (this record authorizes completion) |
| 2.2 Basemap implementations | OSM/Esri/OTM/USGS/local | NONE | GAP → Phase 2 |
| 2.3 Layer stacking | z-order/opacity/groups/persist | composition semantics (1.2); registry wiring + attribution missing | PARTIAL → Phase 2 completes engine side |
| 2.4 Selector UX | casual/advanced/expert UI | needs app host | DEFERRED → app track |
| — (not in blueprint) | resolution/resource/cache/acquisition/orchestration semantics (our 1.5–1.9) | committed | INSERTED core work; sits between blueprint 1.1 and 2.1 logically |
| — (not in blueprint) | execution substrate (our 2.0) | committed in `atlas_tiles/src/execution/` | INSERTED core work; prerequisite for 2.2/3.x engines |

## 2. Realignment ruling

- Our committed "Phase 2.0 (execution infrastructure)" is redesignated
  **inserted core-engine work** (conceptually Phase 1.10). Its docs/commits
  keep their names; its blueprint position is defined HERE, not by rename.
- Canonical numbering resumes NOW: the next phase is **Blueprint Phase 2 —
  Basemap Matrix (engine completion)**, scoped in §3. No further inserted
  numbers without a record of this kind.
- Blueprint Phase 1.3 (UI events/commands) and 1.4-app-config are reassigned
  to the **app track** (they require the host ADR-002/3 unlocks); engine
  keeps execution commands (2.0-D) which are a different, already-closed
  concern.

## 3. Phase 2 engine-completion scope (authorized by this record)

IN: workspace harness (ADR-002) → provider implementations package (ADR-003)
→ registry + 7 provider definitions (descriptor + endpoint + policy, 2.1/2.2)
→ operations as executor bindings (first REAL binding) → stacking-through-
registry + attribution composition (2.3 engine side) → fixtures/runner/
verification/closure. OUT (with reasons): selector UX (needs app host);
live-network tests (fake transport; determinism law); MBTiles/sqlite (needs
its own dependency ADR — local file-bundle instead); retry/scheduler/prefetch
engines (Phase 3); any new package beyond ADR-003.
