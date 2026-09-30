---
description: Run the Atlas host gate with the working JDK, report real counts, never a false green
agent: build
---

Run the Sovereign Atlas host verification gates. NEVER build an APK unless explicitly asked.

The Android Studio JBR is stripped and will fail with
``could not open ... jbr\lib/jvm.cfg``. Set the working JDK before invoking Gradle:

    $env:JAVA_HOME = "C:\Users\612co\.jdks\jbr-21.0.11"
    $env:ANDROID_HOME = "C:\android"

Then run from `apps/atlas-android/`:

    .\gradlew.bat :app:testPlayDebugUnitTest :app:testEnterpriseDebugUnitTest --console=plain

Prefer the `atlas_gates` tool, which does both steps and parses the JUnit XML for real
counts. Expect 352 tests per flavor, 0 failures.

Filter output to failures only. If `BUILD FAILED`, extract only the `e:` compiler lines and
the failing task, then report them with `file:line`.

Known flake, not a regression: `AndroidKeyProviderTest.keySurvivesNewInstance` intermittently
times out after 10000 ms under parallel flavor load and passes on an immediate re-run. It is
still an unresolved harness problem. If it fires, report it as the known flake and say it
remains unresolved, rather than quietly re-running until green.

Never claim a build succeeded. Unit tests are the host gate (RULES.md 1.6, 4.5).
