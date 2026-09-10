# RULES.md — Sovereign Atlas Engine Operating Law
> **CANONICAL RULESET.** Every session MUST read this file before doing any work.
> If any other document contradicts this file, THIS FILE WINS.
> Supplements, never replaces: `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md`
> (frozen v1.0 Draft 2026-09-07) and `docs/architecture/` ADRs/DECs.

---

## 1. ABSOLUTE RULES (never violated, no exceptions)

### 1.1 The Sovereign Directives (100% canonical — source: `C:\sovereign_mantle\vault\blueprints\THE SOVEREIGN DIRECTIVES.txt`)

1. **NO PIECEMEALING.** Only full code: complete files, never snippets, half-functions, or "insert here" instructions.
2. **NO EXTRA COMMENTS IN CODE.** Zero conversational or explanatory comments inside code. Explanations belong in chat, not the file.
3. **GENESIS HEADER MANDATORY.** The only permitted comments are the genesis signature. Every new `.dart` / `.kt` / `.py` file begins with exactly:
   ```
   // ============================================================
   // As Above, So Below. As Within, So Without.
   // The Future Dictates the Past and the Past is Always Present.
   // ============================================================
   ```
4. **ABSOLUTE — no grandfathering** (operator directive 2026-09-10).
   All 117 `.dart` files were stripped to genesis-header-only in one
   verified pass (engine suite 453/413 + app 52/52 identical before and
   after — semantics preserved). Every file in this repo now obeys the
   Directives. Keep it that way.

### 1.2 External directories are READ-ONLY
Never create, modify, move, or delete ANYTHING under:
```
C:\pathfinder_god
C:\Recovery for All
C:\Sovereign Nodes
C:\sovereign_mantle
C:\sovereign_tagger
C:\sovereign_tagger_2
C:\sovereign_tagger_bak
```
- Folders matching `sovereign_tagger*` are READ-ONLY regardless of name.
- Reading and copying FROM them is allowed — Sovereign Mantle is the pattern goldmine. When in doubt: copy out, edit inside.
- The ONLY writable work area is `C:\Sovereign-Atlas-Engine` (plus approved tool homes: `%USERPROFILE%\.gradle`, pub-cache, `C:\android` SDK).

### 1.3 Secrets and commercial data never enter the repo
- Never commit: `.env`, API keys (**including CARTO credentials**), keystores, `local.properties`, `google-services.json`.
- Keys live on device/host only. Fixtures use synthetic placeholders. No key material in logs, APK source, or Git history — ever.

### 1.4 Git discipline
- **Stage by explicit path only.** `git add -A` / `git add .` are FORBIDDEN.
- Never force-push, rebase public history, or delete branches unless explicitly asked.
- Commit messages report gates status (analyze/test) only. **Never claim build success** — the human builds in Android Studio.

### 1.5 Synthetic data only
No real person names, addresses, or personal data in committed code, tests, or fixtures.

### 1.6 BUILD BOUNDARY — HARD RULE
- **NEVER run builds here unless explicitly asked.** No `flutter build apk|appbundle`, no emulator installs, no `flutter run`. Emulator builds are redundant — the human builds in Android Studio.
- Permitted gates: `flutter pub get`, `flutter analyze`, `flutter test` (host-side only).
- `integration_test/` files exist for the human's manual/device runs — do NOT execute them here unless explicitly asked (they compile a test binary).
- Debug device failures from the human's pasted output — never by rebuilding locally.

### 1.7 Nothing outside the project without approval
Do not install software, modify system settings, or write outside the work area / approved tool homes without asking first.

---

## 1A. CONTEXT & OUTPUT DISCIPLINE

- Filter all terminal output; pipe for failures only, never ingest passing noise.
- No massive file reads — probe large files/logs/data with short scripts or filtered searches.
- Targeted verification during development; full suites reserved for staged-commit verification.
- Spawn subagents for deep exploration when available; return summaries, not raw dumps.
- Proactively compact context after each verified phase.

---

## 2. PROJECT CONVENTIONS (Atlas architecture — non-negotiable)

