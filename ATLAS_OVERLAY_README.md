# Sovereign Atlas — Master Production Overlay v1.0

**Additive planning package, not an application patch.** Extract at the root of `C:\Sovereign-Atlas-Engine` after checking collisions. No existing source, rules, ADRs, or handoff files are replaced. No GitHub writes have been made.

## Current evidence (GitHub default branch, inspected 2026-10-12)
- `README.md`: single Kotlin + MapLibre native host at `apps/atlas-android`; Flutter/Dart retired lineage, not a current build target.
- `RULES.md`: canonical; genesis header; no extra code comments; no builds/device operations without explicit ask; one sanctioned host gate per phase; explicit-path staging; no push unless told.
- `SESSION_HANDOFF.md`: last recorded 411 unit tests per flavor on 2026-09-30, not a current run. Open questions: offline socket I/O in pure tier, untracked IIM converter without schema, root screenshot, lint absence, Phase 13 unfinished.
- README verification paragraph conflicts with its own older count; use fresh gate evidence, not any stale count.

## Entry point
1. Read `RULES.md`, `SESSION_HANDOFF.md`, `AGENTS.md` and `ARCHITECTURE.md` (existing repo files).
2. Read `blueprints/atlas-production/00_MASTER_PLAN.md`.
3. Give OpenCode `blueprints/atlas-production/01_EXECUTOR_MASTER_ORDER.md`.
4. Execute four phases in order; record evidence under `docs/atlas-production/evidence/` using supplied templates. Never automatically push or release.

**The term 'complete' is conditional:** production-ready requires live data/provider licensing, offline route data, physical acceptance, SDK consumer proof, release signing, and operator acceptance. The overlay is not evidence those are already implemented.
