// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:convert';
import 'dart:io';

import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_location/atlas_location.dart';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/field/field_journal.dart';
import 'package:atlas/location/location_service.dart';
import 'package:atlas/main.dart';

import 'fake_location_source.dart';

Future<Directory> _freshDir(WidgetTester tester) async {
  final dir = await tester.runAsync(
    () => Directory.systemTemp.createTemp('go_to_page_test_'),
  );
  final path = dir!.path;
  addTearDown(() {
    final stale = Directory(path);
    if (stale.existsSync()) stale.deleteSync(recursive: true);
  });
  return dir;
}

FieldJournal _journal(Directory dir) {
  return FieldJournal(directoryProvider: () async => dir);
}

Future<void> pumpGoTo(
  WidgetTester tester, {
  required FieldJournal journal,
  LocationService? location,
}) async {
  await tester.pumpWidget(
    AtlasApp(fieldJournal: journal, locationService: location),
  );
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

Future<void> tapLocate(WidgetTester tester) async {
  await tester.tap(find.byTooltip('my-location'));
  await tester.pumpAndSettle();
}

Future<void> openWaypoints(WidgetTester tester) async {
  await tester.tap(find.byTooltip('waypoints'));
  await tester.pumpAndSettle();
}

Future<void> activateGoTo(WidgetTester tester, String label) async {
  await openWaypoints(tester);
  await tester.tap(find.text(label));
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const ValueKey<String>('waypoint-go-to')));
  await tester.pumpAndSettle();
}

String mapLine(WidgetTester tester) {
  return tester.widget<Text>(find.textContaining('MAP lat')).data!;
}

double mapRotation(WidgetTester tester) {
  return tester
      .widget<FlutterMap>(find.byType(FlutterMap))
      .mapController!
      .camera
      .rotation;
}

Marker? findWaypointMarker(WidgetTester tester, String id) {
  final key = ValueKey<String>('waypoint-$id');
  final layers = tester.widgetList<MarkerLayer>(
    find.byType(MarkerLayer, skipOffstage: false),
  );
  for (final layer in layers) {
    for (final marker in layer.markers) {
      if (marker.key == key) return marker;
    }
  }
  return null;
}

