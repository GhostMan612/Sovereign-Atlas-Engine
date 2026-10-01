# SESSION_HANDOFF.md — Sovereign Atlas Engine (live state)

> Update every session per RULES.md §4.2. This file is the cold-start
> continuation point — state, facts, next moves. Evidence stays in
> `blueprints/app-track/`; this file points at it, never duplicates it.
>
> **Split 2026-09-30.** This file was 1,636 lines / 90 KB with ~40 `##` blocks,
> most of them closed sprints that a cold start had to read past to reach live
> state. All of that history now lives in **`blueprints/app-track/HISTORY.md`**
> (byte-identical, moved with `git mv`). Read it only when chasing a specific
> past decision. Do not read it on a normal cold start.

## Verification posture

- **The host gate is `:app:testPlayDebugUnitTest` + `:app:testEnterpriseDebugUnitTest`.**
  There is no `:app:testDebugUnitTest`. Two product flavors make the unflavored
  name ambiguous, and Gradle fails it with *"task 'testDebugUnitTest' is ambiguous
  in project ':app'"*. Confirmed by `--dry-run`. Do not quote that task name; it is
  the single most-repeated error in this repo's docs.
- **Last measured 2026-09-30: 411 tests, 0 failures+errors, per flavor
  (822 total across both).** Counts quoted in `HISTORY.md` are stale by
  construction — they were recorded when each block was written. Run the gate;
  never quote a historical count as current.
- The gate runs **once per phase** (RULES.md §1A, §3), never per edit. Use the
  `atlas_gates` tool; it sets both environment variables and counts the JUnit XML.
- A non-zero failure count is a real failure. Fix the code, or say why the test
  is wrong. Never re-run to manufacture a green.

## CI

- **`verify` is the only workflow and has been green since run #72 (2026-09-29).**
  Judge a red run by per-step conclusions, not the job conclusion.
- The long-red `Unit tests` step was a **Linux-only teardown race** in
  `AndroidKeyProviderTest.keySurvivesNewInstance`: `harnessOver` passed one
  `CoroutineScope` to both the DataStore builder and the provider, so `cancel()`
  tore down the `stateIn` collector that owned the scratch file. Fixed by joining
  the cancelled scope. Widening the timeout was never the fix and RULES 4.5 forbids it.
- Three other real CI defects were fixed with it: `setup-android` v3's bundled
  runtime, `gradlew` committed mode 100644, and `google-services` applied at
  configuration time without the gitignored `google-services.json`.
- **Reading CI output still requires a signed-in human.** Step logs are sign-in
  gated, the REST logs endpoint returns 403, artifact download returns 401. The
  upload-artifact step is the correct surface; tooling here cannot fetch it.

## Standing environment facts

- **No builds here unless explicitly asked** (RULES.md §1.6). The human builds and
  smokes in Android Studio; debug device failures from their pasted output.
- Toolchain: Android SDK `C:\android`, JDK 21, AGP 8.13.2, Kotlin 2.1.0, MapLibre 13.3.1.
- **Working JDK is `C:\Users\612co\.jdks\jbr-21.0.11`.** The Android Studio JBR at
  `C:\Program Files\Android\Android Studio\jbr` is stripped — no `lib/jvm.cfg` — and
  every Gradle invocation fails against it. Set both `JAVA_HOME` and `ANDROID_HOME`
  before any Gradle call. `atlas_gates` bakes them in.
- `androidTest/` connected tests need a device and run **only on explicit ask**.
- Never pipe `adb pull` or binary output through PowerShell pipes. Never edit sources
  via PS text pipelines. `adb kill-server` fixes most wedges (RULES.md §3).
- Device: Moto G **ZT4222BMWN**, driven by the `atlas_device` tool over wireless TCP.
  A device **is** attached here — an older handoff entry claiming otherwise was stale.

## Toolchain state

- `atlas_gates`, `atlas_purity_scan`, `atlas_device`, `atlas_maplibre_probe` are in
  `.opencode/plugin/atlas-tools.ts`. **Restart opencode after editing that file.**
- `.opencode/opencode.json` denies the whole read/search/write shell cmdlet class and
  the raw gradle assemble class, so the sanctioned tools are the only cheap path.
- The three blueprint files `MASTER_OPERATING_DIRECTIVE.md`, `phase-0/build-toolchain-spec.md`
  §2, and `camera-layers-sprint.md` §6 are **RETIRED**, marked in-file. They demanded a
  build per prompt and per-workstream gates. Do not implement their gate wording.
- **No linter is configured.** `detekt`/`ktlint`/`spotless` are absent from
  `app/build.gradle`; the real boundary mechanism is `atlas_purity_scan`.

## Open items

1. **`offline/` is a declared pure package that does real socket I/O** —
   `PackTileServer.kt` imports `ServerSocket`/`Socket`, `OfflineDownloader.kt`
   imports `HttpURLConnection`/`URL`. RULES.md §2.1 only forbids
   MapLibre/Android/Compose, so the scan passes it and this is *not* a gate failure.
   It is an open architecture question: does `offline/` belong in the pure tier at all?
   Fixing it means moving code behind an abstraction, which RULES 2.4 requires an ADR
   for first. **Undecided — no ADR written.**
2. **Untracked, not committed:** `IimStatementConverter.kt` and
   `IimStatementConverterTest.kt` under `app/src/test/.../core/`. They compile and are
   in the 411-test count, but `iim_statement.json` — the schema they were written
   against — is not in the workspace, so they are unverified against real input.
   Deliberately left out of every commit.
3. **Repo hygiene, awaiting operator call:** `Screenshot_20260923_015651.png` at the
   repo root is 1,784,169 bytes and referenced by zero source files. Not deleted
   without approval. Root `.gitignore` lacks `node_modules` and relies on
   `.opencode/.gitignore`; adding it at root is the safer default.
4. **Restart opencode** to pick up the widened shell denies and the plugin fixes.
5. Prior sessions' next moves — Phase 13 productization, terrain-aware LOS/DEM
   decision (RADIO-002), compass accuracy — are recorded in `HISTORY.md` under
   `Next actions`. None has an owner or a date.