// Sovereign Atlas Engine — atlas_providers public barrel.
//
// Blueprint Phase 2 (Basemap Matrix) engine side, ADR-003. Concrete provider
// implementations live here (never in atlas_provider_api, ADR-001). Depends:
// atlas_core + atlas_provider_api + atlas_tiles. Transport is dart:io behind
// an injected function type (no hosted dependencies).

export 'src/attribution.dart';
export 'src/builtin_providers.dart';
export 'src/live_providers.dart';
export 'src/pack_planner.dart';
export 'src/provider_endpoint.dart';
export 'src/provider_registry.dart';
export 'src/tile_fetch_operation.dart';
