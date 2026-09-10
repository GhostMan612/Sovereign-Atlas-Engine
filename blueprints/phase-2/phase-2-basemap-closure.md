# Blueprint Phase 2 — Basemap Matrix, Engine Completion (PASS / CLOSED)

- **Status:** PASS / CLOSED 2026-09-10. Executed end-to-end in one controlled
  pass under BLUEPRINT-ALIGNMENT-001 (drift fix: our execution work recorded
  as inserted core work; canonical blueprint numbering resumed here).
- **Scope kept:** engine side only (2.1/2.2/2.3-engine). Selector UX (2.4)
  stays app-track; live-network tests refused (fake transport/injectable
  reader); MBTiles/sqlite refused (file-tree bundle instead, own ADR later);
  enforcement engines stay Phase 3.

## 1. Foundation (the solid-base work)

- **ADR-002** workspace harness: 12 minimal pubspecs (offline `pub get`
  verified), root `analysis_options.yaml` (inline strict rules — surfaced 6
  latent runner issues, all fixed, none weakened), `tools/atlas_tool.dart`
  (`analyze|format-check|test|all`), `.github/workflows/atlas.yaml`
  (marked UNEXECUTED-ON-CI, honestly). DEC-016 partially resolved
  (package: migration + Melos + CI-green stay OPEN).
- **ADR-003** `atlas_providers` package (core + provider_api + tiles edges):
  endpoint/policy/registry/operations/attribution. Transport = dart:io
  behind injected function (zero hosted dependencies).

## 2. Providers (first REAL executor bindings)

Seven definitions (declared PROVISIONAL ceilings, OSM bulk-guard + UA,
OTM explicit `{s}=a`, local bundle file-tree, requiresKey field with
truthful policyRejected refusal). Operations: template→fetch→carried result
(bytes discarded, payloadId = address); transport failure ⇒ `unavailable`;
serve/store throw documented Phase-3-seam UnsupportedError. Registry wiring
proven end-to-end (registry→binding→executor, BM-024); ceilings enforced
through real resolution matching (BM-013/014); attribution composes ordered
and deduped (BM-027; layers' visibility-SET type kept distinct by rename to
`AtlasProviderAttribution` — no duplication).

## 3. Verification snapshot (at closure)

- Runner: total=351, pass=311, fail=0, blocked=8, notApplicable=32 —
  two-run byte-identical. All 276 prior passes preserved.
- 35 new fixtures (BM-001..028, ADV-086..092), zero dup IDs across 349 files.
- `dart analyze` (whole workspace, strict set): clean.
  `tools/atlas_tool.dart format-check`: clean. Leakage self-check green.
- DEC-001..019 all OPEN (DEC-016 partial as noted); no new DECs beyond
  ADR-002/003 (zoom ceilings + key-plumbing absence owned by fixture text).

## 4. Blueprint checkboxes now true (engine side)

2.1 registration (registry, schemes, zooms, attribution/license/caching/
prefetch/auth declarations, version metadata via descriptors); 2.2 seven
providers (fetchable through operations, fake-transport proven); 2.3
stacking support (catalog-order eligibility, per-layer zoom gating,
visibility/opacity untouched in layers, attribution from active set —
renderer-rebuild and persistence ordering stay app-track with 2.4).
Exit: ≥6 types through registry ✓; add-without-editing-core-UI ✓ (registry
is data); stacking ✓ (semantics + catalog order); attribution from layers ✓
(provider-order display); failure isolation = executor + resolution
provenance (provider failure is a carried result, never a session fatality).
