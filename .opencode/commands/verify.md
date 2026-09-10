---
description: Run the host verification gates (analyze + tests, no builds)
---

Run the project verification gates in order. NEVER build anything.

1. `flutter pub get`
2. `flutter analyze --no-pub` — must report **No issues found**
3. `flutter test test/` — all host tests must pass
4. Engine suite: `dart tools/atlas_tool.dart all` — must report clean

Filter output to failures only. Report a compact summary: analyze status,
host count, engine totals, plus any failures with file:line.

Do NOT run `integration_test/` (it compiles a binary — needs explicit ask).
Do NOT run `flutter build`, emulator installs, or `flutter run`.
