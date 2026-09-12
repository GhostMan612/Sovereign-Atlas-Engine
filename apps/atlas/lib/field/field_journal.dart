// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_tactical/atlas_tactical.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

const int kFieldJournalVersion = 1;
const String kFieldJournalDir = 'field_data';
const String kFieldJournalFile = 'journal.json';

enum WaypointSource { mapSelected }

String waypointSourceName(WaypointSource source) {
  return switch (source) {
    WaypointSource.mapSelected => 'map_selected',
  };
}

WaypointSource? waypointSourceFromName(Object? raw) {
  return raw == 'map_selected' ? WaypointSource.mapSelected : null;
}

final class StoredWaypoint {
  const StoredWaypoint({
    required this.id,
    required this.latitude,
    required this.longitude,
    required this.createdAt,
    this.label = '',
    this.note = '',
    required this.source,
  });

  factory StoredWaypoint.fromWaypoint(
    AtlasWaypoint waypoint,
    WaypointSource source,
  ) {
    return StoredWaypoint(
      id: waypoint.id.value,
      latitude: waypoint.position.latitude,
      longitude: waypoint.position.longitude,
      createdAt: waypoint.createdAt,
      label: waypoint.label,
      note: waypoint.note,
      source: source,
    );
  }

  final String id;
  final double latitude;
  final double longitude;
  final int createdAt;
  final String label;
  final String note;
  final WaypointSource source;

  AtlasWaypoint toWaypoint() {
    return AtlasWaypoint(
      id: AtlasId(id),
      position: AtlasCoordinate(latitude: latitude, longitude: longitude),
      createdAt: createdAt,
      label: label,
      note: note,
    );
  }

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'latitude': latitude,
      'longitude': longitude,
      'created_at': createdAt,
      'label': label,
      'note': note,
      'source': waypointSourceName(source),
    };
  }

  static StoredWaypoint? tryParse(Object? raw) {
    if (raw is! Map) return null;
    final id = raw['id'];
    if (id is! String || !AtlasIds.check(id).isValid) return null;
    final latitude = (raw['latitude'] as num?)?.toDouble();
    final longitude = (raw['longitude'] as num?)?.toDouble();
    if (latitude == null || longitude == null) return null;
    if (!AtlasCoordinates.validate(latitude, longitude).isValid) return null;
    final createdAt = raw['created_at'];
    if (createdAt is! int) return null;
    final label = raw['label'];
    final note = raw['note'];
    final source = waypointSourceFromName(raw['source']);
    if (source == null) return null;
    return StoredWaypoint(
      id: id,
      latitude: latitude,
      longitude: longitude,
      createdAt: createdAt,
      label: label is String ? label : '',
      note: note is String ? note : '',
      source: source,
    );
  }
}

final class FieldJournal extends ChangeNotifier {
  FieldJournal({
    Future<Directory> Function()? directoryProvider,
    int Function()? clock,
  })  : _directoryProvider = directoryProvider ?? getApplicationDocumentsDirectory,
        _clock = clock ?? (() => DateTime.now().millisecondsSinceEpoch);

  final Future<Directory> Function() _directoryProvider;
  final int Function() _clock;
  final Map<String, StoredWaypoint> _waypoints = {};
  int _sequence = 0;
  Object? _lastError;
  int _skipped = 0;

  List<StoredWaypoint> get waypoints => _waypoints.values.toList();
  StoredWaypoint? lookup(String id) => _waypoints[id];
  Object? get lastError => _lastError;
  int get skippedCount => _skipped;

  StoredWaypoint create({
    required double latitude,
    required double longitude,
    String label = '',
    String note = '',
    WaypointSource source = WaypointSource.mapSelected,
  }) {
    final point = AtlasCoordinates.checked(latitude, longitude);
    final id = _allocateId();
    final record = StoredWaypoint(
      id: id,
      latitude: point.latitude,
      longitude: point.longitude,
      createdAt: _clock(),
      label: label,
      note: note,
      source: source,
    );
    _waypoints[id] = record;
    _lastError = null;
    unawaited(persist());
    notifyListeners();
    return record;
  }

