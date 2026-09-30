# Phase 0.3 — Build Toolchain Spec (Evaluation, Nothing Installed)

> **DEAD REQUIREMENT — see section 2.** "Static analysis runs on every change" has
> no mechanism: no detekt, ktlint, spotless, or any other linter is configured in
> `apps/atlas-android/app/build.gradle`. A normative rule nothing can satisfy is
> unenforceable, and an agent told to run it will either shell out looking for a
> tool that does not exist or quietly claim it ran. Verified 2026-09-30.

- **Status:** Evaluation only. No tool installed, initialized, or configured.
- **Scope:** What each toolchain would own IF chosen; final selection is DEC-016 (workspace) and DEC-014 (language).

## 1. Toolchain responsibilities (PROPOSED matrix)

| Need | Dart/Flutter (`dart`/`flutter`, Melos, `analysis_options`, `dart test`, `dart format`) | Kotlin (Gradle, KMP) | Rust (cargo, rustfmt, clippy, cargo-test) | TypeScript (npm, eslint/prettier, jest/vitest) |
|---|---|---|---|---|
| Monorepo orchestration | Melos or workspaces (TBD) | Gradle composite builds | Cargo workspace | npm/turborepo |
| Formatting | `dart format` | ktfmt/spotless | rustfmt | prettier |
| Linting | `dart analyze` + custom import lint | detekt/ktlint | clippy | eslint |
| Unit/contract tests | `dart test` / `flutter test` | JUnit/kotest | cargo test + golden files | jest/vitest + snapshots |
| Golden fixtures | checked-in vectors + runner (Phase 0.4) — language-neutral format REQUIRED regardless of choice | same | same | same |
| Codegen | build_runner (iff needed; avoid unless justified) | KSP (iff needed) | build.rs (iff needed) | codegen (iff needed) |
| Docs | dartdoc | Dokka | rustdoc | typedoc |
| Publishing | pub.dev | Maven/Gradle plugins | crates.io | npm |
| Import-lint (dependency-map enforcement) | custom_lint rule (PLANNED) | Gradle module rules | crate boundaries | eslint boundaries |

## 2. Atlas toolchain requirements (ATLAS-NORMATIVE, tool-agnostic)

- Formatting is non-negotiable and machine-enforced (tool TBD).
- ~~Static analysis runs on every change~~ — **UNENFORCEABLE, no tool exists.**
  No linter is wired into the Gradle build, so there is nothing to run. The one
  enforced substitute is `atlas_purity_scan`, which checks the pure-boundary
  imports and does have a mechanism. Restoring a general linter needs an ADR
  (RULES 2.4), not an agent running a command that will fail.
- Golden-fixture runner is hermetic (no network) and float-tolerant (tolerances TBD in Phase 0.4).
- Codegen is guilty-until-proven-innocent: any generator needs an ADR (outputs must be reviewable, pinned, reproducible).
- Publishing/versioning mechanics follow DEC-019; nothing publishes in Phase 0.

## 3. Explicit non-actions

No SDK installs, no `pubspec.yaml`/`gradle`/`Cargo`/`package.json` files, no lockfiles, no CI workflows,
no formatter runs. Toolchain selection (DEC-016) and language selection (DEC-014) precede all of it.
