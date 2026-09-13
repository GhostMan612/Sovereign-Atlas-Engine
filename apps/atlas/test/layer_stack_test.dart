// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_layers/atlas_layers.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/map/layer_stack.dart';

AtlasProviderEndpoint endpointFor(String id) {
  final endpoint = AtlasBuiltinProviders.registry().lookup(id);
  expect(endpoint, isNotNull, reason: 'registry must hold $id');
  return endpoint!;
}

AtlasLayerStack fullStack(AtlasProviderEndpoint endpoint) {
  return buildLayerStack(
    endpoint: endpoint,
    graticuleVisible: true,
    ringsVisible: true,
    waypointsVisible: true,
    trackVisible: true,
    measureVisible: true,
  );
}

List<String> stackIds(AtlasLayerStack stack) {
  return [
    for (final state in stack.states) state.definition.id.value,
  ];
}

void main() {
  group('stack composition', () {
    test('ids are deterministic and ordered base to topmost', () {
      final stack = fullStack(endpointFor('osm-standard'));
      expect(stackIds(stack), [
        'raster-sources',
        'offline-graticule',
        'range-rings',
        'markers',
        'track-line',
        'measurement-overlay-topmost',
        'position-fix',
      ]);
    });

    test('rebuilds are deterministic and never mutate the source', () {
      final endpoint = endpointFor('osm-standard');
      final first = fullStack(endpoint);
      final second = fullStack(endpoint);
      expect(second, first);
      final hidden = buildLayerStack(
        endpoint: endpoint,
        graticuleVisible: false,
        ringsVisible: false,
        waypointsVisible: false,
        trackVisible: false,
        measureVisible: false,
      );
      expect(first.orderedVisible().length, 7);
      expect(hidden.orderedVisible().length, 2);
    });

    test('stack validates and conforms to the engine baseline order', () {
      final stack = fullStack(endpointFor('osm-standard'));
      expect(stack.validate().isValid, isTrue);
      expect(
        stack.conformsToBaseline(AtlasBaselineRanks.rankOf),
        isTrue,
      );
    });

    test('visibility flags control exactly their own layer', () {
      final endpoint = endpointFor('osm-standard');
      final stack = buildLayerStack(
        endpoint: endpoint,
        graticuleVisible: true,
        ringsVisible: false,
        waypointsVisible: true,
        trackVisible: true,
        measureVisible: true,
      );
      final visibleIds = [
        for (final state in stack.orderedVisible())
          state.definition.id.value,
      ];
      expect(visibleIds, contains('offline-graticule'));
      expect(visibleIds, isNot(contains('range-rings')));
    });

    test('only implemented layers are exposed', () {
      final stack = fullStack(endpointFor('osm-standard'));
      expect(stack.states.length, 7);
      expect(
        stackIds(stack).toSet(),
        {
          MapLayerIds.base,
          MapLayerIds.graticule,
          MapLayerIds.rings,
          MapLayerIds.waypoints,
          MapLayerIds.track,
          MapLayerIds.measure,
          MapLayerIds.position,
        },
      );
    });
  });

  group('attribution', () {
    test('follows the visible provider', () {
      final osm = attributionFor(fullStack(endpointFor('osm-standard')));
      expect(osm, contains('© OpenStreetMap contributors'));
      final satellite = attributionFor(
        fullStack(endpointFor('esri-imagery')),
      );
      expect(satellite, isNot(contains('© OpenStreetMap contributors')));
      expect(satellite, isNotEmpty);
    });

    test('inactive layers contribute no attribution', () {
      AtlasLayerState attributed(String id, bool visible) {
        return AtlasLayerState(
          definition: AtlasLayerDefinition(
            id: AtlasId(id),
            kind: AtlasLayerKind.vector,
            providerId: 'test-provider',
            category: AtlasLayerCategory.overlay,
            attribution: 'Attribution for $id',
          ),
          visible: visible,
        );
      }

      final stack = AtlasLayerStack([
        attributed('shown-layer', true),
        attributed('hidden-layer', false),
      ]);
      final texts = AtlasAttribution.forVisible(stack);
      expect(texts, contains('Attribution for shown-layer'));
      expect(texts, isNot(contains('Attribution for hidden-layer')));
    });
  });

  group('provider ceilings', () {
    test('native ceilings travel with the endpoint descriptor', () {
      expect(
        endpointFor('osm-standard').descriptor.nativeMaxZoom,
        19,
      );
      expect(
        endpointFor('opentopomap').descriptor.nativeMaxZoom,
        17,
      );
      expect(
        endpointFor('esri-dark-gray').descriptor.nativeMaxZoom,
        16,
      );
    });
  });
}
