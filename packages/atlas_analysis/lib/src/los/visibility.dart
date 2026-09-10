// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';
import '../zones/spatial.dart';

typedef AtlasElevationSampler = double? Function(AtlasCoordinate point);

final class AtlasLineOfSight {
  const AtlasLineOfSight({
    required this.visible,
    this.obstructionAt,
    this.obstructionHeight,
  });

  final bool visible;
  final AtlasCoordinate? obstructionAt;
  final double? obstructionHeight;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasLineOfSight &&
          visible == other.visible &&
          obstructionAt == other.obstructionAt &&
          obstructionHeight == other.obstructionHeight;

  @override
  int get hashCode => Object.hash(visible, obstructionAt, obstructionHeight);
}

abstract final class AtlasVisibility {

  static AtlasLineOfSight lineOfSight({
    required AtlasCoordinate from,
    required AtlasCoordinate to,
    required double fromHeightM,
    required double toHeightM,
    required double stepMeters,
    required AtlasElevationSampler elevationAt,
  }) {
    final fromElev = elevationAt(from) ?? 0.0;
    final toElev = elevationAt(to) ?? 0.0;
    final path = AtlasSpatial.densify([from, to], stepMeters);
    final totalM = AtlasGeoMath.haversineKm(from, to) * 1000.0;
    if (totalM == 0) return const AtlasLineOfSight(visible: true);
    var traveledM = 0.0;
    for (var i = 1; i < path.length - 1; i++) {
      traveledM += AtlasGeoMath.haversineKm(path[i - 1], path[i]) * 1000.0;
      final fraction = traveledM / totalM;
      final sightH = (fromElev + fromHeightM) * (1 - fraction) +
          (toElev + toHeightM) * fraction;
      final terrain = elevationAt(path[i]);
      if (terrain != null && terrain > sightH) {
        return AtlasLineOfSight(
          visible: false,
          obstructionAt: path[i],
          obstructionHeight: terrain,
        );
      }
    }
    return const AtlasLineOfSight(visible: true);
  }

  static Map<double, double> viewshed({
    required AtlasCoordinate observer,
    required double observerHeightM,
    required List<double> bearings,
    required double rangeMeters,
    required double stepMeters,
    required AtlasElevationSampler elevationAt,
  }) {
    final result = <double, double>{};
    for (final bearing in bearings) {
      final edge = _project(observer, bearing, rangeMeters);
      final sight = lineOfSight(
        from: observer,
        to: edge,
        fromHeightM: observerHeightM,
        toHeightM: 0.0,
        stepMeters: stepMeters,
        elevationAt: elevationAt,
      );
      result[bearing] = sight.visible
          ? rangeMeters
          : AtlasGeoMath.haversineKm(observer, sight.obstructionAt!) * 1000.0;
    }
    return result;
  }

  static AtlasCoordinate _project(
    AtlasCoordinate from,
    double bearingDeg,
    double rangeMeters,
  ) {
    const earth = AtlasLengthUnits.earthMeanRadiusMeters;
    final angular = rangeMeters / earth;
    final bearing = bearingDeg * math.pi / 180.0;
    final lat1 = from.latitude * math.pi / 180.0;
    final lon1 = from.longitude * math.pi / 180.0;
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
      longitude: lon2 * 180.0 / math.pi,
    );
  }
}
