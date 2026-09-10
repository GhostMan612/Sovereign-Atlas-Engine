// Sovereign Atlas Engine — atlas_providers
// Live-data provider definitions (blueprint Phase 6 engine side).
//
// Contract: blueprint Phase 6 (NOAA/USGS/environmental families). Kept
// SEPARATE from the closed 2.2 proven set (AtlasBuiltinProviders frozen):
// live families have different freshness/policy semantics (liveRefresh
// capability; freshness model itself stays TBD per capability docs).
// - usgs-elevation: 3DEP tile endpoint (served through the tile fetch
//   operation like any tile family — kind honesty via elevation kind).
// - noaa-weather: dataset-source declaration (JSON API family; fetching is
//   a dataset-adapter concern downstream, same seam as requiresKey).
// Phase 6 slice. Depends on atlas_core + atlas_provider_api only.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'provider_endpoint.dart';
import 'provider_registry.dart';

/// Live-data definitions (freshness-sensitive; policy-declared).
abstract final class AtlasLiveProviders {
  static AtlasProviderEndpoint get usgsElevation => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('usgs-elevation'),
          kinds: {AtlasDataKind.elevation},
          title: 'USGS 3DEP Elevation',
          capabilities: {
            AtlasProviderCapability.tileServing,
            AtlasProviderCapability.liveRefresh,
          },
          nativeMinZoom: 0,
          nativeMaxZoom: 15,
          attribution: 'USGS 3D Elevation Program (3DEP)',
          license: 'Public domain (USGS)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: true,
          requiresKey: false,
        ),
        urlTemplate: 'https://elevation.nationalmap.gov/arcgis/rest/services/'
            '3DEPElevation/ImageServer/tile/{z}/{y}/{x}',
      );

  /// Live-data endpoints (tile-shaped families only).
  static List<AtlasProviderEndpoint> get all => [usgsElevation];

  /// Registry of the live set (compose with the builtin registry downstream;
  /// registries concatenate — no merge machinery here).
  static AtlasProviderRegistry registry() => AtlasProviderRegistry(all);
}
