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

## 3. Consequences (as amended)

- Workspace packaging/analysis/tooling are LANDED (see §4 for the
  package: migration that superseded the relative-import line).
- `dart pub get --offline` works at root and per package (no hosted deps).
- DEC-016: Melos adoption + first green CI run stay OPEN; everything else
  in DEC-016 is resolved (no silent closure).

## 4. Amendment 2026-09-10 (package: migration executed)

Relative cross-package imports proved INCOMPATIBLE with `package_config.json`:
once `pub get` runs, the analyzer resolves the importing file inside its
package and relative URIs escaping the package fail (`uri_does_not_exist`,
found empirically). The "relative imports kept" line above is therefore
superseded:

- All manifests carry `publish_to: none` + path dependencies for edges
  actually used (depscan-verified, narrower than the approved map).
- All cross-package imports migrated to `package:` URIs (57 converted,
  scripted + analyzer-verified); `test/` + `tools/` resolve via the new root
  `sovereign_atlas_workspace` package (path deps, offline `pub get`
  verified). Library `pubspec.lock` files stay uncommitted (gitignored).
- DEC-016 now: Melos adoption + first green CI run stay OPEN; everything
  else in DEC-016 is resolved.

- The pre-amendment notes below are superseded where they conflict:

- DEC-016 is PARTIALLY resolved (packaging/analysis/tooling land); the
`package:`-import migration + Melos/CI-verification stay OPEN as
follow-ups (no silent closure).
- `dart pub get` per package works offline (no hosted deps). Until run, IDE
  resolution may warn — documented, harmless (analyzer CLI is normative).
