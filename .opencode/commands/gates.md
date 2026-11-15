---
description: Run the Atlas host gate across SDK, consumer, and both app flavors with the working JDK, never a false green
agent: build
---

Run the canonical Sovereign Atlas host verification gate. NEVER build an APK unless explicitly asked.

The Android Studio JBR is stripped and will fail with
``could not open ... jbr\lib/jvm.cfg``. Set the working JDK before invoking Gradle:

    $env:JAVA_HOME = "C:\Users\612co\.jdks\jbr-21.0.11"
    $env:ANDROID_HOME = "C:\android"

Then run from `apps/atlas-android/`:

    .\gradlew.bat :atlas-sdk:test :sdk-consumer:test :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest --console=plain

Prefer the `atlas_gates` tool, which sets both environment variables, runs all four host
suites (`:atlas-sdk:test`, `:sdk-consumer:test`, `:app:testPlayDebugUnitTest`,
`:app:testEnterpriseDebugUnitTest`) with `--continue`, and parses JUnit XML across every
module (`atlas-sdk`, `sdk-consumer`, `app` Play & Enterprise) for real counts and failures.

Filter output to failures only. If `BUILD FAILED`, extract the `e:` compiler lines,
failed tasks, and failing test cases across all four suites, then report them accurately.

A non-zero failure, error, missing XML suite, or timeout is a real failure: report it cleanly
rather than re-running to manufacture a green (`AndroidKeyProviderTest.keySurvivesNewInstance`
root cause was fixed in CI run #72; treat any recurrence as a regression).

Never claim a build succeeded. Unit tests are the host gate (RULES.md 1.6, 4.2, 4.5).