void main() {
  testWidgets('go-to activates from detail and moves camera to target', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    await pumpGoTo(tester, journal: journal);
    await activateGoTo(tester, 'Alpha');
    expect(find.byKey(const ValueKey<String>('go-to-card')), findsOneWidget);
    expect(find.textContaining('GO-TO Alpha'), findsOneWidget);
    expect(find.textContaining('45.0000'), findsWidgets);
    expect(mapLine(tester), contains('lat 45.0000'));
    expect(mapLine(tester), contains('lon -93.0000'));
    expect(mapLine(tester), contains('zoom 2.0'));
    expect(mapRotation(tester), 0.0);
  });

  testWidgets('valid fix shows live distance and bearing', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpGoTo(tester, journal: journal, location: location);
    await tapLocate(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -94.0));
    await activateGoTo(tester, 'Alpha');
    final fix = AtlasCoordinate(latitude: 45.0, longitude: -94.0);
    final target = AtlasCoordinate(latitude: 45.0, longitude: -93.0);
    expect(
      find.textContaining(
        AtlasGeoMath.formatDistance(AtlasGeoMath.haversineKm(fix, target)),
      ),
      findsOneWidget,
    );
    expect(find.textContaining('BRG 090°'), findsOneWidget);
    location.stop();
  });

  testWidgets('fix updates refresh navigation values', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpGoTo(tester, journal: journal, location: location);
    await tapLocate(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -94.0));
    await activateGoTo(tester, 'Alpha');
    final first = tester
        .widget<Text>(find.textContaining('Distance:'))
        .data!;
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.5));
    final second = tester
        .widget<Text>(find.textContaining('Distance:'))
        .data!;
    expect(first, isNot(contains('unavailable')));
    expect(second, isNot(contains('unavailable')));
    expect(second, isNot(first));
    location.stop();
  });

  testWidgets('no fix shows target with unavailable navigation', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    await pumpGoTo(tester, journal: journal);
    await activateGoTo(tester, 'Alpha');
    expect(find.textContaining('GO-TO Alpha'), findsOneWidget);
    expect(find.textContaining('45.0000'), findsWidgets);
    expect(find.textContaining('Distance: unavailable'), findsOneWidget);
    expect(find.textContaining('Bearing: unavailable'), findsOneWidget);
    expect(findWaypointMarker(tester, 'wp-000001'), isNotNull);
  });

  testWidgets('stale fix shows unavailable navigation', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(
      locationSource: fake,
      staleAfter: const Duration(milliseconds: 50),
    );
    addTearDown(location.dispose);
    await pumpGoTo(tester, journal: journal, location: location);
    await tapLocate(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -94.0));
    await tester.pump(const Duration(milliseconds: 150));
    expect(location.status, AtlasLocationStatus.stale);
    await activateGoTo(tester, 'Alpha');
    expect(find.textContaining('Distance: unavailable'), findsOneWidget);
    expect(find.textContaining('Bearing: unavailable'), findsOneWidget);
    location.stop();
  });

  testWidgets('clear removes card and preserves waypoint', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    await pumpGoTo(tester, journal: journal);
    await activateGoTo(tester, 'Alpha');
    final before = mapLine(tester);
    await tester.tap(find.byKey(const ValueKey<String>('go-to-clear')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('go-to-card')), findsNothing);
    expect(journal.lookup('wp-000001'), isNotNull);
    expect(findWaypointMarker(tester, 'wp-000001'), isNotNull);
    expect(mapLine(tester), before);
    expect(mapRotation(tester), 0.0);
  });

  testWidgets('fresh launch has no active target', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    await pumpGoTo(tester, journal: journal);
    await activateGoTo(tester, 'Alpha');
    expect(find.byKey(const ValueKey<String>('go-to-card')), findsOneWidget);
    await tester.pumpWidget(Container());
    await tester.pumpAndSettle();
    await tester.pumpWidget(AtlasApp(fieldJournal: journal));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('go-to-card')), findsNothing);
  });

  testWidgets('go-to writes nothing to the journal file', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    const seed = StoredWaypoint(
      id: 'wp-000001',
      latitude: 45.0,
      longitude: -93.0,
      createdAt: 7,
      label: 'Alpha',
      note: '',
      source: WaypointSource.mapSelected,
    );
    await tester.runAsync(() async {
      final journalDir = Directory('${dir.path}/field_data');
      await journalDir.create(recursive: true);
      await File('${journalDir.path}/journal.json').writeAsString(
        jsonEncode({
          'version': 1,
          'waypoints': [seed.toJson()],
        }),
        flush: true,
      );
    });
    await tester.runAsync(() => journal.restore());
    expect(journal.waypoints.length, 1);
    final file = File('${dir.path}/field_data/journal.json');
    final before = await tester.runAsync(() => file.readAsString());
    await pumpGoTo(tester, journal: journal);
    await activateGoTo(tester, 'Alpha');
    final during = await tester.runAsync(() => file.readAsString());
    expect(during, before);
    await tester.tap(find.byKey(const ValueKey<String>('go-to-clear')));
    await tester.pumpAndSettle();
    final after = await tester.runAsync(() => file.readAsString());
    expect(after, before);
    expect(journal.lastError, isNull);
  });

  testWidgets('deleted waypoint leaves a snapshot target', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    await pumpGoTo(tester, journal: journal);
    await activateGoTo(tester, 'Alpha');
    journal.remove('wp-000001');
    await tester.pump();
    expect(find.textContaining('GO-TO Alpha'), findsOneWidget);
    expect(journal.waypoints, isEmpty);
    await tester.tap(find.byKey(const ValueKey<String>('go-to-clear')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('go-to-card')), findsNothing);
  });

  testWidgets('measure tap capture still works beside go-to', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    await pumpGoTo(tester, journal: journal);
    await activateGoTo(tester, 'Alpha');
    await tester.tap(find.byTooltip('measure'));
    await tester.pumpAndSettle();
    final origin = tester.getTopLeft(find.byType(FlutterMap));
    await tester.tapAt(origin + const Offset(600.0, 120.0));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.textContaining(RegExp(r'Distance: \d')), findsOneWidget);
    expect(find.textContaining('Distance: unavailable'), findsOneWidget);
    expect(find.byKey(const ValueKey<String>('go-to-card')), findsOneWidget);
  });
}
