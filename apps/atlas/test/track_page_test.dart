// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:atlas/field/field_journal.dart';
import 'package:atlas/location/location_service.dart';
import 'package:atlas/main.dart';
import 'package:atlas_location/atlas_location.dart';

import 'fake_location_source.dart';

Future<Directory> _freshDir(WidgetTester tester) async {
  final dir = await tester.runAsync(
    () => Directory.systemTemp.createTemp('track_page_test_'),
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

Future<void> _seedTrackFile(
  WidgetTester tester,
  Directory dir,
  StoredTrack track,
) async {
  await tester.runAsync(() async {
    final journalDir = Directory('${dir.path}/field_data');
    await journalDir.create(recursive: true);
    await File('${journalDir.path}/journal.json').writeAsString(
      jsonEncode({
        'version': 1,
        'waypoints': [],
        'tracks': [track.toJson()],
      }),
      flush: true,
    );
  });
}

const StoredTrack _seededTrack = StoredTrack(
  id: 'trk-000001',
  createdAt: 1000,
  points: [
    StoredWaypoint(
      id: 'trk-000001-p0001',
      latitude: 45.0,
      longitude: -93.0,
      createdAt: 1000,
      source: WaypointSource.gpsRecorded,
    ),
  ],
  source: WaypointSource.gpsRecorded,
);

Future<void> pumpTrack(
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

Future<void> openTracks(WidgetTester tester) async {
  await tester.tap(find.byTooltip('tracks'));
  await tester.pumpAndSettle();
}

Future<void> startRecording(WidgetTester tester) async {
  await openTracks(tester);
  await tester.tap(find.byKey(const ValueKey<String>('track-start')));
  await tester.pumpAndSettle();
  await tester.pageBack();
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

int trackLinePointCount(WidgetTester tester) {
  final layer = tester.widget<PolylineLayer>(
    find.byKey(const ValueKey<String>('track-line')),
  );
  return layer.polylines.single.points.length;
}

Marker? positionMarker(WidgetTester tester) {
  const key = ValueKey<String>('position-marker');
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
  testWidgets('tracks page opens empty with a start control', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    await pumpTrack(tester, journal: _journal(dir));
    await openTracks(tester);
    expect(find.text('Tracks'), findsOneWidget);
    expect(find.textContaining('No tracks yet'), findsOneWidget);
    expect(find.byKey(const ValueKey<String>('track-start')), findsOneWidget);
    expect(find.byKey(const ValueKey<String>('track-rec-badge')), findsNothing);
  });

  testWidgets('start shows recording badge with zero points', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    await pumpTrack(tester, journal: _journal(dir));
    await openTracks(tester);
    await tester.tap(find.byKey(const ValueKey<String>('track-start')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Recording • 0 pts'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('track-rec-badge')), findsOneWidget);
    expect(find.textContaining('REC • 0 pts'), findsOneWidget);
  });

  testWidgets('emitted fixes grow the badge count and polyline', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await startRecording(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.0));
    await emitFix(tester, fake, testFix(latitude: 45.1, longitude: -93.1));
    expect(find.textContaining('REC • 2 pts'), findsOneWidget);
    expect(trackLinePointCount(tester), 2);
    location.stop();
  });

  testWidgets('stop persists the track and clears the active line', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await startRecording(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.0));
    await emitFix(tester, fake, testFix(latitude: 45.1, longitude: -93.1));
    await openTracks(tester);
    await tester.tap(find.byKey(const ValueKey<String>('track-stop')));
    await tester.pumpAndSettle();
    expect(journal.tracks.length, 1);
    expect(journal.tracks.single.id, 'trk-000001');
    expect(journal.tracks.single.pointCount, 2);
    expect(find.textContaining('trk-000001 · 2 pts'), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('track-rec-badge')), findsNothing);
    expect(
      find.byKey(const ValueKey<String>('track-line')),
      findsNothing,
    );
    location.stop();
  });

  testWidgets('empty stop persists nothing and clears cleanly', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    await pumpTrack(tester, journal: journal);
    await openTracks(tester);
    await tester.tap(find.byKey(const ValueKey<String>('track-start')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey<String>('track-stop')));
    await tester.pumpAndSettle();
    expect(journal.tracks, isEmpty);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('track-rec-badge')), findsNothing);
  });

  testWidgets('track detail shows fields and delete removes it', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await startRecording(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.0));
    await emitFix(tester, fake, testFix(latitude: 45.1, longitude: -93.1));
    await openTracks(tester);
    await tester.tap(find.byKey(const ValueKey<String>('track-stop')));
    await tester.pumpAndSettle();
    await tester.tap(find.textContaining('trk-000001 · 2 pts'));
    await tester.pumpAndSettle();
    expect(find.text('trk-000001'), findsWidgets);
    expect(find.textContaining('Points: 2'), findsOneWidget);
    expect(find.textContaining('Distance:'), findsOneWidget);
    expect(find.textContaining('Started:'), findsOneWidget);
    expect(find.textContaining('Duration:'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey<String>('track-delete')));
    await tester.pumpAndSettle();
    expect(journal.tracks, isEmpty);
    expect(find.textContaining('No tracks yet'), findsOneWidget);
    location.stop();
  });

  testWidgets('restart shows persisted tracks with no active recording', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    const channel = MethodChannel('plugins.flutter.io/path_provider');
    final cacheDir = await tester.runAsync(() async {
      final cache = Directory(
        '${Directory.systemTemp.path}/track_tile_cache',
      );
      if (!await cache.exists()) await cache.create(recursive: true);
      return cache.path;
    });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (MethodCall call) async {
      if (call.method == 'getApplicationCacheDirectory') {
        return cacheDir;
      }
      return null;
    });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );
    await _seedTrackFile(tester, dir, _seededTrack);
    final journal = _journal(dir);
    await tester.runAsync(() => journal.restore());
    expect(journal.tracks.length, 1);
    await tester.pumpWidget(AtlasApp(fieldJournal: journal));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('track-rec-badge')), findsNothing);
    expect(find.byKey(const ValueKey<String>('track-line')), findsNothing);
    await openTracks(tester);
    expect(find.textContaining('trk-000001 · 1 pts'), findsOneWidget);
  });

  testWidgets('stale fixes never seed or extend a recording', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(
      locationSource: fake,
      staleAfter: const Duration(milliseconds: 50),
    );
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.0));
    await tester.pump(const Duration(milliseconds: 150));
    expect(location.status, AtlasLocationStatus.stale);
    await startRecording(tester);
    expect(find.textContaining('REC • 0 pts'), findsOneWidget);
    await emitFix(tester, fake, testFix(latitude: 45.1, longitude: -93.1));
    expect(find.textContaining('REC • 1 pts'), findsOneWidget);
    location.stop();
  });

  testWidgets('recording without any fix draws nothing at origin', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    await pumpTrack(tester, journal: _journal(dir));
    await startRecording(tester);
    expect(find.byKey(const ValueKey<String>('track-line')), findsNothing);
    expect(positionMarker(tester), isNull);
    expect(find.textContaining('REC • 0 pts'), findsOneWidget);
  });

  testWidgets('recording leaves camera rotation and center alone', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await emitFix(tester, fake, testFix(latitude: 44.0, longitude: -92.0));
    final before = mapLine(tester);
    await startRecording(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.0));
    await emitFix(tester, fake, testFix(latitude: 45.1, longitude: -93.1));
    expect(mapLine(tester), before);
    expect(mapRotation(tester), 0.0);
    location.stop();
  });

  testWidgets('go-to card stays live beside an active recording', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Alpha');
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await startRecording(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -94.0));
    await tester.tap(find.byTooltip('waypoints'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Alpha'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey<String>('waypoint-go-to')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey<String>('go-to-card')), findsOneWidget);
    expect(find.byKey(const ValueKey<String>('track-rec-badge')), findsOneWidget);
    expect(find.textContaining('Distance:'), findsWidgets);
    location.stop();
  });

  testWidgets('measure capture still works beside a recording', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await startRecording(tester);
    await emitFix(tester, fake, testFix(latitude: 45.0, longitude: -93.0));
    await tester.tap(find.byTooltip('measure'));
    await tester.pumpAndSettle();
    final origin = tester.getTopLeft(find.byType(FlutterMap));
    await tester.tapAt(origin + const Offset(600.0, 120.0));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.textContaining(RegExp(r'Distance: \d')), findsOneWidget);
    expect(find.byKey(const ValueKey<String>('track-rec-badge')), findsOneWidget);
    expect(trackLinePointCount(tester), 1);
    location.stop();
  });

  testWidgets('position marker stays correct during recording', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final location = LocationService(locationSource: fake);
    addTearDown(location.dispose);
    await pumpTrack(tester, journal: journal, location: location);
    await tapLocate(tester);
    await startRecording(tester);
    await emitFix(tester, fake, testFix(latitude: 10.0, longitude: 20.0));
    expect(positionMarker(tester)?.point, const LatLng(10.0, 20.0));
    expect(find.textContaining('REC • 1 pts'), findsOneWidget);
    location.stop();
  });
}
