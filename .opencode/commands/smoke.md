---
description: Manual smoke checklist for the human's Android Studio runs (no builds here)
---

This command NEVER builds. It produces/verifies the manual smoke checklist
the human runs in Android Studio against his own builds.

1. First run the `/verify` gate here and confirm green — abort the checklist if host gates fail.
2. Present the smoke matrix for the current track (adapt to what just landed):
   - Map boots offline (airplane mode) with no crash
   - Basemap picker switches template + attribution
   - Offline pack: plan → download → Saved Areas → Storage numbers sane
   - Diagnostics page shows live state, no stale counters
   - Rotation / background / relaunch keeps state sane
3. The human executes on his device and pastes back results or log excerpts.
4. Record returned evidence in the matching `blueprints/app-track/` doc.
5. Never run a build to satisfy this checklist. If device automation is ever
   wanted here, ask explicitly first (`integration_test/` compiles).
