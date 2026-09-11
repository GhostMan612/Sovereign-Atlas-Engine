// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';

import 'package:atlas_location/atlas_location.dart';

import 'package:atlas/location/heading_service.dart';

HeadingSample testSample({
  double magnetic = 90.0,
  double? trueNorth = 95.0,
  HeadingAccuracy accuracy = HeadingAccuracy.high,
}) {
  return HeadingSample(
    magnetic: AtlasHeading(degrees: magnetic, source: 'rotation-vector'),
    accuracy: accuracy,
    receivedAt: DateTime(2026),
    trueNorthDeg: trueNorth,
    sensorTimeNanos: 1,
  );
}

final class FakeHeadingSource implements HeadingSource {
  bool supportedResult = true;
  Object? statusThrow;
  int statusCalls = 0;
  int samplesCalls = 0;

  final StreamController<HeadingSample> samplesController =
      StreamController<HeadingSample>.broadcast();

  @override
  Future<bool> querySupported() async {
    statusCalls++;
    final error = statusThrow;
    if (error != null) throw error;
    return supportedResult;
  }

  @override
  Stream<HeadingSample> samples(DateTime Function() clock) {
    samplesCalls++;
    return samplesController.stream;
  }

  void emit(HeadingSample sample) => samplesController.add(sample);

  void emitError(Object error) => samplesController.addError(error);
}
