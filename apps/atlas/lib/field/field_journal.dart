// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_location/atlas_location.dart';
import 'package:atlas_tactical/atlas_tactical.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

const int kFieldJournalVersion = 1;
const String kFieldJournalDir = 'field_data';
const String kFieldJournalFile = 'journal.json';

enum WaypointSource { mapSelected, gpsRecorded }

String waypointSourceName(WaypointSource source) {
  return switch (source) {
    WaypointSource.mapSelected => 'map_selected',
    WaypointSource.gpsRecorded => 'gps_recorded',
  };
}

WaypointSource? waypointSourceFromName(Object? raw) {
  return switch (raw) {
    'map_selected' => WaypointSource.mapSelected,
    'gps_recorded' => WaypointSource.gpsRecorded,
    _ => null,
  };
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

  final String id;
  final double latitude;
  final double longitude;
  final int createdAt;
  final String label;
  final String note;
  final WaypointSource source;

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

final class StoredTrack {
  const StoredTrack({
    required this.id,
    required this.createdAt,
    this.points = const [],
    required this.source,
  });

  final String id;
  final int createdAt;
  final List<StoredWaypoint> points;
  final WaypointSource source;

  int get pointCount => points.length;

  AtlasTrack toTrack() {
    return AtlasTrack(
      id: AtlasId(id),
      points: [
        for (final point in points)
          AtlasWaypoint(
            id: AtlasId(point.id),
            position: AtlasCoordinate(
              latitude: point.latitude,
              longitude: point.longitude,
            ),
            createdAt: point.createdAt,
            label: point.label,
            note: point.note,
          ),
      ],
      createdAt: createdAt,
    );
  }

  Map<String, Object?> toJson() {
    return {
      'id': id,
      'created_at': createdAt,
      'source': waypointSourceName(source),
      'points': [for (final point in points) point.toJson()],
    };
  }

  static StoredTrack? tryParse(Object? raw) {
    if (raw is! Map) return null;
    final id = raw['id'];
    if (id is! String || !AtlasIds.check(id).isValid) return null;
    final createdAt = raw['created_at'];
    if (createdAt is! int) return null;
    final source = waypointSourceFromName(raw['source']);
    if (source == null) return null;
    final rawPoints = raw['points'];
    if (rawPoints is! List) return null;
    final points = <StoredWaypoint>[];
    for (final entry in rawPoints) {
      final point = StoredWaypoint.tryParse(entry);
      if (point == null) return null;
      points.add(point);
    }
    return StoredTrack(
      id: id,
      createdAt: createdAt,
      points: points,
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
  final Map<String, StoredTrack> _tracks = {};
  int _sequence = 0;
  int _trackSequence = 0;
  Object? _lastError;
  int _skipped = 0;

  List<StoredWaypoint> get waypoints => _waypoints.values.toList();
  StoredWaypoint? lookup(String id) => _waypoints[id];
  List<StoredTrack> get tracks => _tracks.values.toList();
  StoredTrack? lookupTrack(String id) => _tracks[id];
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

  StoredTrack? saveTrack({required List<AtlasLocationFix> fixes}) {
    if (fixes.isEmpty) return null;
    final id = _allocateTrackId();
    final points = <StoredWaypoint>[
      for (var i = 0; i < fixes.length; i++)
        StoredWaypoint(
          id: '$id-p${(i + 1).toString().padLeft(4, '0')}',
          latitude: fixes[i].position.latitude,
          longitude: fixes[i].position.longitude,
          createdAt: fixes[i].at,
          source: WaypointSource.gpsRecorded,
        ),
    ];
    final record = StoredTrack(
      id: id,
      createdAt: fixes.first.at,
      points: points,
      source: WaypointSource.gpsRecorded,
    );
    _tracks[id] = record;
    _lastError = null;
    unawaited(persist());
    notifyListeners();
    return record;
  }

  bool removeTrack(String id) {
    if (_tracks.remove(id) == null) return false;
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
      'tracks': [for (final record in _tracks.values) record.toJson()],
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
    final loadedTracks = <String, StoredTrack>{};
    var maxTrackSequence = 0;
    final rawTracks = decoded['tracks'];
    if (rawTracks != null) {
      if (rawTracks is! List) {
        _lastError = StateError('field journal has no track list');
        notifyListeners();
        return;
      }
      for (final raw in rawTracks) {
        final record = StoredTrack.tryParse(raw);
        if (record == null || loadedTracks.containsKey(record.id)) {
          skipped += 1;
          continue;
        }
        loadedTracks[record.id] = record;
        final sequence = _trackSequenceOf(record.id);
        if (sequence > maxTrackSequence) maxTrackSequence = sequence;
      }
    }
    _waypoints
      ..clear()
      ..addAll(loaded);
    _tracks
      ..clear()
      ..addAll(loadedTracks);
    _sequence = maxSequence;
    _trackSequence = maxTrackSequence;
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

  String _allocateTrackId() {
    var candidate = _trackSequence + 1;
    while (_tracks.containsKey(_trackIdFor(candidate))) {
      candidate += 1;
    }
    _trackSequence = candidate;
    return _trackIdFor(candidate);
  }

  static String _idFor(int sequence) {
    return 'wp-${sequence.toString().padLeft(6, '0')}';
  }

  static int _sequenceOf(String id) {
    if (!id.startsWith('wp-')) return 0;
    return int.tryParse(id.substring(3)) ?? 0;
  }

  static String _trackIdFor(int sequence) {
    return 'trk-${sequence.toString().padLeft(6, '0')}';
  }

  static int _trackSequenceOf(String id) {
    if (!id.startsWith('trk-')) return 0;
    return int.tryParse(id.substring(4)) ?? 0;
  }
}
