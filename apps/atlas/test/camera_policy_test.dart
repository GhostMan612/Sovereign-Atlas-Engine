// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:atlas_location/atlas_location.dart';
import 'package:atlas_map/atlas_map.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:atlas/location/location_service.dart';
import 'package:atlas/map/camera_policy.dart';

AtlasLocationFix policyFix({
  double latitude = 10.0,
  double longitude = 20.0,
}) {
  return AtlasLocationFix(
    position: AtlasCoordinate(latitude: latitude, longitude: longitude),
    at: 1,
    source: 'gps',
  );
}

void main() {
  group('startup intent', () {
    test('valid fix yields a local camera at the startup zoom', () {
      final intent = startupIntent(
        status: AtlasLocationStatus.valid,
        fix: policyFix(),
      );
      expect(intent, isNotNull);
      expect(intent!.center.latitude, 10.0);
      expect(intent.center.longitude, 20.0);
      expect(intent.zoom, kStartupLocalZoom);
      expect(intent.validate().isValid, isTrue);
    });

    test('stale fix is not a startup target', () {
      expect(
        startupIntent(
          status: AtlasLocationStatus.stale,
          fix: policyFix(),
        ),
        isNull,
      );
    });

    test('unavailable statuses yield no startup camera', () {
      for (final status in [
        AtlasLocationStatus.notRequested,
        AtlasLocationStatus.denied,
        AtlasLocationStatus.permanentlyDenied,
        AtlasLocationStatus.servicesDisabled,
        AtlasLocationStatus.acquiring,
        AtlasLocationStatus.error,
      ]) {
        expect(
          startupIntent(status: status, fix: policyFix()),
          isNull,
          reason: 'status $status must not produce a camera',
        );
      }
    });

    test('missing fix yields no startup camera', () {
      expect(
        startupIntent(status: AtlasLocationStatus.valid, fix: null),
        isNull,
      );
    });
  });

  group('my-location intent', () {
    test('valid fix moves to zoom 15', () {
      final intent = myLocationIntent(
        status: AtlasLocationStatus.valid,
        fix: policyFix(),
        bearing: 0.0,
      );
      expect(intent, isNotNull);
      expect(intent!.center.latitude, 10.0);
      expect(intent.center.longitude, 20.0);
      expect(intent.zoom, 15.0);
      expect(kMyLocationZoom, 15.0);
      expect(intent.validate().isValid, isTrue);
    });

    test('bearing passes through so the map never rotates', () {
      final intent = myLocationIntent(
        status: AtlasLocationStatus.valid,
        fix: policyFix(),
        bearing: 37.0,
      );
      expect(intent!.bearing, 37.0);
    });

    test('stale fix is not a my-location target', () {
      expect(
        myLocationIntent(
          status: AtlasLocationStatus.stale,
          fix: policyFix(),
          bearing: 0.0,
        ),
        isNull,
      );
    });

    test('no valid fix means no camera jump', () {
      expect(
        myLocationIntent(
          status: AtlasLocationStatus.acquiring,
          fix: null,
          bearing: 0.0,
        ),
        isNull,
      );
      expect(
        myLocationIntent(
          status: AtlasLocationStatus.valid,
          fix: null,
          bearing: 0.0,
        ),
        isNull,
      );
    });
  });

  group('camera validation safety', () {
    test('malformed serialized state is rejected, never applied', () {
      expect(
        () => AtlasCameraState.parse('not-a-camera'),
        throwsA(isA<AtlasRejectionException>()),
      );
      expect(
        () => AtlasCameraState.parse('10|20|99|0|0'),
        throwsA(isA<AtlasRejectionException>()),
      );
    });

    test('out-of-range zoom fails engine validation', () {
      final state = AtlasCameraState(
        center: const AtlasCoordinate(latitude: 10.0, longitude: 20.0),
        zoom: 99.0,
        bearing: 0.0,
        pitch: 0.0,
      );
      expect(state.validate().isValid, isFalse);
    });
  });
}
