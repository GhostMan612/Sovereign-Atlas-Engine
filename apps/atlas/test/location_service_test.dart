// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/location/location_service.dart';

import 'fake_location_source.dart';

LocationService grantedService(
  FakeLocationSource fake, {
  Duration? staleAfter,
}) {
  fake.queryResult = grantedQuery;
  final service =
      LocationService(locationSource: fake, staleAfter: staleAfter);
  addTearDown(service.dispose);
  return service;
}

void main() {
  test('initial state is no-fix not-requested', () {
    final service = LocationService(locationSource: FakeLocationSource());
    expect(service.latestFix, isNull);
    expect(service.recenterTarget, isNull);
    expect(service.isStale, isFalse);
    expect(service.status, AtlasLocationStatus.notRequested);
  });

  test('permission-not-requested survives refresh', () async {
    final service = LocationService(locationSource: FakeLocationSource());
    await service.refreshStatus();
    expect(service.permission, AtlasLocationPermission.notRequested);
    expect(service.status, AtlasLocationStatus.notRequested);
  });

  test('denied state is explicit', () async {
    final fake = FakeLocationSource()
      ..queryResult = const AtlasLocationQuery(
        permission: AtlasLocationPermission.denied,
        servicesEnabled: true,
      );
    final service = LocationService(locationSource: fake);
    await service.refreshStatus();
    expect(service.status, AtlasLocationStatus.denied);
    expect(service.recenterTarget, isNull);
  });

  test('permanently-denied is distinct from denied', () async {
    final fake = FakeLocationSource()
      ..queryResult = const AtlasLocationQuery(
        permission: AtlasLocationPermission.permanentlyDenied,
        servicesEnabled: true,
      );
    final service = LocationService(locationSource: fake);
    await service.refreshStatus();
    expect(service.status, AtlasLocationStatus.permanentlyDenied);
    expect(service.status, isNot(AtlasLocationStatus.denied));
  });

  test('granted without services is services-disabled', () async {
    final fake = FakeLocationSource()
      ..queryResult = const AtlasLocationQuery(
        permission: AtlasLocationPermission.granted,
        servicesEnabled: false,
      );
    final service = LocationService(locationSource: fake);
    await service.refreshStatus();
    expect(service.status, AtlasLocationStatus.servicesDisabled);
  });

  test('granted with services and no fix is acquiring', () async {
    final fake = FakeLocationSource();
    final service = grantedService(fake);
    await service.start();
    expect(service.status, AtlasLocationStatus.acquiring);
    expect(service.recenterTarget, isNull);
  });

  test('emitted fix becomes valid latest with accuracy', () async {
    final fake = FakeLocationSource();
    final service = grantedService(fake);
    await service.start();
    fake.emit(testFix());
    await Future<void>.delayed(Duration.zero);
    expect(service.status, AtlasLocationStatus.valid);
    expect(service.latestFix?.accuracyMeters, 4.5);
    expect(service.recenterTarget?.latitude, 10.0);
    expect(service.recenterTarget?.longitude, 20.0);
  });

  test('null accuracy is preserved, never defaulted', () async {
    final fake = FakeLocationSource();
    final service = grantedService(fake);
    await service.start();
    fake.emit(testFix(accuracy: null));
    await Future<void>.delayed(Duration.zero);
    expect(service.status, AtlasLocationStatus.valid);
    expect(service.latestFix?.accuracyMeters, isNull);
  });

  test('fix older than the bound reads stale', () async {
    final fake = FakeLocationSource();
    final service = grantedService(
      fake,
      staleAfter: const Duration(milliseconds: 50),
    );
    await service.start();
    fake.emit(testFix());
    await Future<void>.delayed(Duration.zero);
    expect(service.status, AtlasLocationStatus.valid);
    await Future<void>.delayed(const Duration(milliseconds: 120));
    expect(service.isStale, isTrue);
    expect(service.status, AtlasLocationStatus.stale);
    expect(service.latestFix, isNotNull);
  });

  test('stream error surfaces as error state', () async {
    final fake = FakeLocationSource();
    final service = grantedService(fake);
    await service.start();
    fake.emitError(StateError('sensor lost'));
    await Future<void>.delayed(Duration.zero);
    expect(service.status, AtlasLocationStatus.error);
    expect(service.lastError, isStateError);
  });

  test('query failure surfaces as error state', () async {
    final fake = FakeLocationSource()..queryThrow = StateError('no binder');
    final service = LocationService(locationSource: fake);
    await service.refreshStatus();
    expect(service.status, AtlasLocationStatus.error);
  });

  test('denied permission never subscribes', () async {
    final fake = FakeLocationSource()
      ..queryResult = const AtlasLocationQuery(
        permission: AtlasLocationPermission.denied,
        servicesEnabled: true,
      )
      ..requestResult = const AtlasLocationQuery(
        permission: AtlasLocationPermission.denied,
        servicesEnabled: true,
      );
    final service = LocationService(locationSource: fake);
    await service.ensureActive();
    expect(service.status, AtlasLocationStatus.denied);
    expect(fake.fixesCalls, 0);
  });

  test('ensureActive requests then starts exactly once', () async {
    final fake = FakeLocationSource()..seedResult = testFix();
    final service = LocationService(locationSource: fake);
    await service.ensureActive();
    fake.queryResult = grantedQuery;
    await service.ensureActive();
    expect(fake.requestCalls, 1);
    expect(fake.fixesCalls, 1);
    expect(service.status, AtlasLocationStatus.valid);
  });

  test('start is idempotent across repeated calls', () async {
    final fake = FakeLocationSource();
    final service = grantedService(fake);
    await service.start();
    await service.start();
    await service.start();
    expect(fake.fixesCalls, 1);
  });

  test('dispose cancels the subscription', () async {
    final fake = FakeLocationSource()..queryResult = grantedQuery;
    final service = LocationService(locationSource: fake);
    await service.start();
    expect(fake.fixesController.hasListener, isTrue);
    service.dispose();
    await Future<void>.delayed(Duration.zero);
    expect(fake.fixesController.hasListener, isFalse);
  });

  test('channel fix parsing keeps typed values', () {
    final fix = ChannelLocationSource.parseFix(const <String, Object?>{
      'latitude': 51.5,
      'longitude': -0.12,
      'accuracy': 12,
      'speed': 1.5,
      'bearing': 90,
      'time': 123456789,
      'provider': 'gps',
    }, 0);
    expect(fix?.position.latitude, 51.5);
    expect(fix?.position.longitude, -0.12);
    expect(fix?.accuracyMeters, 12.0);
    expect(fix?.speedMetersPerSecond, 1.5);
    expect(fix?.headingDeg, 90.0);
    expect(fix?.at, 123456789);
    expect(fix?.source, 'gps');
  });

  test('channel fix parsing rejects maps without coordinates', () {
    expect(
      ChannelLocationSource.parseFix(const <String, Object?>{'accuracy': 3}, 7),
      isNull,
    );
    expect(ChannelLocationSource.parseFix(null, 7), isNull);
  });
}
