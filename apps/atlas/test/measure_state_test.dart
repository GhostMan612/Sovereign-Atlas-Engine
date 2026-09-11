// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_geo/atlas_geo.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/measure/measure_state.dart';

AtlasCoordinate coord(double latitude, double longitude) {
  return AtlasCoordinate(latitude: latitude, longitude: longitude);
}

void main() {
  test('initial state is inactive and empty', () {
    final state = MeasureState();
    expect(state.isActive, isFalse);
    expect(state.isComplete, isFalse);
    expect(state.pointA, isNull);
    expect(state.pointB, isNull);
    expect(state.distanceKm, isNull);
    expect(state.bearingDeg, isNull);
    expect(state.displayDistance, isNull);
    expect(state.unit, MeasureUnit.meters);
  });

  test('begin with fix labels A as GPS', () {
    final state = MeasureState();
    state.begin(fixA: coord(10.0, 20.0), center: coord(0.0, 0.0));
    expect(state.isActive, isTrue);
    expect(state.isComplete, isFalse);
    expect(state.pointA, coord(10.0, 20.0));
    expect(state.pointASource, MeasurePointSource.fix);
    expect(state.pointB, isNull);
  });

  test('begin without fix falls back to labeled map center', () {
    final state = MeasureState();
    state.begin(center: coord(1.0, 2.0));
    expect(state.pointA, coord(1.0, 2.0));
    expect(state.pointASource, MeasurePointSource.mapCenter);
  });

  test('distance follows the TAC2-001 golden vector', () {
    final state = MeasureState();
    state.begin(center: coord(0.0, 0.0));
    state.setB(coord(0.0, 1.0));
    expect(state.isComplete, isTrue);
    expect(state.distanceKm, moreOrLessEquals(111.19508, epsilon: 1e-4));
  });

  test('bearing due east is 90 degrees', () {
    final state = MeasureState();
    state.begin(center: coord(0.0, 0.0));
    state.setB(coord(0.0, 1.0));
    expect(state.bearingDeg, moreOrLessEquals(90.0, epsilon: 1e-9));
  });

  test('coincident points give zero distance and undefined bearing', () {
    final state = MeasureState();
    state.begin(center: coord(10.0, 20.0));
    state.setB(coord(10.0, 20.0));
    expect(state.distanceKm, 0.0);
    expect(state.bearingDeg, isNull);
  });

  test('display distance converts through engine unit factors', () {
    final state = MeasureState();
    state.begin(center: coord(0.0, 0.0));
    state.setB(coord(0.0, 1.0));
    expect(state.displayDistance, moreOrLessEquals(111195.08, epsilon: 1e-3));
    state.setUnit(MeasureUnit.kilometers);
    expect(state.displayDistance, moreOrLessEquals(111.19508, epsilon: 1e-6));
    state.setUnit(MeasureUnit.miles);
    expect(state.displayDistance, moreOrLessEquals(69.0934, epsilon: 1e-3));
    state.setUnit(MeasureUnit.feet);
    expect(state.displayDistance, moreOrLessEquals(364813.255, epsilon: 0.1));
    state.setUnit(MeasureUnit.nauticalMiles);
    expect(state.displayDistance, moreOrLessEquals(60.0405, epsilon: 1e-3));
  });

  test('unit labels cover all five supported units', () {
    expect(MeasureState.labelOf(MeasureUnit.meters), 'm');
    expect(MeasureState.labelOf(MeasureUnit.kilometers), 'km');
    expect(MeasureState.labelOf(MeasureUnit.miles), 'mi');
    expect(MeasureState.labelOf(MeasureUnit.feet), 'ft');
    expect(MeasureState.labelOf(MeasureUnit.nauticalMiles), 'nmi');
  });

  test('clear resets points but keeps the chosen unit', () {
    final state = MeasureState();
    state.begin(center: coord(0.0, 0.0));
    state.setB(coord(0.0, 1.0));
    state.setUnit(MeasureUnit.miles);
    state.clear();
    expect(state.isActive, isFalse);
    expect(state.pointA, isNull);
    expect(state.pointB, isNull);
    expect(state.unit, MeasureUnit.miles);
  });

  test('re-entry resets the previous B', () {
    final state = MeasureState();
    state.begin(center: coord(0.0, 0.0));
    state.setB(coord(0.0, 1.0));
    state.begin(center: coord(5.0, 5.0));
    expect(state.pointA, coord(5.0, 5.0));
    expect(state.pointB, isNull);
    expect(state.isComplete, isFalse);
  });

  test('engine formatters pin the presentation contract', () {
    expect(AtlasGeoMath.formatBearing(90.0), 'BRG 090°');
    expect(AtlasGeoMath.formatDistance(0.5), '500 M');
  });

  test('change notifications fire on every mutation', () {
    final state = MeasureState();
    var calls = 0;
    state.addListener(() => calls++);
    state.begin(center: coord(0.0, 0.0));
    state.setB(coord(0.0, 1.0));
    state.setUnit(MeasureUnit.kilometers);
    state.clear();
    expect(calls, 4);
  });
}
