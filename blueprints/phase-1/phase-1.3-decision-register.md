# Phase 1.3 — Decision Register (Scoped; No New DECs)

All items below are DEFERRED/BLOCKED/PROVISIONAL with ownership in existing
DECs or PROPOSED contract text. Nothing here closes DEC-001..019 and no new
DEC entries are created (no new architectural questions arose; all findings
land inside already-open items per the 0.5A no-parallel-system ruling).

| Item | Posture | Owner |
|---|---|---|
| Camera antimeridian wrap policy | DEFERRED (representational practice only: centers always validated positions; no wrap logic anywhere) | DEC-004/005 edge |
| Pitch normative 3D meaning | DEFERRED (guarded scalar retained; no renderer-shaped model invented) | Contract text (pending terrain phase) |
| Camera serde versioning | DEFERRED gap (wire unversioned as observed) | Contract text |
| Persistence meaning | DEFERRED (no storage of any kind; meaning itself undefined) | No contract (correctly absent) |
| Bearing range in camera | Preserved as-is: any-finite (observed absence, DEC-008 open) | DEC-008 |
| Zoom bounds [0,24] status | SOURCE-VERIFIED data carried as model shape (not Atlas law) | F-03 evidence; DEC-008 |
| `copyWith` transitions | Added as minimal 1.3-H pure recomposition (contract-authorized) | ATLAS-CORE-CAM-001 area |
| Coincident bearing | Unchanged: provisional throw, BRG-004 BLOCKED | BRG-004 |
| ADV-016 | Unchanged: flag-not-veto, BLOCKED | ATLAS-MAP-ORDER-001 clarification |

## Verification snapshot (at closure)

- Runner: total=138, pass=85, fail=0, blocked=8, notApplicable=45 (two-run identical).
- `dart analyze`: clean. `dart format --check`: clean. Ban-list: clean.
- Production diff vs 1.2-close: `camera_state.dart` + `map_state.dart` (copyWith only).
  `atlas_core`, `atlas_geo`, `atlas_layers`: untouched.
