// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';

import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_location/atlas_location.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:latlong2/latlong.dart';

const int kLocationStaleAfterSeconds = 30;
const String kLocationMethodChannel = 'com.sovereignatlas.atlas/location';
const String kLocationEventChannel = 'com.sovereignatlas.atlas/location_stream';

enum AtlasLocationPermission { notRequested, denied, permanentlyDenied, granted }

enum AtlasLocationStatus {
  notRequested,
  denied,
  permanentlyDenied,
  servicesDisabled,
  acquiring,
  valid,
  stale,
  error,
}

final class AtlasLocationQuery {
  const AtlasLocationQuery({
    required this.permission,
    required this.servicesEnabled,
  });

  final AtlasLocationPermission permission;
  final bool servicesEnabled;
}

abstract class AtlasLocationSource {
  Future<AtlasLocationQuery> queryStatus();
  Future<AtlasLocationQuery> requestPermission();
  Future<bool> openAppSettings();
  Future<AtlasLocationFix?> lastKnownFix();
  Stream<AtlasLocationFix> fixes();
}

final class ChannelLocationSource implements AtlasLocationSource {
  ChannelLocationSource({MethodChannel? methods, EventChannel? events})
      : _methods = methods ?? const MethodChannel(kLocationMethodChannel),
        _events = events ?? const EventChannel(kLocationEventChannel);

  final MethodChannel _methods;
  final EventChannel _events;

  @override
  Future<AtlasLocationQuery> queryStatus() async {
    final raw = await _methods.invokeMethod<Object?>('getStatus');
    return _parseQuery(raw);
  }

  @override
  Future<AtlasLocationQuery> requestPermission() async {
    final raw = await _methods.invokeMethod<Object?>('requestPermission');
    return _parseQuery(raw);
  }

  @override
  Future<bool> openAppSettings() async {
    final raw = await _methods.invokeMethod<Object?>('openAppSettings');
    return raw is bool && raw;
  }

  @override
  Future<AtlasLocationFix?> lastKnownFix() async {
    final raw = await _methods.invokeMethod<Object?>('getLastKnownFix');
    return parseFix(raw, DateTime.now().millisecondsSinceEpoch);
  }

  @override
  Stream<AtlasLocationFix> fixes() {
    return _events.receiveBroadcastStream().map(
          (event) => parseFix(event, DateTime.now().millisecondsSinceEpoch),
        ).where((fix) => fix != null).cast<AtlasLocationFix>();
  }

  AtlasLocationQuery _parseQuery(Object? raw) {
    if (raw is Map) {
      return AtlasLocationQuery(
        permission: parsePermission(raw['permission']),
        servicesEnabled: raw['servicesEnabled'] is bool &&
            (raw['servicesEnabled'] as bool),
      );
    }
    return const AtlasLocationQuery(
      permission: AtlasLocationPermission.notRequested,
      servicesEnabled: false,
    );
  }

  static AtlasLocationPermission parsePermission(Object? raw) {
    return switch (raw) {
      'granted' => AtlasLocationPermission.granted,
      'denied' => AtlasLocationPermission.denied,
      'permanentlyDenied' => AtlasLocationPermission.permanentlyDenied,
      _ => AtlasLocationPermission.notRequested,
    };
  }

  static AtlasLocationFix? parseFix(Object? raw, int fallbackAt) {
    if (raw is! Map) return null;
    final latitude = (raw['latitude'] as num?)?.toDouble();
    final longitude = (raw['longitude'] as num?)?.toDouble();
    if (latitude == null || longitude == null) return null;
    return AtlasLocationFix(
      position: AtlasCoordinate(latitude: latitude, longitude: longitude),
      at: (raw['time'] as num?)?.toInt() ?? fallbackAt,
      accuracyMeters: (raw['accuracy'] as num?)?.toDouble(),
      speedMetersPerSecond: (raw['speed'] as num?)?.toDouble(),
      headingDeg: (raw['bearing'] as num?)?.toDouble(),
      source: (raw['provider'] as String?) ?? '',
    );
  }
}

class LocationService extends ChangeNotifier {
  LocationService({
    required AtlasLocationSource locationSource,
    DateTime Function()? clock,
    Duration? staleAfter,
  })  : _source = locationSource,
        _clock = clock ?? DateTime.now,
        _staleAfter = staleAfter ??
            const Duration(seconds: kLocationStaleAfterSeconds);

