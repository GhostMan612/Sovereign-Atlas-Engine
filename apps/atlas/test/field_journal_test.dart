// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:convert';
import 'dart:io';

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_location/atlas_location.dart';
import 'package:atlas_tactical/atlas_tactical.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/field/field_journal.dart';

Future<Directory> _tempDir() {
  return Directory.systemTemp.createTemp('field_journal_test_');
}

FieldJournal _journal(Directory dir, {int Function()? clock}) {
  return FieldJournal(
    directoryProvider: () async => dir,
    clock: clock,
  );
}

Future<void> _writeRaw(Directory dir, String content) async {
  final journal = Directory('${dir.path}/$kFieldJournalDir');
  await journal.create(recursive: true);
  await File('${journal.path}/$kFieldJournalFile')
      .writeAsString(content, flush: true);
}

Future<String> _readRaw(Directory dir) {
  return File('${dir.path}/$kFieldJournalDir/$kFieldJournalFile')
      .readAsString();
}

void main() {
  test('create stores fields with deterministic first id', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = FieldJournal(
      directoryProvider: () async => dir,
      clock: () => 1700000000000,
    );
    final record = service.create(
      latitude: 45.0,
      longitude: -93.0,
      label: 'Summit',
      note: 'Camp',
    );
    expect(record.id, 'wp-000001');
    expect(record.latitude, 45.0);
    expect(record.longitude, -93.0);
    expect(record.createdAt, 1700000000000);
    expect(record.label, 'Summit');
    expect(record.note, 'Camp');
    expect(record.source, WaypointSource.mapSelected);
  });

  test('ids increment and never repeat', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = FieldJournal(directoryProvider: () async => dir);
    final ids = <String>{
      for (var i = 0; i < 5; i++)
        service.create(latitude: 45.0 + i * 0.001, longitude: -93.0).id,
    };
    expect(ids.length, 5);
    expect(ids.first, 'wp-000001');
    expect(ids.last, 'wp-000005');
  });

  test('edit label and note preserve identity fields', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = FieldJournal(
      directoryProvider: () async => dir,
      clock: () => 7,
    );
    final record =
        service.create(latitude: 45.0, longitude: -93.0, label: 'A');
    expect(service.updateLabel(record.id, 'B'), isTrue);
    expect(service.updateNote(record.id, 'note'), isTrue);
    final updated = service.lookup(record.id)!;
    expect(updated.label, 'B');
    expect(updated.note, 'note');
    expect(updated.id, record.id);
    expect(updated.latitude, 45.0);
    expect(updated.longitude, -93.0);
    expect(updated.createdAt, 7);
  });

  test('edit and delete of missing ids report false', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = FieldJournal(directoryProvider: () async => dir);
    expect(service.updateLabel('wp-000009', 'x'), isFalse);
    expect(service.updateNote('wp-000009', 'x'), isFalse);
    expect(service.remove('wp-000009'), isFalse);
  });

  test('multiple waypoints keep insertion order', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = FieldJournal(directoryProvider: () async => dir);
    service.create(latitude: 45.0, longitude: -93.0, label: 'first');
    service.create(latitude: 45.1, longitude: -93.1, label: 'second');
    final labels = [for (final w in service.waypoints) w.label];
    expect(labels, ['first', 'second']);
  });

  test('invalid coordinates throw instead of inventing', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = FieldJournal(directoryProvider: () async => dir);
    expect(
      () => service.create(latitude: 91.0, longitude: 0.0),
      throwsA(isA<AtlasRejectionException>()),
    );
    expect(
      () => service.create(latitude: 0.0, longitude: 200.0),
      throwsA(isA<AtlasRejectionException>()),
    );
    expect(service.waypoints, isEmpty);
  });

  test('persist and restore round-trip every field', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir, clock: () => 42);
    service.create(
      latitude: 45.0,
      longitude: -93.0,
      label: 'Summit',
      note: 'Camp',
    );
    await service.persist();
    final restored = _journal(dir);
    await restored.restore();
    expect(restored.lastError, isNull);
    expect(restored.waypoints.length, 1);
    final record = restored.waypoints.single;
    expect(record.id, 'wp-000001');
    expect(record.latitude, 45.0);
    expect(record.longitude, -93.0);
    expect(record.createdAt, 42);
    expect(record.label, 'Summit');
    expect(record.note, 'Camp');
    expect(record.source, WaypointSource.mapSelected);
  });

  test('sequence continues past restored ids', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    service.create(latitude: 45.0, longitude: -93.0);
    service.create(latitude: 45.1, longitude: -93.1);
    await service.persist();
    final restored = _journal(dir);
    await restored.restore();
    final next = restored.create(latitude: 45.2, longitude: -93.2);
    expect(next.id, 'wp-000003');
  });

  test('missing journal restores to empty without error', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    await service.restore();
    expect(service.lastError, isNull);
    expect(service.waypoints, isEmpty);
  });

  test('malformed journal fails safe and preserves the file', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    await _writeRaw(dir, 'not-json{{{');
    final service = _journal(dir);
    await service.restore();
    expect(service.lastError, isNotNull);
    expect(service.waypoints, isEmpty);
    expect(await _readRaw(dir), 'not-json{{{');
    service.create(latitude: 45.0, longitude: -93.0);
    await service.persist();
    final reread = _journal(dir);
    await reread.restore();
    expect(reread.waypoints.length, 1);
  });

  test('unsupported version fails safe without data', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    await _writeRaw(
      dir,
      jsonEncode({'version': 99, 'waypoints': []}),
    );
    final service = _journal(dir);
    await service.restore();
    expect(service.lastError, isNotNull);
    expect(service.waypoints, isEmpty);
  });

  test('invalid records are skipped, valid records kept', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    await _writeRaw(
      dir,
      jsonEncode({
        'version': 1,
        'waypoints': [
          {
            'id': 'wp-000001',
            'latitude': 45.0,
            'longitude': -93.0,
            'created_at': 1,
            'label': 'good',
            'note': '',
            'source': 'map_selected',
          },
          {
            'id': 'wp-000002',
            'latitude': 91.0,
            'longitude': 0.0,
            'created_at': 2,
            'label': 'bad-coords',
            'note': '',
            'source': 'map_selected',
          },
          {
            'id': 'wp-000003',
            'latitude': 45.0,
            'longitude': -93.0,
            'created_at': 3,
            'label': 'bad-source',
            'note': '',
            'source': 'satellite_uplink',
          },
        ],
      }),
    );
    final service = _journal(dir);
    await service.restore();
    expect(service.lastError, isNull);
    expect(service.waypoints.length, 1);
    expect(service.waypoints.single.id, 'wp-000001');
    expect(service.skippedCount, 2);
  });

  test('duplicate ids keep the first record', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    Map<String, Object?> entry(String label) {
      return {
        'id': 'wp-000001',
        'latitude': 45.0,
        'longitude': -93.0,
        'created_at': 1,
        'label': label,
        'note': '',
        'source': 'map_selected',
      };
    }

    await _writeRaw(
      dir,
      jsonEncode({
        'version': 1,
        'waypoints': [entry('first'), entry('second')],
      }),
    );
    final service = _journal(dir);
    await service.restore();
    expect(service.waypoints.length, 1);
    expect(service.waypoints.single.label, 'first');
    expect(service.skippedCount, 1);
  });

  test('deleted waypoints do not resurrect after restart', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    final record = service.create(latitude: 45.0, longitude: -93.0);
    await service.persist();
    expect(service.remove(record.id), isTrue);
    await service.persist();
    final restored = _journal(dir);
    await restored.restore();
    expect(restored.waypoints, isEmpty);
  });

  test('repeated persist writes no duplicates', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    service.create(latitude: 45.0, longitude: -93.0);
    await service.persist();
    await service.persist();
    final restored = _journal(dir);
    await restored.restore();
    expect(restored.waypoints.length, 1);
  });

  test('journal record schema shape is pinned', () {
    const record = StoredWaypoint(
      id: 'wp-000007',
      latitude: 45.5,
      longitude: -93.5,
      createdAt: 9,
      label: 'L',
      note: 'N',
      source: WaypointSource.mapSelected,
    );
    expect(
      record.toJson().keys.toSet(),
      {
        'id',
        'latitude',
        'longitude',
        'created_at',
        'label',
        'note',
        'source',
      },
    );
    expect(record.toJson()['source'], 'map_selected');
    final roundTripped = StoredWaypoint.tryParse(record.toJson());
    expect(roundTripped?.id, 'wp-000007');
    expect(roundTripped?.latitude, 45.5);
    expect(roundTripped?.longitude, -93.5);
    expect(roundTripped?.createdAt, 9);
    expect(roundTripped?.label, 'L');
    expect(roundTripped?.note, 'N');
    expect(roundTripped?.source, WaypointSource.mapSelected);
  });

  List<AtlasLocationFix> trackFixes() {
    return [
      AtlasLocationFix(
        position: AtlasCoordinate(latitude: 0.0, longitude: 0.0),
        at: 1000,
        source: 'gps',
      ),
      AtlasLocationFix(
        position: AtlasCoordinate(latitude: 0.0, longitude: 1.0),
        at: 2000,
        source: 'gps',
      ),
    ];
  }

  test('saveTrack stores fields with deterministic first id', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    final record = service.saveTrack(fixes: trackFixes())!;
    expect(record.id, 'trk-000001');
    expect(record.createdAt, 1000);
    expect(record.pointCount, 2);
    expect(record.source, WaypointSource.gpsRecorded);
    expect(record.points[0].id, 'trk-000001-p0001');
    expect(record.points[1].id, 'trk-000001-p0002');
    expect(record.points[0].createdAt, 1000);
    expect(record.points[1].createdAt, 2000);
    expect(record.points[0].source, WaypointSource.gpsRecorded);
    expect(service.tracks.length, 1);
  });

  test('saveTrack with no fixes persists nothing', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    expect(service.saveTrack(fixes: const []), isNull);
    expect(service.tracks, isEmpty);
    await service.persist();
    expect(await _readRaw(dir), contains('"tracks":[]'));
  });

  test('tracks persist and restore with waypoints intact', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    service.create(latitude: 45.0, longitude: -93.0, label: 'Base');
    await service.persist();
    service.saveTrack(fixes: trackFixes());
    await service.persist();
    final restored = _journal(dir);
    await restored.restore();
    expect(restored.lastError, isNull);
    expect(restored.waypoints.length, 1);
    expect(restored.waypoints.single.id, 'wp-000001');
    expect(restored.tracks.length, 1);
    final record = restored.tracks.single;
    expect(record.id, 'trk-000001');
    expect(record.createdAt, 1000);
    expect(record.pointCount, 2);
    expect(record.points[0].latitude, 0.0);
    expect(record.points[1].longitude, 1.0);
    expect(record.points[1].createdAt, 2000);
    expect(record.source, WaypointSource.gpsRecorded);
  });

  test('track ids continue past restored tracks', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    service.saveTrack(fixes: trackFixes());
    await service.persist();
    final restored = _journal(dir);
    await restored.restore();
    final next = restored.saveTrack(fixes: trackFixes())!;
    expect(next.id, 'trk-000002');
  });

  test('removeTrack removes only that track', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    service.create(latitude: 45.0, longitude: -93.0);
    service.saveTrack(fixes: trackFixes());
    expect(service.removeTrack('trk-000001'), isTrue);
    expect(service.removeTrack('trk-000001'), isFalse);
    expect(service.tracks, isEmpty);
    expect(service.waypoints.length, 1);
  });

  test('deleted tracks do not resurrect after restart', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    service.saveTrack(fixes: trackFixes());
    await service.persist();
    expect(service.removeTrack('trk-000001'), isTrue);
    await service.persist();
    final restored = _journal(dir);
    await restored.restore();
    expect(restored.tracks, isEmpty);
  });

  test('invalid tracks are skipped, valid tracks kept', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    Map<String, Object?> trackEntry(String id, double lon) {
      return {
        'id': id,
        'created_at': 1,
        'source': 'gps_recorded',
        'points': [
          {
            'id': '$id-p0001',
            'latitude': 45.0,
            'longitude': lon,
            'created_at': 1,
            'label': '',
            'note': '',
            'source': 'gps_recorded',
          },
        ],
      };
    }

    await _writeRaw(
      dir,
      jsonEncode({
        'version': 1,
        'waypoints': [],
        'tracks': [trackEntry('trk-000001', -93.0), trackEntry('trk-000002', 190.0)],
      }),
    );
    final service = _journal(dir);
    await service.restore();
    expect(service.lastError, isNull);
    expect(service.tracks.length, 1);
    expect(service.tracks.single.id, 'trk-000001');
    expect(service.skippedCount, 1);
  });

  test('duplicate track ids keep the first record', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    Map<String, Object?> trackEntry(int createdAt) {
      return {
        'id': 'trk-000001',
        'created_at': createdAt,
        'source': 'gps_recorded',
        'points': [],
      };
    }

    await _writeRaw(
      dir,
      jsonEncode({
        'version': 1,
        'waypoints': [],
        'tracks': [trackEntry(1), trackEntry(2)],
      }),
    );
    final service = _journal(dir);
    await service.restore();
    expect(service.tracks.length, 1);
    expect(service.tracks.single.createdAt, 1);
    expect(service.skippedCount, 1);
  });

  test('journal without tracks key loads waypoints cleanly', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    await _writeRaw(
      dir,
      jsonEncode({
        'version': 1,
        'waypoints': [
          {
            'id': 'wp-000001',
            'latitude': 45.0,
            'longitude': -93.0,
            'created_at': 1,
            'label': '',
            'note': '',
            'source': 'map_selected',
          },
        ],
      }),
    );
    final service = _journal(dir);
    await service.restore();
    expect(service.lastError, isNull);
    expect(service.waypoints.length, 1);
    expect(service.tracks, isEmpty);
  });

  test('tracks present but malformed fails safe with memory kept', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    await _writeRaw(
      dir,
      jsonEncode({
        'version': 1,
        'waypoints': [
          {
            'id': 'wp-000001',
            'latitude': 45.0,
            'longitude': -93.0,
            'created_at': 1,
            'label': '',
            'note': '',
            'source': 'map_selected',
          },
        ],
        'tracks': [],
      }),
    );
    final service = _journal(dir);
    await service.restore();
    expect(service.lastError, isNull);
    expect(service.waypoints.length, 1);
    await _writeRaw(
      dir,
      jsonEncode({
        'version': 1,
        'waypoints': [],
        'tracks': {'id': 'trk-000001'},
      }),
    );
    await service.restore();
    expect(service.lastError, isNotNull);
    expect(service.waypoints.length, 1);
    expect(service.tracks, isEmpty);
  });

  test('materialized track length matches the golden vector', () async {
    final dir = await _tempDir();
    addTearDown(() => dir.delete(recursive: true));
    final service = _journal(dir);
    final record = service.saveTrack(fixes: trackFixes())!;
    final AtlasTrack track = record.toTrack();
    expect(track.id.value, 'trk-000001');
    expect(track.pointCount, 2);
    expect(track.lengthMeters, closeTo(111195.08, 0.01));
    expect(track.createdAt, 1000);
  });

  test('track record schema shape is pinned', () {
    const record = StoredTrack(
      id: 'trk-000007',
      createdAt: 9,
      source: WaypointSource.gpsRecorded,
    );
    expect(
      record.toJson().keys.toSet(),
      {'id', 'created_at', 'source', 'points'},
    );
    expect(record.toJson()['source'], 'gps_recorded');
    expect(record.pointCount, 0);
    final roundTripped = StoredTrack.tryParse(record.toJson());
    expect(roundTripped?.id, 'trk-000007');
    expect(roundTripped?.createdAt, 9);
    expect(roundTripped?.source, WaypointSource.gpsRecorded);
    expect(roundTripped?.points, isEmpty);
  });
}
