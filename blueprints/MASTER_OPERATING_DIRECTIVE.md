================================================================================

SOVEREIGN ATLAS ENGINE - MASTER OPERATING DIRECTIVE

> **DEAD REFERENCE — DO NOT FOLLOW.** Retired 2026-09-30. Its section 4 "THE GREEN
> LINE" demanded `./gradlew assembleDebug` in every execution prompt. That
> contradicts RULES.md 1.6 (no builds without explicit ask) and names a task that
> does not exist in a flavored project. This file is NOT in RULES.md's supplement
> list (which names only ATLAS_ENGINE_MASTER_BLUEPRINT.md and docs/architecture/),
> so its precedence was UNDEFINED — that ambiguity is what let a build demand
> outlive its own ban. History only. What binds is AGENTS.md "The tool law" and
> RULES.md 1A / 3.

================================================================================

PROJECT VISION: A hybrid platform merging an encyclopedic offline historical atlas (land patents, blueprints, layers) with a lethal, decentralized Tactical C2 Node (mesh networking, cursor-on-target, line-of-sight).



REMOTE REPOSITORY (GROUND TRUTH): 

https://github.com/GhostMan612/Sovereign-Atlas-Engine



COMMANDMENTS FOR THE AI ARCHITECT:



1\. AUDIT BEFORE EXECUTION (NO HALLUCINATIONS)

Never assume the state of the codebase. Before drafting any execution prompt or writing new components, you MUST either:

&#x20; a) Search the remote GitHub repository to verify exact file paths, class signatures, and existing logic.

&#x20; b) Issue a "Task 0: Pre-Flight Audit / Grep" instruction to the local execution agent (OpenCode) to confirm the exact AST state before applying modifications.



2\. THE PURE-LOGIC BOUNDARY (THE IRON LAW)

The `core/` and `geo/` packages are sacred. They must contain ZERO Android framework, MapLibre, or java.net imports. All geographic math, domain models, and routing logic must remain pure Kotlin, 100% unit-testable, and entirely decoupled from the OS. All UI stays in `ui/`. All OS bindings stay in `android/`.



3\. SURGICAL AST INTERVENTIONS ONLY

Never issue a prompt that blindly overwrites a file unless it is a newly generated file. Use targeted, surgical AST edits. Do not destroy load-bearing legacy code without a verified integration path.



4\. THE GREEN LINE (TEST ENFORCEMENT) — SUPERSEDED, DO NOT APPLY

**Withdrawn 2026-09-30.** `./gradlew assembleDebug` contradicts RULES.md 1.6 and is
a task that does not exist here. It also produced a per-prompt build, which is the
per-edit verification loop this file is retired for. The gate is ONE
`:app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest` run per phase, via the
`atlas_gates` tool. If tests break, the sprint halts and reverts — that part stands.

Original (dead) text:

&#x20; - `./gradlew test` (To ensure the pure-logic boundary and DI graphs have not fractured).

&#x20; - `./gradlew assembleDebug` (To verify syntax and Android integrations).



CURRENT INITIATIVE: 

The Tactical Mesh \& Line of Sight pipeline is locked. The current vector is Phase 2: Offline Mapping \& Historical Encyclopedia (Intercepting .mbtiles via PackTileServer, SQLite rendering, and UI/UX modernization).

================================================================================

