// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:io';

import 'package:atlas_location/atlas_location.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/field/field_journal.dart';
import 'package:atlas/location/location_service.dart';
import 'package:atlas/main.dart';
import 'package:atlas/offline/offline_repository.dart';

import 'fake_location_source.dart';

Future<Directory> freshDir(WidgetTester tester) async {
  final dir = await tester.runAsync(
    () => Directory.systemTemp.createTemp('camera_layers_page_test_'),
  );
  final path = dir!.path;
  addTearDown(() {
    final stale = Directory(path);
    if (stale.existsSync()) stale.deleteSync(recursive: true);
  });
  return dir;
}

FieldJournal journalFor(Directory dir) {
  return FieldJournal(directoryProvider: () async => dir);
}

LocationService scopedService(FakeLocationSource fake) {
  final service = LocationService(locationSource: fake);
  addTearDown(service.dispose);
  return service;
}

Future<void> pumpCamera(
  WidgetTester tester, {
  required FieldJournal journal,
  LocationService? location,
  OfflineRepository? repository,
}) async {
  await tester.pumpWidget(
    AtlasApp(
      fieldJournal: journal,
      locationService: location,
      repository: repository,
    ),
  );
  await tester.pumpAndSettle();
}

Future<void> tapLocate(WidgetTester tester) async {
  await tester.tap(find.byTooltip('my-location'));
  await tester.pumpAndSettle();
}

Future<void> emitFix(
  WidgetTester tester,
  FakeLocationSource fake,
  AtlasLocationFix fix,
) async {
  fake.emit(fix);
  await tester.idle();
  await tester.pump();
}

String mapLine(WidgetTester tester) {
  return tester.widget<Text>(find.textContaining('MAP lat')).data!;
}

MapController mapController(WidgetTester tester) {
  return tester.widget<FlutterMap>(find.byType(FlutterMap)).mapController!;
}

MapCamera mapCamera(WidgetTester tester) {
  return mapController(tester).camera;
}

Future<void> openBasemapSheet(WidgetTester tester) async {
  await tester.tap(find.byTooltip('Basemap'));
  await tester.pumpAndSettle();
}

