// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../coordinates/coordinate.dart';
import 'polygon.dart';

final class AtlasPolygon {
  const AtlasPolygon({required this.exterior, this.holes = const []});

  final List<AtlasCoordinate> exterior;
  final List<List<AtlasCoordinate>> holes;

  AtlasValidation validate() {
    final exteriorCheck = AtlasRings.validateRing(exterior);
    if (!exteriorCheck.isValid) return exteriorCheck;
    for (final hole in holes) {
      final holeCheck = AtlasRings.validateRing(hole);
      if (!holeCheck.isValid) return holeCheck;
    }
    return const AtlasValidation.valid();
  }

  AtlasBoundingBox get bounds {
    var south = exterior.first.latitude;
    var north = exterior.first.latitude;
    var west = exterior.first.longitude;
    var east = exterior.first.longitude;
    for (final point in exterior.skip(1)) {
      if (point.latitude < south) south = point.latitude;
      if (point.latitude > north) north = point.latitude;
      if (point.longitude < west) west = point.longitude;
      if (point.longitude > east) east = point.longitude;
    }
    return AtlasBoundingBox(south: south, west: west, north: north, east: east);
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPolygon &&
          _equalRings(exterior, other.exterior) &&
          holes.length == other.holes.length &&
          _equalHoleSets(holes, other.holes);

  static bool _equalRings(List<AtlasCoordinate> a, List<AtlasCoordinate> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  static bool _equalHoleSets(
    List<List<AtlasCoordinate>> a,
    List<List<AtlasCoordinate>> b,
  ) {
    for (var i = 0; i < a.length; i++) {
      if (!_equalRings(a[i], b[i])) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(Object.hashAll(exterior), holes.length);
}

final class AtlasBoundingBox {
  const AtlasBoundingBox({
    required this.south,
    required this.west,
    required this.north,
    required this.east,
  });

  final double south;
  final double west;
  final double north;
  final double east;

  AtlasValidation validate() {
    for (final edge in [south, west, north, east]) {
      if (!edge.isFinite) {
        return const AtlasValidation.invalid(
          AtlasRejection('NON_FINITE', 'Bounds edges must be finite.'),
        );
      }
    }
    if (south < -90.0 || south > 90.0 || north < -90.0 || north > 90.0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'OUT_OF_RANGE',
          'Bounds latitudes must be within [-90, 90].',
        ),
      );
    }
    if (west < -180.0 || west > 180.0 || east < -180.0 || east > 180.0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'OUT_OF_RANGE',
          'Bounds longitudes must be within [-180, 180] (DEC-004).',
        ),
      );
    }
    if (south > north) {
      return const AtlasValidation.invalid(
        AtlasRejection('INVALID_GEOMETRY', 'Bounds require south <= north.'),
      );
    }
    return const AtlasValidation.valid();
  }

  bool get crossesAntimeridian => west > east;

  bool contains(AtlasCoordinate point) {
    if (crossesAntimeridian) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'UNRESOLVED_ANTIMERIDIAN',
          'Containment for antimeridian-crossing boxes is undecided (DEC-005).',
        ),
      );
    }
    return point.latitude >= south &&
        point.latitude <= north &&
        point.longitude >= west &&
        point.longitude <= east;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasBoundingBox &&
          south == other.south &&
          west == other.west &&
          north == other.north &&
          east == other.east;

  @override
  int get hashCode => Object.hash(south, west, north, east);
}
