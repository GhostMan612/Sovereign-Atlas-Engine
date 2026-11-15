---
description: Run the host verification gates across SDK, consumer, and both app flavors (unit tests, no builds)
---

Run the project verification gates in order. NEVER build an APK.

1. `:atlas-sdk:test :sdk-consumer:test :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest` (Gradle, from `apps/atlas-android/`) —
   all unit tests must pass across all four suites, 0 failures/errors/skipped. The `atlas_gates` tool runs all four tasks and reports real JUnit XML counts across `atlas-sdk`, `sdk-consumer`, and both `:app` flavors. If invoking manually, set `JAVA_HOME` to `C:\Users\612co\.jdks\jbr-21.0.11` and `ANDROID_HOME` to `C:\android` first — the Android Studio JBR is stripped and fails with a missing `lib/jvm.cfg`.
2. Confirm `git status` shows no unintended files.

Filter output to failures only. Report a compact summary: per-suite counts (`:atlas-sdk:test`, `:sdk-consumer:test`, Play, Enterprise), total test count, and any failures with `file:line`.

Do NOT run connected tests (needs a device — explicit ask only).
Do NOT run `assembleDebug` or `assembleRelease`, emulator installs, or device runs.

Treat any non-zero failure, error, or timeout as a real failure rather than re-running to manufacture a green (`AndroidKeyProviderTest.keySurvivesNewInstance` root cause was fixed in CI run #72).