  bool updateLabel(String id, String label) {
    final current = _waypoints[id];
    if (current == null) return false;
    _waypoints[id] = StoredWaypoint(
      id: current.id,
      latitude: current.latitude,
      longitude: current.longitude,
      createdAt: current.createdAt,
      label: label,
      note: current.note,
      source: current.source,
    );
    _lastError = null;
    unawaited(persist());
    notifyListeners();
    return true;
  }

  bool updateNote(String id, String note) {
    final current = _waypoints[id];
    if (current == null) return false;
    _waypoints[id] = StoredWaypoint(
      id: current.id,
      latitude: current.latitude,
      longitude: current.longitude,
      createdAt: current.createdAt,
      label: current.label,
      note: note,
      source: current.source,
    );
    _lastError = null;
    unawaited(persist());
    notifyListeners();
    return true;
  }

  bool remove(String id) {
    if (_waypoints.remove(id) == null) return false;
    _lastError = null;
    unawaited(persist());
    notifyListeners();
    return true;
  }

  Future<void> persist() async {
    try {
      final dir = await _journalDir();
      await File('${dir.path}/$kFieldJournalFile')
          .writeAsString(jsonEncode(_envelope()), flush: true);
    } catch (error) {
      _lastError = error;
      notifyListeners();
    }
  }

  Future<void> restore() async {
    try {
      await _restoreUnsafe();
    } catch (error) {
      _lastError = error;
      notifyListeners();
    }
  }

  Map<String, Object?> _envelope() {
    return {
      'version': kFieldJournalVersion,
      'waypoints': [for (final record in _waypoints.values) record.toJson()],
    };
  }

  Future<Directory> _journalDir() async {
    final base = await _directoryProvider();
    final dir = Directory('${base.path}/$kFieldJournalDir');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  Future<void> _restoreUnsafe() async {
    final dir = await _journalDir();
    final file = File('${dir.path}/$kFieldJournalFile');
    if (!await file.exists()) {
      _lastError = null;
      notifyListeners();
      return;
    }
    late final Object? decoded;
    try {
      decoded = jsonDecode(await file.readAsString());
    } catch (error) {
      _lastError = error;
      notifyListeners();
      return;
    }
    if (decoded is! Map || decoded['version'] != kFieldJournalVersion) {
      _lastError = StateError('unsupported field journal version');
      notifyListeners();
      return;
    }
    if (decoded['waypoints'] is! List) {
      _lastError = StateError('field journal has no waypoint list');
      notifyListeners();
      return;
    }
    final loaded = <String, StoredWaypoint>{};
    var skipped = 0;
    var maxSequence = 0;
    for (final raw in (decoded['waypoints'] as List)) {
      final record = StoredWaypoint.tryParse(raw);
      if (record == null || loaded.containsKey(record.id)) {
        skipped += 1;
        continue;
      }
      loaded[record.id] = record;
      final sequence = _sequenceOf(record.id);
      if (sequence > maxSequence) maxSequence = sequence;
    }
    _waypoints
      ..clear()
      ..addAll(loaded);
    _sequence = maxSequence;
    _skipped = skipped;
    _lastError = null;
    notifyListeners();
  }

  String _allocateId() {
    var candidate = _sequence + 1;
    while (_waypoints.containsKey(_idFor(candidate))) {
      candidate += 1;
    }
    _sequence = candidate;
    return _idFor(candidate);
  }

  static String _idFor(int sequence) {
    return 'wp-${sequence.toString().padLeft(6, '0')}';
  }

  static int _sequenceOf(String id) {
    if (!id.startsWith('wp-')) return 0;
    return int.tryParse(id.substring(3)) ?? 0;
  }
}
