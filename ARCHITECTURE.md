# Architecture Boundary Rules

## Core Principles

1. The engine is independent of Sovereign Mantle and Recovery for All.
2. Rendering is an adapter; geospatial computation belongs in reusable domain packages.
3. Data providers are plugins/adapters, not hard-coded application logic.
4. Offline operation is a first-class capability.
5. Provenance, licensing, confidence, and source metadata travel with spatial data.
6. Sensitive/private datasets must remain explicitly scoped and must never acquire an unintended public fallback.
7. Tactical capabilities consume shared geospatial/terrain services rather than owning their own parallel geometry stack.
8. UI complexity is progressive: simple defaults, advanced tools accessible without being buried, expert tooling available through dedicated workspaces.
9. Production code requires tests at the domain boundaries before feature growth.
10. No application-specific dependencies in `atlas_core`.
