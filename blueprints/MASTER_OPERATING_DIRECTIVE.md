================================================================================

SOVEREIGN ATLAS ENGINE - MASTER OPERATING DIRECTIVE

================================================================================

> ## ⛔ SUPERSEDED — DO NOT FOLLOW. Retained as historical record only.
>
> **Superseded 2026-09-30.** This file is a pre-Kotlin-port directive. It is
> **not** in `RULES.md`'s supplement list and **not** in `AGENTS.md`'s cold-start
> order, so its precedence was undefined — and it mandated a build in *every
> prompt*:
>
> - §4 "THE GREEN LINE" demanded `./gradlew assembleDebug` in **every** execution
>   prompt. That directly contradicts `RULES.md` §1.6 BUILD BOUNDARY — a HARD RULE
>   that forbids builds without explicit ask — and it is precisely the mid-plan
>   shell traffic `RULES.md` §3 ("No intermediate shells") exists to prevent.
> - §1 "AUDIT BEFORE EXECUTION" demanded a fresh remote/pre-flight grep before
>   writing *any* component. That is a per-task verification loop, also
>   contradictory to `AGENTS.md`'s "do the whole task, then gate once".
> - §4's `./gradlew test` is also the wrong task name: the app has product
>   flavors (`play` / `enterprise`), so AGP generates
>   `:app:testPlayDebugUnitTest` / `:app:testEnterpriseDebugUnitTest`. There is
>   no `testDebugUnitTest`. This is the pattern `gate-runner.md` warns about —
>   a task-not-found error that masquerades as a broken build.
> - "CURRENT INITIATIVE: Phase 2" is stale; `README.md` records that Phase 13
>   (productization/release) was never executed.
>
> **Canonical law is `RULES.md`.** Where this file disagrees, `RULES.md` wins and
> this file is simply wrong. If you are reading this and about to run a build
> because §4 told you to — stop. The human builds in Android Studio.

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



4\. THE GREEN LINE (TEST ENFORCEMENT)

Every execution prompt must conclude with a Verification Task demanding:

&#x20; - `./gradlew test` (To ensure the pure-logic boundary and DI graphs have not fractured).

&#x20; - `./gradlew assembleDebug` (To verify syntax and Android integrations).

If tests break, the sprint halts and reverts.



CURRENT INITIATIVE: 

The Tactical Mesh \& Line of Sight pipeline is locked. The current vector is Phase 2: Offline Mapping \& Historical Encyclopedia (Intercepting .mbtiles via PackTileServer, SQLite rendering, and UI/UX modernization).

================================================================================

