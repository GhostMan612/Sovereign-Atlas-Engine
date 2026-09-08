# Phase 1.2 — Layer Stabilization Report (Closure)

- **Status:** Closure record. Checkpoints 1.2-A..J executed in order; none skipped.
- **Predecessor:** Phase 1.1 CLOSED. Vehicle: Dart (provisional, DEC-014 open).

## Scope

`atlas_layers` semantic expansion only (+ runner + fixtures + phase-1 docs).
`atlas_core`, `atlas_geo`, `atlas_map` untouched. No other package touched.

## Files

- Added: `blueprints/phase-1/phase-1.2-layer-contract-inventory.md` (1.2-A),
  `blueprints/phase-1/phase-1.2-layer-stabilization-report.md` (this file, 1.2-J).
- Modified: `packages/atlas_layers/lib/src/definition/layer_definition.dart`
  (the package's single model file — one coherent unit, no piecemealing).
- Added fixtures (9): `layers/LAYER-001..007`,
  `adversarial/ADV-028/029`.
- Modified: `test/phase05_runner.dart` (new branches + enum parsers only).

## API changes

- Added: `AtlasLayerCategory` (5 values, optional field, PROVISIONAL),
  `AtlasLayerCapability` (4 advertised-only flags, default empty, PROVISIONAL),
  `datasetId` (optional opaque hook, PROVISIONAL), `AtlasLayerState.toggled/
  withOpacity/copyWith` (validated transitions, PROVISIONAL),
  `AtlasLayerStack.validate` (duplicate-id INVALID, PROVISIONAL).
- Fixed: `AtlasLayerDefinition.==`/hashCode drop `title` (ATLAS-NORMATIVE
  correction §11/32: presentation ≠ identity). Three equality levels documented.
- Unchanged: kind taxonomy (9), baseline ranks/order, attribution set rule,
  `conformsToBaseline` flag-not-veto (ADV-016 posture kept), opacity stays a
  PROVISIONAL state hint (never definition), no `enabled` dimension (§22),
  no dependency graph (§20/21 deferred — unrepresentable, so cycles impossible),
  single-string attribution (§23), no serialization (§31), no metadata bag (§10).

## Contracts affected

ATLAS-LAYER-001 (identity/taxonomy/metadata/capabilities/state),
ATLAS-MAP-ORDER-001 (ordering + ties + empty-valid), ATLAS-ATTR-001 (unchanged,
re-verified). No contract text rewritten; additions are PROVISIONAL with
1.2-A ownership (no new DECs — sub-details of the layer contract).

## Fixtures / blocked / provisional

9 new fixtures, all passing; twins for sharp edges (LAYER-006/ADV-028 family
coverage). Blocked set unchanged (8). New PROVISIONAL surface: category,
capabilities, datasetId, transitions, duplicate rule — all marked in code.
Baseline regression: all 48 pre-existing passes intact (ORDER/ATTR re-verified).

## Open decisions / gates

DEC-001..019 ALL STILL OPEN. `dart analyze`: no issues. `dart format --check`:
clean. Ban-list: no code-level hits. Dependency direction unchanged (layers →
core only). Two-run byte-identical. Final: total=131, pass=78, fail=0,
blocked=8, notApplicable=45. Tree clean, nothing pushed.

## Gate

PASS (all 40 Phase 1.2 gates hold).
