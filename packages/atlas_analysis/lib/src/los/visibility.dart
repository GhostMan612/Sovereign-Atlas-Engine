// Sovereign Atlas Engine — atlas_analysis
// Line-of-sight + radial viewshed over injected elevation samplers.
//
// Contract: blueprint Phase 9 (visibility/LOS engine side). Elevation enters
// ONLY through the injected sampler (ADR-004: no analysis→terrain edge):
// `elevationAt(point)` returns meters or null (void). LOS marches the
// great-circle dense path at explicit step meters; obstruction = terrain
// above the sight line (earth curvature ignored — documented flat-earth
// simplification; refraction ignored). Viewshed samples explicit bearings at
// explicit range (polar grid, documented discretization — not continuous
// visibility). Observer/target heights are explicit parameters.
// Phase 9 slice. Depends on atlas_core + atlas_geo only.

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';
import '../zones/spatial.dart';

/// Injected elevation sampler: point in, meters or null (void) out.
typedef AtlasElevationSampler = double? Function(AtlasCoordinate point);

/// LOS result value (visible flag + obstruction detail, if any).
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

/// Visibility services (deterministic given the same sampler).
abstract final class AtlasVisibility {
  /// Line-of-sight from [from] (+[fromHeightM]) to [to] (+[toHeightM]),
  /// marched at [stepMeters]. Voids are transparent (unknown ≠ blocked —
  /// documented; conservative callers densify their own grids).
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

  /// Radial viewshed: [bearings] rays to [rangeMeters] at [stepMeters].
  /// Returns visible range per bearing (full range = clear to the edge).
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
