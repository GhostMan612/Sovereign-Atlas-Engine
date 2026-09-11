// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';

import 'package:atlas_location/atlas_location.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const String kHeadingMethodChannel = 'com.sovereignatlas.atlas/heading';
const String kHeadingEventChannel = 'com.sovereignatlas.atlas/heading_stream';

enum HeadingAccuracy { unknown, high, medium, low, unreliable }

final class HeadingSample {
  const HeadingSample({
    required this.magnetic,
    required this.accuracy,
    required this.receivedAt,
    this.trueNorthDeg,
    this.sensorTimeNanos,
  });

  final AtlasHeading magnetic;
  final HeadingAccuracy accuracy;
  final DateTime receivedAt;
  final double? trueNorthDeg;
  final int? sensorTimeNanos;
}

abstract class HeadingSource {
  Future<bool> querySupported();
  Stream<HeadingSample> samples(DateTime Function() clock);
}

final class ChannelHeadingSource implements HeadingSource {
  ChannelHeadingSource({MethodChannel? methods, EventChannel? events})
      : _methods = methods ?? const MethodChannel(kHeadingMethodChannel),
        _events = events ?? const EventChannel(kHeadingEventChannel);

  final MethodChannel _methods;
  final EventChannel _events;

  @override
  Future<bool> querySupported() async {
    final raw = await _methods.invokeMethod<Object?>('status');
    if (raw is Map) return raw['supported'] is bool && (raw['supported'] as bool);
    return false;
  }

  @override
  Stream<HeadingSample> samples(DateTime Function() clock) {
    return _events.receiveBroadcastStream().map(
          (event) => parseSample(event, clock()),
        ).where((sample) => sample != null).cast<HeadingSample>();
  }

  static HeadingAccuracy parseAccuracy(Object? raw) {
    return switch (raw) {
      'high' => HeadingAccuracy.high,
      'medium' => HeadingAccuracy.medium,
      'low' => HeadingAccuracy.low,
      'unreliable' => HeadingAccuracy.unreliable,
      _ => HeadingAccuracy.unreliable,
    };
  }

  static HeadingSample? parseSample(Object? raw, DateTime receivedAt) {
    if (raw is! Map) return null;
    final magnetic = (raw['magnetic'] as num?)?.toDouble();
    if (magnetic == null) return null;
    return HeadingSample(
      magnetic: AtlasHeading(degrees: magnetic, source: 'rotation-vector'),
      accuracy: parseAccuracy(raw['accuracy']),
      receivedAt: receivedAt,
      trueNorthDeg: (raw['true'] as num?)?.toDouble(),
      sensorTimeNanos: (raw['timeNanos'] as num?)?.toInt(),
    );
  }
}

class HeadingService extends ChangeNotifier {
  HeadingService({
    required HeadingSource source,
    DateTime Function()? clock,
  })  : _headingSource = source,
        _clock = clock ?? DateTime.now;

  final HeadingSource _headingSource;
  final DateTime Function() _clock;

  bool? _supported;
  HeadingSample? _latest;
  Object? _lastError;
  StreamSubscription<HeadingSample>? _subscription;
  bool _disposed = false;

  bool? get supported => _supported;
  bool get unsupported => _supported == false;
  HeadingSample? get latest => _latest;
  Object? get lastError => _lastError;
  bool get active => _subscription != null;

  HeadingAccuracy get accuracy => _latest?.accuracy ?? HeadingAccuracy.unknown;

  double? get displayDeg {
    final sample = _latest;
    if (sample == null) return null;
    return sample.trueNorthDeg ?? sample.magnetic.degrees;
  }

  String get frameLabel =>
      _latest?.trueNorthDeg != null ? 'TRUE' : 'MAG';

  bool get dimmed =>
      accuracy == HeadingAccuracy.low ||
      accuracy == HeadingAccuracy.unreliable ||
      accuracy == HeadingAccuracy.unknown;

  Future<void> ensureStarted() async {
    try {
      _supported = await _headingSource.querySupported();
    } catch (error) {
      _fail(error);
      return;
    }
    if (_disposed || _supported != true) {
      notifyListeners();
      return;
    }
    await start();
  }

  Future<void> start() async {
    if (_subscription != null || _disposed) return;
    try {
      _supported = await _headingSource.querySupported();
    } catch (error) {
      _fail(error);
      return;
    }
    if (_disposed || _supported != true) {
      notifyListeners();
      return;
    }
    _subscription = _headingSource.samples(_clock).listen(
          _ingest,
          onError: _fail,
          onDone: () => _fail(StateError('Heading stream closed')),
        );
  }

  void stop() {
    _subscription?.cancel();
    _subscription = null;
  }

  @override
  void dispose() {
    _disposed = true;
    stop();
    super.dispose();
  }

  void _ingest(HeadingSample sample) {
    if (_disposed) return;
    _latest = sample;
    _lastError = null;
    notifyListeners();
  }

  void _fail(Object error) {
    if (_disposed) return;
    _lastError = error;
    notifyListeners();
  }
}
