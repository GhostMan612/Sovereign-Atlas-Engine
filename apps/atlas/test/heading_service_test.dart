// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/location/heading_service.dart';

import 'fake_heading_source.dart';

HeadingService supportedService(FakeHeadingSource fake) {
  final service = HeadingService(source: fake);
  addTearDown(service.dispose);
  return service;
}

void main() {
  test('initial state is idle with unknown accuracy', () {
    final service = HeadingService(source: FakeHeadingSource());
    expect(service.supported, isNull);
    expect(service.unsupported, isFalse);
    expect(service.latest, isNull);
    expect(service.displayDeg, isNull);
    expect(service.accuracy, HeadingAccuracy.unknown);
    expect(service.active, isFalse);
    expect(service.dimmed, isTrue);
  });

  test('unsupported sensor is explicit and never subscribes', () async {
    final fake = FakeHeadingSource()..supportedResult = false;
    final service = supportedService(fake);
    await service.ensureStarted();
    expect(service.unsupported, isTrue);
    expect(service.active, isFalse);
    expect(fake.samplesCalls, 0);
  });

  test('start subscribes exactly once', () async {
    final fake = FakeHeadingSource();
    final service = supportedService(fake);
    await service.start();
    await service.start();
    await service.start();
    expect(service.supported, isTrue);
    expect(service.active, isTrue);
    expect(fake.samplesCalls, 1);
  });

  test('ingested sample prefers true north', () async {
    final fake = FakeHeadingSource();
    final service = supportedService(fake);
    await service.start();
    fake.emit(testSample(magnetic: 90.0, trueNorth: 95.0));
    await Future<void>.delayed(Duration.zero);
    expect(service.displayDeg, 95.0);
    expect(service.frameLabel, 'TRUE');
    expect(service.accuracy, HeadingAccuracy.high);
    expect(service.dimmed, isFalse);
  });

  test('missing true north falls back to magnetic labeled MAG', () async {
    final fake = FakeHeadingSource();
    final service = supportedService(fake);
    await service.start();
    fake.emit(testSample(magnetic: 90.0, trueNorth: null));
    await Future<void>.delayed(Duration.zero);
    expect(service.displayDeg, 90.0);
    expect(service.frameLabel, 'MAG');
  });

  test('low and unreliable accuracy dim the compass', () async {
    final fake = FakeHeadingSource();
    final service = supportedService(fake);
    await service.start();
    fake.emit(testSample(accuracy: HeadingAccuracy.low));
    await Future<void>.delayed(Duration.zero);
    expect(service.dimmed, isTrue);
    fake.emit(testSample(accuracy: HeadingAccuracy.medium));
    await Future<void>.delayed(Duration.zero);
    expect(service.dimmed, isFalse);
    fake.emit(testSample(accuracy: HeadingAccuracy.unreliable));
    await Future<void>.delayed(Duration.zero);
    expect(service.dimmed, isTrue);
  });

  test('stream error surfaces without crashing', () async {
    final fake = FakeHeadingSource();
    final service = supportedService(fake);
    await service.start();
    fake.emitError(StateError('sensor lost'));
    await Future<void>.delayed(Duration.zero);
    expect(service.lastError, isStateError);
  });

  test('status failure surfaces without crashing', () async {
    final fake = FakeHeadingSource()..statusThrow = StateError('no binder');
    final service = supportedService(fake);
    await service.ensureStarted();
    expect(service.lastError, isStateError);
    expect(service.active, isFalse);
  });

  test('dispose cancels the subscription', () async {
    final fake = FakeHeadingSource();
    final service = HeadingService(source: fake);
    await service.start();
    expect(fake.samplesController.hasListener, isTrue);
    service.dispose();
    await Future<void>.delayed(Duration.zero);
    expect(fake.samplesController.hasListener, isFalse);
  });

  test('channel accuracy parsing maps every code', () {
    expect(ChannelHeadingSource.parseAccuracy('high'), HeadingAccuracy.high);
    expect(
      ChannelHeadingSource.parseAccuracy('medium'),
      HeadingAccuracy.medium,
    );
    expect(ChannelHeadingSource.parseAccuracy('low'), HeadingAccuracy.low);
    expect(
      ChannelHeadingSource.parseAccuracy('unreliable'),
      HeadingAccuracy.unreliable,
    );
    expect(
      ChannelHeadingSource.parseAccuracy('bogus'),
      HeadingAccuracy.unreliable,
    );
    expect(ChannelHeadingSource.parseAccuracy(null), HeadingAccuracy.unreliable);
  });

  test('channel sample parsing keeps typed values', () {
    final sample = ChannelHeadingSource.parseSample(const <String, Object?>{
      'magnetic': 270.5,
      'true': 272.0,
      'accuracy': 'medium',
      'timeNanos': 99,
    }, DateTime(2026));
    expect(sample?.magnetic.degrees, 270.5);
    expect(sample?.magnetic.source, 'rotation-vector');
    expect(sample?.trueNorthDeg, 272.0);
    expect(sample?.accuracy, HeadingAccuracy.medium);
    expect(sample?.sensorTimeNanos, 99);
    expect(sample?.receivedAt, DateTime(2026));
  });

  test('channel sample parsing rejects maps without magnetic', () {
    expect(
      ChannelHeadingSource.parseSample(
        const <String, Object?>{'accuracy': 'high'},
        DateTime(2026),
      ),
      isNull,
    );
    expect(ChannelHeadingSource.parseSample(null, DateTime(2026)), isNull);
  });
}
