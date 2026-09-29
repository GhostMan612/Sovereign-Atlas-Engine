---
description: Run the host verification gates (unit tests, no builds)
---

Run the project verification gates in order. NEVER build an APK.

1. `:app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest` (Gradle, from `apps/atlas-android/`) —
   all unit tests must pass, 0 failures/errors. The `atlas_gates` tool does this and reports
   real counts; expect 352 per flavor. Set JAVA_HOME to
   `C:\Users\612co\.jdks\jbr-21.0.11` first — the Android Studio JBR is stripped and fails
   with a missing `lib/jvm.cfg`.
2. Confirm `git status` shows no unintended files.

Filter output to failures only. Report a compact summary: total test
count plus any failures with file:line.

Do NOT run connected tests (needs a device — explicit ask only).
Do NOT run `assembleDebug`, emulator installs, or device runs.

Known flake, not a regression: `AndroidKeyProviderTest.keySurvivesNewInstance` times out
under parallel flavor load and passes on re-run. Report it as unresolved rather than
re-running until green.
