# WO-C-02A.1 — rejected author attempts

Date: 2026-11-16. Status: **FAIL; no implementation accepted.** The pre-existing rejected Ollama response under `D:\venv-hub\atlas-generic-protobuf-author` was not modified or supplied to any new author. The author calls used local Ollama's raw single-message prompts, no tools, no prior context and no application source. Raw mode prevents the server's model template from inserting its default system prompt; model metadata is self-reported and does not prove training-data provenance. The model and runtime do not establish legal independence.

| Attempt | Model | Input SHA-256 | Output SHA-256 | Result |
| --- | --- | --- | --- | --- |
| Full source and tests | `qwen2.5-coder:1.5b` | `3f534f5db621500a6d4c1c37d60e2069356ce2c8637ea3b9cff36c10034314b9` | `0fbb5e23ccf5b51dccc2a72481fa1840ff682502c7f8bf22fe09e7b110f54728` | Incomplete at 4,096 output tokens; no complete source/test pair. |
| Source only | `qwen2.5-coder:1.5b` | `a7bc465cc0440aef447defd8a11b4cbf982ea23686e533ebc8c764768567026b` | `f9894b4f6f38707f5b96a16185ec9813c174108aba4260b94556f8d41efd86ac` | Complete response; Kotlin compilation failed with 12 errors. No tests ran. |
| Source only | `qwen2.5:3b` | `a7bc465cc0440aef447defd8a11b4cbf982ea23686e533ebc8c764768567026b` | `49a7300f4eece8301b69c1958b8c8ae6cdc13a5cbbb1b16f3d4e0ee4a85ac0dd` | Complete response; Kotlin compilation failed with 10 errors. No tests ran. |
| Source only | `phi4-mini:latest` | `a7bc465cc0440aef447defd8a11b4cbf982ea23686e533ebc8c764768567026b` | `89ba8b0b3762aab0ff590c70cd79ccc5e14a345a2a7dbee070d6bc1e41d65457` | Complete response; Kotlin compilation failed with 8 errors. No tests ran. |

The source-only attempts returned no author-written tests. Each was transferred unchanged to a standalone Kotlin/JVM test project and rejected on `compileKotlin`; none was added to the SDK. The first executor run failed because the test harness had Java target 21 and Kotlin target 17; the harness was corrected to target 17 before compiling the generated source. All source compilation attempts used Gradle 8.13, Kotlin 2.1.0 and JDK 21 on Windows, with offline dependencies. No Ubuntu build was run.

An independently written eight-test JUnit 4 suite is present in the ignored executor workspace, but it could not run because compilation failed. It is not an author deliverable or a passing result. No actual Meshtastic compatibility, binary artifact inspection or independent provenance approval is claimed.

The exact requests, raw responses, model metadata and hashes were copied to `tools/generic-protobuf-author/local-evidence/`, outside build output and excluded from Git; 26 copied files matched the original hashes. The original executor and test source are in `apps/atlas-android/build/wo-c-02a-executor/` and can be deleted by clean. The incomplete first response is recorded as `response.json` in its run directory even though it has no success manifest. No SDK, adapter, schema, signing or release configuration was changed during these attempts.