  final AtlasLocationSource _source;
  final DateTime Function() _clock;
  final Duration _staleAfter;

  AtlasLocationPermission _permission = AtlasLocationPermission.notRequested;
  bool _servicesEnabled = false;
  AtlasLocationFix? _latestFix;
  DateTime? _fixReceivedAt;
  Object? _lastError;
  StreamSubscription<AtlasLocationFix>? _subscription;
  Timer? _staleTimer;
  bool _disposed = false;

  AtlasLocationPermission get permission => _permission;
  bool get servicesEnabled => _servicesEnabled;
  AtlasLocationFix? get latestFix => _latestFix;
  DateTime? get fixReceivedAt => _fixReceivedAt;
  Object? get lastError => _lastError;
  Duration get staleAfter => _staleAfter;

  bool get isStale {
    final received = _fixReceivedAt;
    if (received == null || _latestFix == null) return false;
    return _clock().difference(received) >= _staleAfter;
  }

  AtlasLocationStatus get status {
    if (_lastError != null) return AtlasLocationStatus.error;
    switch (_permission) {
      case AtlasLocationPermission.notRequested:
        return AtlasLocationStatus.notRequested;
      case AtlasLocationPermission.denied:
        return AtlasLocationStatus.denied;
      case AtlasLocationPermission.permanentlyDenied:
        return AtlasLocationStatus.permanentlyDenied;
      case AtlasLocationPermission.granted:
        break;
    }
    if (!_servicesEnabled) return AtlasLocationStatus.servicesDisabled;
    if (_latestFix == null) return AtlasLocationStatus.acquiring;
    return isStale ? AtlasLocationStatus.stale : AtlasLocationStatus.valid;
  }

  LatLng? get recenterTarget {
    final fix = _latestFix;
    if (fix == null) return null;
    return LatLng(fix.position.latitude, fix.position.longitude);
  }

  Future<void> refreshStatus() async {
    try {
      _applyQuery(await _source.queryStatus());
    } catch (error) {
      _fail(error);
    }
  }

  Future<void> requestPermission() async {
    try {
      _applyQuery(await _source.requestPermission());
    } catch (error) {
      _fail(error);
    }
  }

  Future<bool> openAppSettings() async {
    try {
      return await _source.openAppSettings();
    } catch (error) {
      _fail(error);
      return false;
    }
  }

  Future<void> ensureActive() async {
    await refreshStatus();
    if (_disposed) return;
    if (_permission == AtlasLocationPermission.notRequested) {
      await requestPermission();
      if (_disposed) return;
    }
    if (_permission == AtlasLocationPermission.granted) {
      await start();
    }
  }

  Future<void> start() async {
    if (_subscription != null || _disposed) return;
    await refreshStatus();
    if (_disposed || _permission != AtlasLocationPermission.granted) return;
    try {
      final seed = await _source.lastKnownFix();
      if (_disposed) return;
      if (seed != null) _ingest(seed);
      _subscription = _source.fixes().listen(
            _ingest,
            onError: _fail,
            onDone: () => _fail(StateError('Location stream closed')),
          );
    } catch (error) {
      _fail(error);
    }
  }

  void stop() {
    _subscription?.cancel();
    _subscription = null;
    _staleTimer?.cancel();
    _staleTimer = null;
  }

  @override
  void dispose() {
    _disposed = true;
    stop();
    super.dispose();
  }

  void _applyQuery(AtlasLocationQuery query) {
    if (_disposed) return;
    _permission = query.permission;
    _servicesEnabled = query.servicesEnabled;
    _lastError = null;
    notifyListeners();
  }

  void _ingest(AtlasLocationFix fix) {
    if (_disposed) return;
    _latestFix = fix;
    _fixReceivedAt = _clock();
    _servicesEnabled = true;
    _lastError = null;
    notifyListeners();
    _armStaleTimer(fix);
  }

  void _fail(Object error) {
    if (_disposed) return;
    _lastError = error;
    notifyListeners();
  }

  void _armStaleTimer(AtlasLocationFix fix) {
    _staleTimer?.cancel();
    _staleTimer = null;
    final received = _fixReceivedAt;
    if (received == null) return;
    final remaining = _staleAfter - _clock().difference(received);
    if (remaining <= Duration.zero) return;
    _staleTimer = Timer(remaining, () {
      if (!_disposed && identical(_latestFix, fix)) notifyListeners();
    });
  }
}
