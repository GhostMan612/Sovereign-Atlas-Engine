---
description: Run the host verification gates (unit tests, no builds)
---

Run the project verification gates in order. NEVER build an APK.

1. `:app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest` (Gradle, from `apps/atlas-android/`) —
   all unit tests must pass, 0 failures/errors.
2. Confirm `git status` shows no unintended files.

Filter output to failures only. Report a compact summary: total test
count plus any failures with file:line.

Do NOT run connected tests (needs a device — explicit ask only).
Do NOT run `assembleDebug`, emulator installs, or device runs.