Future<void> toggleSheetLayer(
  WidgetTester tester,
  String key,
) async {
  final finder = find.byKey(ValueKey<String>(key));
  await tester.scrollUntilVisible(
    finder,
    200.0,
    scrollable: find.descendant(
      of: find.byType(BottomSheet),
      matching: find.byType(Scrollable),
    ),
  );
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

Future<void> closeSheet(WidgetTester tester) async {
  await tester.tapAt(const Offset(400.0, 30.0));
  await tester.pumpAndSettle();
  expect(find.byKey(const ValueKey<String>('layer-graticule')), findsNothing);
}

Marker? findKeyedMarker(WidgetTester tester, String key) {
  final layers = tester.widgetList<MarkerLayer>(
    find.byType(MarkerLayer, skipOffstage: false),
  );
  for (final layer in layers) {
    for (final marker in layer.markers) {
      if (marker.key == ValueKey<String>(key)) return marker;
    }
  }
  return null;
}

void main() {
  group('startup camera', () {
    testWidgets('no permission means honest overview and no prompt', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final fake = FakeLocationSource();
      await pumpCamera(
        tester,
        journal: journalFor(dir),
        location: scopedService(fake),
      );
      expect(mapLine(tester), contains('lat 0.0000'));
      expect(mapLine(tester), contains('zoom 2.0'));
      expect(fake.requestCalls, 0);
      expect(findKeyedMarker(tester, 'position-marker'), isNull);
    });

    testWidgets('granted seed opens a local view without any tap', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final fake = FakeLocationSource()
        ..queryResult = grantedQuery
        ..seedResult = testFix(latitude: 10.0, longitude: 20.0);
      final location = scopedService(fake);
      await pumpCamera(
        tester,
        journal: journalFor(dir),
        location: location,
      );
      expect(mapLine(tester), contains('lat 10.0000'));
      expect(mapLine(tester), contains('lon 20.0000'));
      expect(mapLine(tester), contains('zoom 13.0'));
      location.stop();
    });
  });

  group('my-location camera', () {
    testWidgets('valid fix centers at zoom 15', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final fake = FakeLocationSource()
        ..queryResult = grantedQuery
        ..seedResult = testFix(latitude: 11.0, longitude: 21.0);
      final location = scopedService(fake);
      await pumpCamera(
        tester,
        journal: journalFor(dir),
        location: location,
      );
      await tapLocate(tester);
      expect(mapLine(tester), contains('lat 11.0000'));
      expect(mapLine(tester), contains('zoom 15.0'));
      expect(mapCamera(tester).zoom, 15.0);
      location.stop();
    });

    testWidgets('my-location preserves bearing and never rotates', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final fake = FakeLocationSource()
        ..queryResult = grantedQuery
        ..seedResult = testFix();
      final location = scopedService(fake);
      await pumpCamera(
        tester,
        journal: journalFor(dir),
        location: location,
      );
      mapController(tester).rotate(30.0);
      await tester.pumpAndSettle();
      expect(mapCamera(tester).rotation, 30.0);
      await tapLocate(tester);
      expect(mapCamera(tester).rotation, 30.0);
      expect(mapCamera(tester).zoom, 15.0);
      location.stop();
    });

    testWidgets('my-location with no fix moves nothing', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final fake = FakeLocationSource()..queryResult = grantedQuery;
      await pumpCamera(
        tester,
        journal: journalFor(dir),
        location: scopedService(fake),
      );
      await tapLocate(tester);
      expect(mapLine(tester), contains('lat 0.0000'));
      expect(mapLine(tester), contains('zoom 2.0'));
    });
  });

  group('go-to separation', () {
    testWidgets('go-to still preserves the current zoom', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final journal = journalFor(dir);
      journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
      final fake = FakeLocationSource()
        ..queryResult = grantedQuery
        ..seedResult = testFix();
      final location = scopedService(fake);
      await pumpCamera(
        tester,
        journal: journal,
        location: location,
      );
      await tapLocate(tester);
      expect(mapCamera(tester).zoom, 15.0);
      await tester.tap(find.byTooltip('waypoints'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Alpha'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey<String>('waypoint-go-to')));
      await tester.pumpAndSettle();
      expect(mapLine(tester), contains('lat 45.0000'));
      expect(mapCamera(tester).zoom, 15.0);
      location.stop();
    });
  });

  group('layer toggles', () {
    testWidgets('graticule toggle renders and removes engine geometry', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      await pumpCamera(tester, journal: journalFor(dir));
      expect(
        find.byKey(const ValueKey<String>('graticule-layer')),
        findsNothing,
      );
      await openBasemapSheet(tester);
      await toggleSheetLayer(tester, 'layer-graticule');
      final layer = tester.widget<PolylineLayer>(
        find.byKey(const ValueKey<String>('graticule-layer')),
      );
      expect(layer.polylines, isNotEmpty);
      await toggleSheetLayer(tester, 'layer-graticule');
      expect(
        find.byKey(const ValueKey<String>('graticule-layer')),
        findsNothing,
      );
      await closeSheet(tester);
    });

    testWidgets('rings toggle renders and removes range geometry', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final fake = FakeLocationSource()
        ..queryResult = grantedQuery
        ..seedResult = testFix(latitude: 10.0, longitude: 20.0);
      final location = scopedService(fake);
      await pumpCamera(
        tester,
        journal: journalFor(dir),
        location: location,
      );
      expect(
        find.byKey(const ValueKey<String>('rings-layer')),
        findsNothing,
      );
      await openBasemapSheet(tester);
      await toggleSheetLayer(tester, 'layer-rings');
      final layer = tester.widget<PolylineLayer>(
        find.byKey(const ValueKey<String>('rings-layer')),
      );
      expect(layer.polylines.length, 8);
      await toggleSheetLayer(tester, 'layer-rings');
      expect(
        find.byKey(const ValueKey<String>('rings-layer')),
        findsNothing,
      );
      await closeSheet(tester);
      location.stop();
    });

    testWidgets('waypoints toggle hides and restores markers', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final journal = journalFor(dir);
      journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
      await pumpCamera(tester, journal: journal);
      expect(findKeyedMarker(tester, 'waypoint-wp-000001'), isNotNull);
      await openBasemapSheet(tester);
      await toggleSheetLayer(tester, 'layer-waypoints');
      expect(findKeyedMarker(tester, 'waypoint-wp-000001'), isNull);
      await toggleSheetLayer(tester, 'layer-waypoints');
      expect(findKeyedMarker(tester, 'waypoint-wp-000001'), isNotNull);
      await closeSheet(tester);
    });

    testWidgets('track toggle hides and restores the active line', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final journal = journalFor(dir);
      final fake = FakeLocationSource()..queryResult = grantedQuery;
      final location = scopedService(fake);
      await pumpCamera(tester, journal: journal, location: location);
      await tapLocate(tester);
      await tester.tap(find.byTooltip('tracks'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey<String>('track-start')));
      await tester.pumpAndSettle();
      await tester.pageBack();
      await tester.pumpAndSettle();
      await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.0));
      expect(
        find.byKey(const ValueKey<String>('track-line')),
        findsOneWidget,
      );
      await openBasemapSheet(tester);
      await toggleSheetLayer(tester, 'layer-track');
      expect(
        find.byKey(const ValueKey<String>('track-line')),
        findsNothing,
      );
      await toggleSheetLayer(tester, 'layer-track');
      expect(
        find.byKey(const ValueKey<String>('track-line')),
        findsOneWidget,
      );
      await closeSheet(tester);
      location.stop();
    });

    testWidgets('measurement toggle hides and restores markers', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      await pumpCamera(tester, journal: journalFor(dir));
      await tester.tap(find.byTooltip('measure'));
      await tester.pumpAndSettle();
      await tester.tapAt(const Offset(200.0, 200.0));
      await tester.pump(const Duration(milliseconds: 500));
      await tester.pumpAndSettle();
      expect(findKeyedMarker(tester, 'measure-a'), isNotNull);
      await openBasemapSheet(tester);
      await toggleSheetLayer(tester, 'layer-measure');
      expect(findKeyedMarker(tester, 'measure-a'), isNull);
      await toggleSheetLayer(tester, 'layer-measure');
      expect(findKeyedMarker(tester, 'measure-a'), isNotNull);
      await closeSheet(tester);
    });
  });

  group('attribution and ceilings', () {
    testWidgets('attribution follows the visible provider', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      await pumpCamera(tester, journal: journalFor(dir));
      expect(
        find.textContaining('© OpenStreetMap contributors'),
        findsOneWidget,
      );
      await openBasemapSheet(tester);
      await tester.tap(find.text('Satellite'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('© OpenStreetMap contributors'),
        findsNothing,
      );
      final tileLayer = tester.widget<TileLayer>(find.byType(TileLayer));
      expect(tileLayer.maxNativeZoom, 19);
    });

    testWidgets('provider ceiling reaches the tile layer honestly', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      await pumpCamera(tester, journal: journalFor(dir));
      await openBasemapSheet(tester);
      await tester.tap(find.text('Topographic'));
      await tester.pumpAndSettle();
      final tileLayer = tester.widget<TileLayer>(find.byType(TileLayer));
      expect(tileLayer.maxNativeZoom, 17);
    });
  });

  group('offline safety', () {
    testWidgets('my-location creates no offline pack', (
      WidgetTester tester,
    ) async {
      final dir = await freshDir(tester);
      final repository = OfflineRepository(
        registry: AtlasBuiltinProviders.registry(),
        chunkSourceFactory: (_) => (_) async => <int>[],
        directoryProvider: () async => dir,
      );
      final fake = FakeLocationSource()
        ..queryResult = grantedQuery
        ..seedResult = testFix();
      final location = scopedService(fake);
      await pumpCamera(
        tester,
        journal: journalFor(dir),
        location: location,
        repository: repository,
      );
      await tapLocate(tester);
      expect(mapCamera(tester).zoom, 15.0);
      expect(repository.packs, isEmpty);
      location.stop();
    });
  });
}
