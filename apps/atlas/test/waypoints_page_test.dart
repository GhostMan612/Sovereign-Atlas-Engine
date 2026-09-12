// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:atlas/field/field_journal.dart';
import 'package:atlas/main.dart';

Future<Directory> _tempDir() {
  return Directory.systemTemp.createTemp('waypoints_page_test_');
}

Future<Directory> _freshDir(WidgetTester tester) async {
  final dir = await tester.runAsync(() => _tempDir());
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

Future<void> pumpField(WidgetTester tester, FieldJournal journal) async {
  await tester.pumpWidget(AtlasApp(fieldJournal: journal));
  await tester.pumpAndSettle();
}

Future<void> longPressMap(
  WidgetTester tester, [
  Offset delta = const Offset(200.0, 200.0),
]) async {
  final origin = tester.getTopLeft(find.byType(FlutterMap));
  await tester.longPressAt(origin + delta);
  await tester.pumpAndSettle();
}

Future<void> openWaypoints(WidgetTester tester) async {
  await tester.tap(find.byTooltip('waypoints'));
  await tester.pumpAndSettle();
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

String mapLine(WidgetTester tester) {
  return tester
      .widget<Text>(find.textContaining('MAP lat'))
      .data!;
}

void main() {
  testWidgets('map long-press opens waypoint creation', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    await pumpField(tester, _journal(dir));
    await longPressMap(tester);
    expect(find.text('Save waypoint'), findsOneWidget);
    expect(
      find.byKey(const ValueKey<String>('waypoint-label')),
      findsOneWidget,
    );
  });

  testWidgets('save creates a marker from the pressed point', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    await pumpField(tester, journal);
    await longPressMap(tester);
    await tester.enterText(
      find.byKey(const ValueKey<String>('waypoint-label')),
      'Summit',
    );
    await tester.enterText(
      find.byKey(const ValueKey<String>('waypoint-note')),
      'Camp',
    );
    await tester.tap(find.byKey(const ValueKey<String>('waypoint-create-save')));
    await tester.pumpAndSettle();
    expect(journal.waypoints.length, 1);
    final record = journal.waypoints.single;
    expect(record.label, 'Summit');
    expect(record.note, 'Camp');
    expect(record.source, WaypointSource.mapSelected);
    final marker = findWaypointMarker(tester, record.id);
    expect(marker, isNotNull);
    expect(
      marker!.point,
      LatLng(record.latitude, record.longitude),
    );
    expect(marker.point, isNot(const LatLng(0.0, 0.0)));
  });

  testWidgets('cancel creates no waypoint', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    await pumpField(tester, journal);
    await longPressMap(tester);
    await tester.enterText(
      find.byKey(const ValueKey<String>('waypoint-label')),
      'Abandoned',
    );
    await tester.tap(
      find.byKey(const ValueKey<String>('waypoint-create-cancel')),
    );
    await tester.pumpAndSettle();
    expect(journal.waypoints, isEmpty);
    expect(find.text('Save waypoint'), findsNothing);
  });

  testWidgets('list shows waypoint and detail edits label', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(
      latitude: 45.0,
      longitude: -93.0,
      label: 'Alpha',
      note: 'First',
    );
    await pumpField(tester, journal);
    await openWaypoints(tester);
    expect(find.text('Alpha'), findsOneWidget);
    expect(find.textContaining('45.0000'), findsWidgets);
    await tester.tap(find.text('Alpha'));
    await tester.pumpAndSettle();
    expect(find.textContaining('map_selected'), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey<String>('waypoint-edit-label')),
      'Beta',
    );
    await tester.tap(find.byKey(const ValueKey<String>('waypoint-save')));
    await tester.pumpAndSettle();
    expect(find.text('Beta'), findsOneWidget);
    expect(journal.lookup('wp-000001')?.label, 'Beta');
    expect(findWaypointMarker(tester, 'wp-000001'), isNotNull);
  });

  testWidgets('delete removes marker list item and journal record', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Gone');
    await pumpField(tester, journal);
    expect(findWaypointMarker(tester, 'wp-000001'), isNotNull);
    await openWaypoints(tester);
    await tester.tap(find.text('Gone'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey<String>('waypoint-delete')));
    await tester.pumpAndSettle();
    expect(find.textContaining('No waypoints yet'), findsOneWidget);
    expect(journal.waypoints, isEmpty);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(findWaypointMarker(tester, 'wp-000001'), isNull);
  });

  testWidgets('selecting a waypoint never moves the camera', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Still');
    await pumpField(tester, journal);
    final before = mapLine(tester);
    await openWaypoints(tester);
    await tester.tap(find.text('Still'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey<String>('waypoint-save')));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(mapLine(tester), before);
  });

  testWidgets('measure tap capture still works beside waypoints', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    journal.create(latitude: 45.0, longitude: -93.0, label: 'Nearby');
    await pumpField(tester, journal);
    await tester.tap(find.byTooltip('measure'));
    await tester.pumpAndSettle();
    final origin = tester.getTopLeft(find.byType(FlutterMap));
    await tester.tapAt(origin + const Offset(120.0, 16.0));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.textContaining('Distance:'), findsOneWidget);
    expect(findWaypointMarker(tester, 'wp-000001'), isNotNull);
  });

  testWidgets('corrupt journal surfaces an error instead of crashing', (
    WidgetTester tester,
  ) async {
    final dir = await _freshDir(tester);
    final journal = _journal(dir);
    await tester.runAsync(() async {
      final journalDir = Directory('${dir.path}/$kFieldJournalDir');
      await journalDir.create(recursive: true);
      await File('${journalDir.path}/$kFieldJournalFile')
          .writeAsString('broken{{{', flush: true);
    });
    await tester.runAsync(() => journal.restore());
    await pumpField(tester, journal);
    await openWaypoints(tester);
    expect(find.textContaining('Journal unavailable'), findsOneWidget);
    expect(find.textContaining('No waypoints yet'), findsOneWidget);
  });
}
