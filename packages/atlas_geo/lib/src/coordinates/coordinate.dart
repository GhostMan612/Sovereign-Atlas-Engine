// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

final class AtlasCoordinate {
  const AtlasCoordinate({
    required this.latitude,
    required this.longitude,
    this.crs = AtlasCoordinate.wgs84,
  });

  static const String wgs84 = 'WGS84';

  final double latitude;
  final double longitude;

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

abstract final class AtlasCoordinates {

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
