# ADR-002 — Workspace Harness (Minimal, Offline-Safe)

- **Status:** Accepted (Phase 2 foundation prerequisite)
- **Date:** 2026-09-10
- **Deciders:** Operator directive (blueprint realignment) + execution agent
- **Scope:** Packaging/analysis/tooling only. No production semantics.

## 1. Context

Eleven implementation packages plus one new provider package (ADR-003) exist
as directory trees with relative imports and no manifests (DEC-016 TBD).
Without packaging there is no language-version pinning, no `package:`
identity, no shared lint set, no one-command verification, and no CI — every
downstream track (providers, offline, app) is blocked on this.

## 2. Decision

- Each of the 12 implementation packages gets a minimal `pubspec.yaml`:
  `name`, `version: 0.1.0`, `environment: sdk '>=3.0.0 <4.0.0'`, NO
  dependencies. Rationale: pins the language version and establishes package
  identity with zero network surface (fully offline-safe). Relative imports
  are KEPT (the `package:` migration is a later, explicitly authorized step —
  not smuggled in here).
- Reserved packages (`atlas_analysis/terrain/history/plugins`) get NO
  pubspec (still placeholders; ADR-001 hard rule 6 stands).
- Root `analysis_options.yaml` with INLINE rules only (no `include:` on
  `package:lints` — that would need a network fetch). Strict subset:
  `unused_import`, `unnecessary_null_comparison`, `prefer_final_fields`,
  `omit_local_variable_types` off-style neutrality — keep close to existing
  code style (final-heavy, explicit types).
- `tools/atlas_tool.dart` (stdlib only): `analyze`, `format-check`, `test`,
  `all` across packages + runner. One command replaces tribal knowledge.
- `.github/workflows/atlas.yaml`: analyze + format-check + runner on push/PR.
  Marked UNEXECUTED-ON-CI in this ADR (cannot run Actions from here; file
  presence claims nothing about green CI).

## 3. Consequences

- DEC-016 is PARTIALLY resolved (packaging/analysis/tooling land); the
`package:`-import migration + Melos/CI-verification stay OPEN as
follow-ups (no silent closure).
- `dart pub get` per package works offline (no hosted deps). Until run, IDE
  resolution may warn — documented, harmless (analyzer CLI is normative).
