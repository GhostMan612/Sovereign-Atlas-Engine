// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import '../coordinates/coordinate.dart';

final class AtlasRingSet {
  const AtlasRingSet({
    required this.center,
    required this.radiusKm,
    required this.rings,
    required this.spokes,
  });

  final AtlasCoordinate center;
  final double radiusKm;

  final List<List<AtlasCoordinate>> rings;

  final List<List<AtlasCoordinate>> spokes;

  static AtlasRingSet empty() => const AtlasRingSet(
        center: AtlasCoordinate(latitude: 0, longitude: 0),
        radiusKm: 0,
        rings: [],
        spokes: [],
      );

  bool get isEmpty => rings.isEmpty && spokes.isEmpty;
}

abstract final class AtlasRangeRings {

  static const List<double> steps = [0.1, 0.25, 0.5, 1.0, 2.0, 5.0];

  static const int ringsPerStep = 4;
  static const int verticesPerRing = 65;

  static const List<double> ringFractions = [0.25, 0.5, 0.75, 1.0];

  static const double metersPerDegreeLatitude = 110540.0;

  static double? radiusForStep(int stepIndex) {
    if (stepIndex < 0 || stepIndex >= steps.length) return null;
    return steps[stepIndex];
  }

  static AtlasRingSet generate(AtlasCoordinate? center, int stepIndex) {
    final radiusKm = center == null ? null : radiusForStep(stepIndex);
    if (center == null || radiusKm == null) return AtlasRingSet.empty();
    final rings = <List<AtlasCoordinate>>[
      for (final fraction in ringFractions)
        _circle(center, radiusKm * fraction),
    ];
    final spokes = <List<AtlasCoordinate>>[
      for (final bearingDeg in [0.0, 90.0, 180.0, 270.0])
        [center, _destination(center, radiusKm, bearingDeg)],
    ];
    return AtlasRingSet(
      center: center,
      radiusKm: radiusKm,
      rings: rings,
      spokes: spokes,
    );
  }

  static List<AtlasCoordinate> _circle(
    AtlasCoordinate center,
    double radiusKm,
  ) {
    final vertices = <AtlasCoordinate>[];
    for (var i = 0; i < verticesPerRing - 1; i++) {
      final bearing = 360.0 * i / (verticesPerRing - 1);
      vertices.add(_destination(center, radiusKm, bearing));
    }
    vertices.add(vertices.first);
    return vertices;
  }

  static AtlasCoordinate _destination(
    AtlasCoordinate center,
    double radiusKm,
    double bearingDeg,
  ) {
    final radiusM = radiusKm * 1000.0;
    final latRad = center.latitude * math.pi / 180.0;
    final dLat = radiusM *
        math.cos(bearingDeg * math.pi / 180.0) /
        metersPerDegreeLatitude;
    final metersPerDegreeLongitude =
        metersPerDegreeLatitude * math.max(math.cos(latRad), 0.01);
    final dLon = radiusM *
        math.sin(bearingDeg * math.pi / 180.0) /
        metersPerDegreeLongitude;
    return AtlasCoordinate(
      latitude: center.latitude + dLat,
      longitude: center.longitude + dLon,
    );
  }
}
