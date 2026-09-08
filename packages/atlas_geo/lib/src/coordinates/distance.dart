// Sovereign Atlas Engine — atlas_geo
// Great-circle distance, initial bearing, and source-observed display formats.
//
// Contracts: ATLAS-GEO-DIST-001, ATLAS-GEO-BRG-001 (behavior-contracts.md).
// - Haversine R6371 / atan2-normalized 0–359: SOURCE-VERIFIED method (F-02).
// - Reference radius is a parameter defaulting to 6371.0088; fixture tolerance
//   absorbs the R6371-vs-R6371.0088 delta (determinism-policy).
// - Bearing convention 0° = true north, clockwise, [0, 360): PROPOSED.
// - Coincident-point bearing: THROWS AtlasRejectionException(COINCIDENT_POINTS)
//   as an explicit provisional until the contract decides (BRG-004 BLOCKED).
//   Status: PROVISIONAL — NOT ATLAS-NORMATIVE (0.5A Ruling 3d/4; no fake 0°). Ownership:
//   ATLAS-GEO-BRG-001 contract area; BRG-004 remains the open item (no new DEC).
// Phase 0.5 slice. Depends on atlas_core (rejection) and dart:math only.

import 'dart:math' as math;

import '../../../../atlas_core/lib/atlas_core.dart';
import '../normalization/angles.dart';
import 'coordinate.dart';

/// Deterministic spherical-approximation geodesy.
abstract final class AtlasGeoMath {
  /// Mean-earth radius default (km). Overridable per call for fixture honesty.
  static const double referenceRadiusKm = 6371.0088;

  /// Great-circle distance via haversine. Inputs MUST be validated coordinates.
  static double haversineKm(
    AtlasCoordinate from,
    AtlasCoordinate to, {
    double radiusKm = referenceRadiusKm,
  }) {
    final lat1 = _radians(from.latitude);
    final lat2 = _radians(to.latitude);
    final dLat = _radians(to.latitude - from.latitude);
    final dLon = _radians(to.longitude - from.longitude);
    final h =
        math.pow(math.sin(dLat / 2), 2) +
        math.cos(lat1) * math.cos(lat2) * math.pow(math.sin(dLon / 2), 2);
    return 2 * radiusKm * math.asin(math.sqrt(h.toDouble()));
  }

  /// Initial bearing in [0, 360). Throws [AtlasRejectionException]
  /// (`COINCIDENT_POINTS`) for identical points.
  ///
  /// PROVISIONAL — NOT ATLAS-NORMATIVE (0.5A Ruling 4). BRG-004 stays BLOCKED;
  /// no silent 0 is returned and no contract is settled by this throw.
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
    final y =
        math.cos(lat1) * math.sin(lat2) -
        math.sin(lat1) * math.cos(lat2) * math.cos(dLon);
    // Bearing-domain normalization (ATLAS-NORMATIVE extraction; identical math
    // to the Phase 0 inline expression over atan2's (-180, 180] range).
    return AtlasAngles.normalizeBearingDeg(math.atan2(x, y) * 180.0 / math.pi);
  }

  /// Destination point from [origin] travelling [distanceKm] on [bearingDeg].
  ///
  /// PROVISIONAL — NOT ATLAS-NORMATIVE (contract-owned measurement op, no DEC
  /// assigned; generalizes the ring generator's helper). Spherical
  /// approximation consistent with [haversineKm]; longitude wrapped to
  /// (−180, 180] as pure math output (not validation policy).
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
    final lon2 =
        lon1 +
        math.atan2(
          math.sin(bearing) * math.sin(angular) * math.cos(lat1),
          math.cos(angular) - math.sin(lat1) * math.sin(lat2),
        );
    return AtlasCoordinate(
      latitude: lat2 * 180.0 / math.pi,
      longitude: AtlasAngles.normalizeSignedDeg(lon2 * 180.0 / math.pi),
    );
  }

  /// Display format `<int> M` below 1 km else `<0.00> KM` (SOURCE-VERIFIED F-02:
  /// `RULER 850 M / 1.25 KM`; fixture DIST-002 expects `111.20 KM`).
  static String formatDistance(double km) {
    if (km < 1.0) {
      return '${(km * 1000).round()} M';
    }
    return '${km.toStringAsFixed(2)} KM';
  }

  /// Display format `BRG 042°` (SOURCE-VERIFIED F-02).
  static String formatBearing(double degrees) {
    return 'BRG ${degrees.round().toString().padLeft(3, '0')}°';
  }

  static double _radians(double degrees) => degrees * math.pi / 180.0;
}
