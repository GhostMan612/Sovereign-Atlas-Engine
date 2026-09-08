# Sovereign Atlas Engine

A modular, portable geospatial engine descended from the Sovereign Mantle TacMap and designed to serve as a reusable mapping platform for Sovereign Mantle, Recovery for All, and the standalone Atlas application.

## Project Direction

Sovereign Atlas Engine is being built as a geospatial platform rather than a single map screen. The engine separates rendering, tiles, geographic computation, data providers, offline storage, analysis, terrain, tactical tooling, historical mapping, security, and plugins behind reusable interfaces.

## Reference Implementations

- Sovereign Mantle `LandSectorView.kt` — reference TacMap implementation.
- Recovery for All `meeting_map_screen.dart` and `map_tile_cache.dart` — first Flutter portability implementation.

## Planned Consumers

- Standalone Sovereign Atlas application
- Sovereign Mantle
- Recovery for All

## Architecture

See:

- `blueprints/ATLAS_ENGINE_MASTER_BLUEPRINT.md`
- `docs/architecture/`
- `blueprints/phase-0/`

## Repository Status

Phase 0 — foundation and extraction.

No production engine code is committed yet. The initial structure is intentionally separated so implementation can proceed package-by-package without coupling the engine to a single application.
