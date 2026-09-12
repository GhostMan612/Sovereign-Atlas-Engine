// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/go_to/go_to_state.dart';

AtlasCoordinate _coord(double latitude, double longitude) {
  return AtlasCoordinate(latitude: latitude, longitude: longitude);
}

void main() {
  test('fresh state has no active target', () {
    final state = GoToState();
    expect(state.isActive, isFalse);
    expect(state.target, isNull);
    expect(state.distanceKmTo(_coord(0.0, 0.0)), isNull);
    expect(state.bearingDegTo(_coord(0.0, 0.0)), isNull);
  });

  test('activate preserves id coordinate and label', () {
    final state = GoToState();
    state.activate(
      id: 'wp-000001',
      latitude: 45.0,
      longitude: -93.0,
      label: 'Alpha',
    );
    expect(state.isActive, isTrue);
    expect(state.target?.id, 'wp-000001');
    expect(state.target?.latitude, 45.0);
    expect(state.target?.longitude, -93.0);
    expect(state.target?.label, 'Alpha');
  });

  test('activate rejects invalid coordinates instead of inventing', () {
    final state = GoToState();
    expect(
      () => state.activate(
        id: 'wp-000001',
        latitude: 91.0,
        longitude: 0.0,
        label: 'Bad',
      ),
      throwsA(isA<AtlasRejectionException>()),
    );
    expect(state.isActive, isFalse);
  });

  test('distance follows the golden track-length vector', () {
    final state = GoToState();
    state.activate(id: 'w2', latitude: 0.0, longitude: 1.0, label: 'East');
    expect(state.distanceKmTo(_coord(0.0, 0.0)), closeTo(111.19508, 0.001));
  });

  test('bearing runs current position to target', () {
    final state = GoToState();
    state.activate(id: 'w2', latitude: 0.0, longitude: 1.0, label: 'East');
    expect(state.bearingDegTo(_coord(0.0, 0.0)), closeTo(90.0, 1e-9));
    expect(state.bearingDegTo(_coord(0.0, 1.0)), isNull);
  });

  test('coincident fix yields zero distance and undefined bearing', () {
    final state = GoToState();
    state.activate(id: 'w1', latitude: 45.0, longitude: -93.0, label: 'Here');
    expect(state.distanceKmTo(_coord(45.0, -93.0)), 0.0);
    expect(state.bearingDegTo(_coord(45.0, -93.0)), isNull);
  });

  test('missing fix yields unavailable navigation values', () {
    final state = GoToState();
    state.activate(id: 'w1', latitude: 45.0, longitude: -93.0, label: 'Far');
    expect(state.distanceKmTo(null), isNull);
    expect(state.bearingDegTo(null), isNull);
  });

  test('clear removes target and tolerates repeat calls', () {
    final state = GoToState();
    state.activate(id: 'w1', latitude: 45.0, longitude: -93.0, label: 'Far');
    state.clear();
    expect(state.isActive, isFalse);
    expect(state.target, isNull);
    state.clear();
    expect(state.isActive, isFalse);
  });

  test('reactivation replaces the previous target', () {
    final state = GoToState();
    state.activate(id: 'w1', latitude: 45.0, longitude: -93.0, label: 'One');
    state.activate(id: 'w2', latitude: 46.0, longitude: -94.0, label: 'Two');
    expect(state.target?.id, 'w2');
    expect(state.target?.label, 'Two');
  });
}