1. **Engine/adapters boundary.** `apps/` + `integrations/` depend on `packages/` contracts — never the reverse. Engine `lib/` never imports Flutter, Android APIs, app screens, host SDKs, or single-vendor map SDK types in public models. Rendering and providers stay behind abstractions. No monolithic map controller.
2. **Offline-first.** Basic map startup must not require network. Failure modes are designed before a network feature is called complete.
3. **Provenance travels.** Licensing, attribution, confidence, and source metadata travel with spatial data. No invented averages, no silent defaults, no fabricated citations — unknown beats invented, always.
4. **No speculative architecture.** New packages, dependencies, or structural changes require an ADR/DEC in `docs/architecture/` first. No duplicating what an existing Atlas package owns. No deleting/renaming packages without an ADR.
5. **Master blueprint is frozen.** No substantive edits to `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md` without an explicit architect directive.
6. **Evidence before status.** No capability is implemented until tests or acceptance criteria demonstrate it. Golden fixtures required for numerical/geospatial behavior (`test/golden/`).

---

## 3. TECHNICAL LAWS (learned the hard way)

| Law | Rule |
|-----|------|
| PowerShell edits | NEVER modify source through PS text pipelines (`Get-Content/-replace/Set-Content`, `Out-File`). Editor tools only; Python `encoding='utf-8'` if scripted. |
| PowerShell binary pulls | NEVER pipe `adb pull` / binary output through PS pipes — write straight to file, no `Out-String`. |
| adb flakiness | `adb kill-server` recovers most wedges. Emulator lifetime here is ~15–25 min; a stale `multiinstance.lock` blocks reboot (delete it). |
| latlong2 import | `package:latlong2/latlong.dart` — never `latlong2.dart`. |
| Flutter radio API | `RadioGroup` (not deprecated `RadioListTile` groupValue). Verify widget names against the bundled SDK, not memory. |
| HttpOverrides | Hook is `createHttpClient`, not `createClient` (SDK rename — verify in `C:\src\flutter\bin\cache\dart-sdk`). |
| Tile keys | Engine-canonical `AtlasPackDownloader.keyOf` only — never hand-render `z/x/y`. |
| Rate limiting | Centralized shared limiter (per-pack limiters cannot resume — proven). |
| Pack ranges | Planner enumerates the full prism — the form requires all six bounds. |
| Timeout policy | App-side per-tile bound, default 30 s (`kDefaultPerTileTimeout`, DEC-020). `failed` + `TimeoutException` detail, cancel wins over timeout. |
| Dependency ceiling | SDK-pinned `material_color_utilities`/`test_api` are the hard ceiling (see pubspec comment). No `--major-versions` without a dedicated session. New deps need a written justification. |

---

## 4. WORKFLOW LAW

### 4.1 Cold start (every session, in order)
1. Read THIS file (`RULES.md`)
2. Read `SESSION_HANDOFF.md` (state, environment facts, next moves)
3. Read the master blueprint section / app-track doc for the task at hand
4. Work the current phase; consult ADRs as needed

### 4.2 Session end (every session)
1. `flutter analyze` clean + `flutter test` green (host gates)
2. Update `SESSION_HANDOFF.md` (where we are + next actions)
3. Tick affected checklists / app-track docs
4. Commit by explicit path with a descriptive message. **No push unless told.**

### 4.3 Verification law
- Host gates always: analyze + unit/widget + engine fixtures.
- Device claims ONLY from device runs (automated here when explicitly asked, or the human's pasted evidence). Screenshots alone never prove a behavior — instrumented assertions do.
- Transport-blocked ≠ radio-off. No pixels-as-proof. No invented requirements.

### 4.4 Scope law
Phases live in the blueprint first. Ship vertical slices; never let polish precede correctness gates. No engine rewrites to serve app convenience — change the adapter's placement, never weaken the contract.

### 4.5 Test posture law
- **Always run here:** engine fixture suite + app host tests (fast, no build — they are the contract proof).
- **Only on explicit ask:** `integration_test/` (compiles a test binary — see §1.6). Keep the files rigorous; the human runs them against his own builds.
- **Never weaken** a test to satisfy implementation. Fix the code; if the test is wrong, fix the test and say so.
