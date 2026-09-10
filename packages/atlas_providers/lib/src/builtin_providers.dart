// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'provider_endpoint.dart';
import 'provider_registry.dart';

abstract final class AtlasBuiltinProviders {
  static const _tileKinds = {AtlasDataKind.rasterTiles};

  static AtlasProviderEndpoint get osmStandard => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('osm-standard'),
          kinds: _tileKinds,
          title: 'OpenStreetMap Standard',
          capabilities: {AtlasProviderCapability.tileServing},
          nativeMinZoom: 0,
          nativeMaxZoom: 19,
          attribution: '© OpenStreetMap contributors',
          license: 'ODbL (openstreetmap.org/copyright)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: false,
          requiresKey: false,
          bulkGuard:
              'Bulk downloading is discouraged by the OSM Tile Usage Policy; '
              'heavy prefetch requires explicit operator approval (Phase 3).',
          userAgent: 'SovereignAtlasEngine/0.1.0 (OSM Tile Usage Policy)',
        ),
        urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
        headers: const {
          'User-Agent': 'SovereignAtlasEngine/0.1.0 (OSM Tile Usage Policy)',
        },
      );

  static AtlasProviderEndpoint get esriImagery => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('esri-imagery'),
          kinds: _tileKinds,
          title: 'Esri World Imagery',
          capabilities: {AtlasProviderCapability.tileServing},
          nativeMinZoom: 0,
          nativeMaxZoom: 19,
          attribution:
              'Esri, Maxar, Earthstar Geographics, and the GIS User Community',
          license: 'Esri Terms of Use (arcgis.com)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: true,
          requiresKey: false,
        ),
        urlTemplate: 'https://server.arcgisonline.com/ArcGIS/rest/services/'
            'World_Imagery/MapServer/tile/{z}/{y}/{x}',
      );

  static AtlasProviderEndpoint get esriLightGray => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('esri-light-gray'),
          kinds: _tileKinds,
          title: 'Esri Light Gray Canvas',
          capabilities: {AtlasProviderCapability.tileServing},
          nativeMinZoom: 0,
          nativeMaxZoom: 16,
          attribution: 'Esri, HERE, Garmin, OpenStreetMap contributors',
          license: 'Esri Terms of Use (arcgis.com)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: true,
          requiresKey: false,
        ),
        urlTemplate: 'https://server.arcgisonline.com/ArcGIS/rest/services/'
            'Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      );

  static AtlasProviderEndpoint get esriDarkGray => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('esri-dark-gray'),
          kinds: _tileKinds,
          title: 'Esri Dark Gray Canvas',
          capabilities: {AtlasProviderCapability.tileServing},
          nativeMinZoom: 0,
          nativeMaxZoom: 16,
          attribution: 'Esri, HERE, Garmin, OpenStreetMap contributors',
          license: 'Esri Terms of Use (arcgis.com)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: true,
          requiresKey: false,
        ),
        urlTemplate: 'https://server.arcgisonline.com/ArcGIS/rest/services/'
            'Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      );

  static AtlasProviderEndpoint get openTopoMap => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('opentopomap'),
          kinds: _tileKinds,
          title: 'OpenTopoMap',
          capabilities: {AtlasProviderCapability.tileServing},
          nativeMinZoom: 0,
          nativeMaxZoom: 17,
          attribution:
              '© OpenStreetMap contributors, SRTM | style: © OpenTopoMap (CC-BY-SA)',
          license: 'CC-BY-SA (opentopomap.org)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: false,
          requiresKey: false,
        ),
        urlTemplate: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png',

        params: const {'s': 'a'},
      );

  static AtlasProviderEndpoint get usgsTopo => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('usgs-topo'),
          kinds: _tileKinds,
          title: 'USGS Topo (The National Map)',
          capabilities: {AtlasProviderCapability.tileServing},
          nativeMinZoom: 0,
          nativeMaxZoom: 16,
          attribution: 'USGS The National Map',
          license: 'Public domain (USGS)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: true,
          requiresKey: false,
        ),
        urlTemplate: 'https://basemap.nationalmap.gov/arcgis/rest/services/'
            'USGSTopo/MapServer/tile/{z}/{y}/{x}',
      );

  static AtlasProviderEndpoint get localBundle => AtlasProviderEndpoint(
        descriptor: const AtlasProviderDescriptor(
          id: AtlasId('local-bundle'),
          kinds: _tileKinds,
          title: 'Local Bundle (file tree)',
          capabilities: {
            AtlasProviderCapability.tileServing,
            AtlasProviderCapability.offlinePacks,
          },
          attribution: 'Local bundle (provider attribution travels with data)',
          license: 'Bundle license (travels with data)',
        ),
        policy: const AtlasProviderPolicy(
          onlineAllowed: false,
          cacheAllowed: true,
          prefetchAllowed: false,
          requiresKey: false,
        ),
        urlTemplate: null,
      );

  static List<AtlasProviderEndpoint> get all => [
        osmStandard,
        esriImagery,
        esriLightGray,
        esriDarkGray,
        openTopoMap,
        usgsTopo,
        localBundle,
      ];

  static AtlasProviderRegistry registry() => AtlasProviderRegistry(all);
}
