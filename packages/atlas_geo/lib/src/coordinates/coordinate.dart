// Sovereign Atlas Engine — atlas_geo
// AtlasCoordinate: WGS84 angular position value object + validation.
//
// Contracts: ATLAS-COORD-001 (coordinate-contract.md), ATLAS-VALID-001.
// - Default CRS WGS84: PROPOSED (consistent with SRC-A WGS84-direct F-05).
// - Range guards lat ±90 / lng ±180: SOURCE-VERIFIED precedent (camera guards
//   F-03), generalized here as ATLAS-NORMATIVE.
// - Longitude normalization: DEC-004 OPEN — out-of-range longitude REJECTS
//   (strict reading of the guard precedent). No silent wrapping is performed.
// - Altitude/elevation: DEC-003 OPEN — this type is 2D only.
// Phase 0.5 slice. Depends only on atlas_core. No I/O, no platform APIs.

import 'package:atlas_core/atlas_core.dart';

/// Earth-referenced angular position in decimal degrees.
///
/// Instances are expected to be validated before construction via
/// [AtlasCoordinates.validate]. The const constructor performs no checks so
/// validated data stays cheap to build; unvalidated data MUST go through
/// [AtlasCoordinates.checked].
final class AtlasCoordinate {
  const AtlasCoordinate({
    required this.latitude,
    required this.longitude,
    this.crs = AtlasCoordinate.wgs84,
  });

  /// Default coordinate reference system identifier (PROPOSED per contract).
  static const String wgs84 = 'WGS84';

  final double latitude;
  final double longitude;

  /// CRS identifier. Only [wgs84] is accepted by [AtlasCoordinates.validate];
  /// unknown values reject (coordinate-contract §2, ATLAS-NORMATIVE).
  final String crs;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasCoordinate &&
          latitude == other.latitude &&
          longitude == other.longitude &&
          crs == other.crs;

  @override
  int get hashCode => Object.hash(latitude, longitude, crs);

  @override
  String toString() => 'AtlasCoordinate($latitude, $longitude, $crs)';
}

/// Validation entry points for [AtlasCoordinate].
abstract final class AtlasCoordinates {
  /// Validates raw angular values without constructing a coordinate.
  ///
  /// Rejection categories: `NON_FINITE` (NaN/±Infinity), `OUT_OF_RANGE`
  /// (latitude outside ±90, longitude outside ±180), `UNSUPPORTED_CRS`.
  static AtlasValidation validate(
    double latitude,
    double longitude, {
    String crs = AtlasCoordinate.wgs84,
  }) {
    if (!latitude.isFinite || !longitude.isFinite) {
      return const AtlasValidation.invalid(
        AtlasRejection('NON_FINITE', 'Coordinates must be finite numbers.'),
      );
    }
    if (latitude < -90.0 || latitude > 90.0) {
      return const AtlasValidation.invalid(
        AtlasRejection('OUT_OF_RANGE', 'Latitude must be within [-90, 90].'),
      );
    }
    if (longitude < -180.0 || longitude > 180.0) {
      // DEC-004 OPEN: strict rejection. Normalization is NOT performed here;
      // GEO-007/ADV-008 record the open decision at fixture level.
      return const AtlasValidation.invalid(
        AtlasRejection(
          'OUT_OF_RANGE',
          'Longitude must be within [-180, 180] (normalization undecided, DEC-004).',
        ),
      );
    }
    if (crs != AtlasCoordinate.wgs84) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'UNSUPPORTED_CRS',
          'Only WGS84 coordinates are accepted; unknown CRS is never assumed.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  /// Validates then constructs, throwing [AtlasRejectionException] on failure.
  static AtlasCoordinate checked(
    double latitude,
    double longitude, {
    String crs = AtlasCoordinate.wgs84,
  }) {
    final validation = validate(latitude, longitude, crs: crs);
    if (!validation.isValid) {
      throw AtlasRejectionException(validation.rejection!);
    }
    return AtlasCoordinate(latitude: latitude, longitude: longitude, crs: crs);
  }
}
