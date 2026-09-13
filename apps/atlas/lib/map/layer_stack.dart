// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_layers/atlas_layers.dart';
import 'package:atlas_providers/atlas_providers.dart';

abstract final class MapLayerIds {
  static const String base = 'raster-sources';
  static const String graticule = 'offline-graticule';
  static const String rings = 'range-rings';
  static const String waypoints = 'markers';
  static const String track = 'track-line';
  static const String measure = 'measurement-overlay-topmost';
  static const String position = 'position-fix';
}

AtlasLayerState _overlay(String id, bool visible) {
  return AtlasLayerState(
    definition: AtlasLayerDefinition(
      id: AtlasId(id),
      kind: AtlasLayerKind.vector,
      providerId: '',
      category: AtlasLayerCategory.overlay,
    ),
    visible: visible,
  );
}

AtlasLayerStack buildLayerStack({
  required AtlasProviderEndpoint endpoint,
  required bool graticuleVisible,
  required bool ringsVisible,
  required bool waypointsVisible,
  required bool trackVisible,
  required bool measureVisible,
}) {
  final descriptor = endpoint.descriptor;
  return AtlasLayerStack([
    AtlasLayerState(
      definition: AtlasLayerDefinition(
        id: const AtlasId(MapLayerIds.base),
        kind: AtlasLayerKind.raster,
        providerId: descriptor.id.value,
        category: AtlasLayerCategory.base,
        title: descriptor.title,
        minZoom: descriptor.nativeMinZoom?.toDouble(),
        maxZoom: descriptor.nativeMaxZoom?.toDouble(),
        attribution: descriptor.attribution,
      ),
    ),
    _overlay(MapLayerIds.graticule, graticuleVisible),
    _overlay(MapLayerIds.rings, ringsVisible),
    _overlay(MapLayerIds.waypoints, waypointsVisible),
    _overlay(MapLayerIds.track, trackVisible),
    _overlay(MapLayerIds.measure, measureVisible),
    _overlay(MapLayerIds.position, true),
  ]);
}

String attributionFor(AtlasLayerStack stack) {
  return AtlasAttribution.forVisible(stack).join(' · ');
}
