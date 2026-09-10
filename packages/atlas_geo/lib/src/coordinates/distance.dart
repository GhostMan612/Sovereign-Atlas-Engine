// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_core/atlas_core.dart';
import '../normalization/angles.dart';
import 'coordinate.dart';

abstract final class AtlasGeoMath {

  static const double referenceRadiusKm = 6371.0088;

  static double haversineKm(
    AtlasCoordinate from,
    AtlasCoordinate to, {
    double radiusKm = referenceRadiusKm,
  }) {
    final lat1 = _radians(from.latitude);
    final lat2 = _radians(to.latitude);
    final dLat = _radians(to.latitude - from.latitude);
    final dLon = _radians(to.longitude - from.longitude);
    final h = math.pow(math.sin(dLat / 2), 2) +
        math.cos(lat1) * math.cos(lat2) * math.pow(math.sin(dLon / 2), 2);
    return 2 * radiusKm * math.asin(math.sqrt(h.toDouble()));
  }

  static double initialBearingDeg(AtlasCoordinate from, AtlasCoordinate to) {
    if (from.latitude == to.latitude && from.longitude == to.longitude) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'COINCIDENT_POINTS',
          'Bearing is undefined for identical points (provisional; BRG-004 open).',
        ),
      );
    }
    final lat1 = _radians(from.latitude);
    final lat2 = _radians(to.latitude);
    final dLon = _radians(to.longitude - from.longitude);
    final x = math.sin(dLon) * math.cos(lat2);
    final y = math.cos(lat1) * math.sin(lat2) -
        math.sin(lat1) * math.cos(lat2) * math.cos(dLon);

    return AtlasAngles.normalizeBearingDeg(math.atan2(x, y) * 180.0 / math.pi);
  }

  static AtlasCoordinate destinationPoint(
    AtlasCoordinate origin,
    double distanceKm,
    double bearingDeg, {
    double radiusKm = referenceRadiusKm,
  }) {
    final angular = distanceKm / radiusKm;
    final bearing =
        AtlasAngles.normalizeBearingDeg(bearingDeg) * math.pi / 180.0;
    final lat1 = _radians(origin.latitude);
    final lon1 = _radians(origin.longitude);
    final lat2 = math.asin(
      math.sin(lat1) * math.cos(angular) +
          math.cos(lat1) * math.sin(angular) * math.cos(bearing),
    );
    final lon2 = lon1 +
        math.atan2(
          math.sin(bearing) * math.sin(angular) * math.cos(lat1),
          math.cos(angular) - math.sin(lat1) * math.sin(lat2),
        );
    return AtlasCoordinate(
      latitude: lat2 * 180.0 / math.pi,
      longitude: AtlasAngles.normalizeSignedDeg(lon2 * 180.0 / math.pi),
    );
  }

  static String formatDistance(double km) {
    if (km < 1.0) {
      return '${(km * 1000).round()} M';
    }
    return '${km.toStringAsFixed(2)} KM';
  }

  static String formatBearing(double degrees) {
    return 'BRG ${degrees.round().toString().padLeft(3, '0')}°';
  }

  static double _radians(double degrees) => degrees * math.pi / 180.0;
}
