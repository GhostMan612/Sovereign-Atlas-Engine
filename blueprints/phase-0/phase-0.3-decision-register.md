# Phase 0.3 — Decision Register (DEC-014+; DEC-001..013 in contract-decision-register.md)

New decisions continue numbering. All below are DECISION REQUIRED; nothing is resolved here.

## DEC-014 — Core language ownership

- **Question:** What language owns the platform-neutral core (`atlas_core`, `atlas_geo`, and later core-adjacent contracts)?
- **Evidence:** Portable logic isolated in both source families (Phase 0.1/0.2); adapter languages observed (Kotlin SRC-A, Dart SRC-B/C) with no core implication.
- **Choices:** (a) Dart core; (b) Kotlin core (possibly KMP); (c) Rust core + bindings; (d) TypeScript core; (e) polyglot neutral-core + adapters. See `language-evaluation.md` §3.
- **Impact:** Workspace, hiring/ramp, GIS/3D ceiling, distribution, fixture-harness language.
- **Status:** DECISION REQUIRED — OPEN. Provisional implementation vehicle for the
  Phase 0.5 slice only: dependency-free pure Dart (see
  `blueprints/phase-0/phase-0.5A-reconciliation.md` Ruling 1). This vehicle
  MUST NOT be read as the core-language decision.

## DEC-015 — Renderer abstraction and per-surface renderers

- **Question:** What renderer abstraction does `atlas_map` expose, and which concrete renderer serves each surface (Atlas-app, Mantle, Recovery, web)?
- **Evidence:** MapLibre-native (SRC-A) + flutter_map (SRC-B) both observed; abstraction requirements in `renderer-evaluation.md` §3.
- **Choices:** (a) single abstraction, multiple adapters (preferred direction); (b) per-surface abstractions (rejected unless justified).
- **Impact:** `atlas_map` contract text, adapter scope, DevTools diagnostics.
- **Status:** DECISION REQUIRED.

## DEC-016 — Workspace / build system ownership

- **Question:** What workspace/build system owns the monorepo (Melos, Gradle composite, Cargo workspace, npm/turbo, other)?
- **Evidence:** `lib/src/` layout + `.gitignore` hints Dart/Flutter; no manifests exist (SOURCE-VERIFIED). Hint is not a decision.
- **Choices:** Follows DEC-014; polyglot (e) needs a meta-orchestrator + per-language workspaces.
- **Impact:** Package boundaries enforcement, CI shape, publishing.
- **Status:** DECISION REQUIRED.

## DEC-017 — Test harness language(s) and golden-runner placement

- **Question:** Where do Phase 0.4 golden fixtures run if the core language is undecided?
- **Evidence:** None (fixtures don't exist yet).
- **Choices:** (a) language-neutral vectors (JSON/text) + per-language runners; (b) single-harness-first with port obligation.
- **Impact:** Phase 0.4 scope; prevents fixtures being invalidated by DEC-014.
- **Status:** DECISION REQUIRED (recommendation in file: (a)).

## DEC-018 — Native interop / FFI posture

- **Question:** How do adapters consume a non-native core (FFI, platform channels, services, Wasm)?
- **Evidence:** Pinned-HTTP + platform-service patterns observed; no FFI observed.
- **Choices:** TBD per DEC-014 outcome; Rust-core option forces this earliest.
- **Impact:** Adapter complexity, perf, backgrounding, offline.
- **Status:** DECISION REQUIRED.

## DEC-019 — Versioning, publishing, and changelog mechanics

- **Question:** Independent vs lockstep package versioning? Which registries? Changelog enforcement?
- **Evidence:** None (no manifests, no tags beyond docs commits).
- **Choices:** Independent semver + ADR-gated breaking changes (recommended direction in `workspace-architecture.md` §6) vs alternatives.
- **Impact:** SDK distribution, integration upgrades, release hardening (Phase 13).
- **Status:** DECISION REQUIRED.

## Register rules

- Resolve only with evidence or ADR. Cross-reference DEC-001..013 where overlapping
  (e.g. DEC-006 error channel interacts with DEC-014/017; DEC-009/010/011 constrain DEC-016/019).
