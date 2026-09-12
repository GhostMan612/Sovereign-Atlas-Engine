// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:convert';
import 'dart:io';

import 'package:atlas_core/atlas_core.dart';
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
}
