// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'provider_endpoint.dart';
import 'provider_registry.dart';

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

  static List<AtlasProviderEndpoint> get all => [usgsElevation];

  static AtlasProviderRegistry registry() => AtlasProviderRegistry(all);
}
