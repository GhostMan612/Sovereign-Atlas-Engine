// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_location/atlas_location.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/location/location_service.dart';
import 'package:atlas/track/track_recorder.dart';

import 'fake_location_source.dart';

AtlasLocationFix _fix(double latitude, double longitude, int at) {
  return AtlasLocationFix(
    position: AtlasCoordinate(latitude: latitude, longitude: longitude),
    at: at,
    source: 'gps',
  );
}

Future<LocationService> _startedService(FakeLocationSource fake) async {
  fake.queryResult = grantedQuery;
  final service = LocationService(locationSource: fake);
  addTearDown(service.dispose);
  await service.start();
  return service;
}

TrackRecorder _recorder(LocationService service) {
  final recorder = TrackRecorder(locationService: service);
  addTearDown(recorder.dispose);
  return recorder;
}

void main() {
  test('initial state is idle with no points', () {
    final fake = FakeLocationSource();
    final service = LocationService(locationSource: fake);
    addTearDown(service.dispose);
    final recorder = _recorder(service);
    expect(recorder.state, TrackRecorderState.idle);
    expect(recorder.isRecording, isFalse);
    expect(recorder.pointCount, 0);
    expect(recorder.points, isEmpty);
    expect(recorder.stop(), isEmpty);
    expect(recorder.isRecording, isFalse);
  });

  test('start enters recording and double start attaches once', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    final recorder = _recorder(service);
    var notifications = 0;
    recorder.addListener(() => notifications++);
    recorder.start();
    recorder.start();
    expect(recorder.isRecording, isTrue);
    expect(recorder.state, TrackRecorderState.recording);
    fake.emit(_fix(45.0, -93.0, 1000));
    await Future<void>.delayed(Duration.zero);
    expect(recorder.pointCount, 1);
    expect(notifications, greaterThanOrEqualTo(2));
  });

  test('start seeds the current valid fix', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    fake.emit(_fix(45.0, -93.0, 1000));
    await Future<void>.delayed(Duration.zero);
    final recorder = _recorder(service);
    recorder.start();
    expect(recorder.pointCount, 1);
    expect(recorder.points.single.position.latitude, 45.0);
  });

  test('valid events append in order with full fidelity', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    final recorder = _recorder(service);
    recorder.start();
    fake.emit(_fix(45.0, -93.0, 1000));
    fake.emit(_fix(45.1, -93.1, 2000));
    fake.emit(_fix(45.1, -93.1, 3000));
    await Future<void>.delayed(Duration.zero);
    expect(recorder.pointCount, 3);
    expect(recorder.points[0].at, 1000);
    expect(recorder.points[1].at, 2000);
    expect(recorder.points[2].at, 3000);
  });

  test('identical re-notification is not a new point', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    final recorder = _recorder(service);
    recorder.start();
    final fix = _fix(45.0, -93.0, 1000);
    fake.emit(fix);
    fake.emit(fix);
    await Future<void>.delayed(Duration.zero);
    expect(recorder.pointCount, 1);
  });

  test('stale flip without new fix appends nothing', () async {
    final fake = FakeLocationSource();
    fake.queryResult = grantedQuery;
    final service = LocationService(
      locationSource: fake,
      staleAfter: const Duration(milliseconds: 50),
    );
    addTearDown(service.dispose);
    await service.start();
    final recorder = _recorder(service);
    recorder.start();
    fake.emit(_fix(45.0, -93.0, 1000));
    await Future<void>.delayed(Duration.zero);
    expect(recorder.pointCount, 1);
    await Future<void>.delayed(const Duration(milliseconds: 120));
    expect(service.status, AtlasLocationStatus.stale);
    expect(recorder.pointCount, 1);
  });

  test('non-valid states record nothing', () async {
    final fake = FakeLocationSource()
      ..queryResult = const AtlasLocationQuery(
        permission: AtlasLocationPermission.denied,
        servicesEnabled: true,
      );
    final service = LocationService(locationSource: fake);
    addTearDown(service.dispose);
    final recorder = _recorder(service);
    recorder.start();
    await service.refreshStatus();
    expect(service.status, AtlasLocationStatus.denied);
    expect(recorder.pointCount, 0);
    expect(recorder.points, isEmpty);
  });

  test('acquiring service yields no fabricated points', () async {
    final fake = FakeLocationSource();
    fake.queryResult = grantedQuery;
    final service = LocationService(locationSource: fake);
    addTearDown(service.dispose);
    final recorder = _recorder(service);
    recorder.start();
    await service.refreshStatus();
    expect(service.status, AtlasLocationStatus.acquiring);
    expect(recorder.pointCount, 0);
  });

  test('stop returns ordered fixes and detaches', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    final recorder = _recorder(service);
    recorder.start();
    fake.emit(_fix(45.0, -93.0, 1000));
    fake.emit(_fix(45.1, -93.1, 2000));
    await Future<void>.delayed(Duration.zero);
    final fixes = recorder.stop();
    expect(recorder.isRecording, isFalse);
    expect(fixes.length, 2);
    expect(fixes[0].at, 1000);
    expect(fixes[1].at, 2000);
    expect(recorder.pointCount, 0);
    fake.emit(_fix(45.2, -93.2, 3000));
    await Future<void>.delayed(Duration.zero);
    expect(recorder.pointCount, 0);
    expect(service.latestFix?.position.latitude, 45.2);
  });

  test('zero-point stop returns empty', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    final recorder = _recorder(service);
    recorder.start();
    expect(recorder.stop(), isEmpty);
    expect(recorder.isRecording, isFalse);
  });

  test('dispose detaches without crashing', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    final recorder = TrackRecorder(locationService: service);
    recorder.start();
    recorder.dispose();
    fake.emit(_fix(45.0, -93.0, 1000));
    await Future<void>.delayed(Duration.zero);
    expect(recorder.pointCount, 0);
  });

  test('stream error surfaces on service while recorder stays safe', () async {
    final fake = FakeLocationSource();
    final service = await _startedService(fake);
    final recorder = _recorder(service);
    recorder.start();
    fake.emitError(StateError('sensor lost'));
    await Future<void>.delayed(Duration.zero);
    expect(service.status, AtlasLocationStatus.error);
    expect(recorder.pointCount, 0);
    fake.emit(_fix(45.0, -93.0, 1000));
    await Future<void>.delayed(Duration.zero);
    expect(recorder.pointCount, 1);
  });
}
