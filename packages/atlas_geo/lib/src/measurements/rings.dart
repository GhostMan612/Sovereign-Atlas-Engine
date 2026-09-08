// Sovereign Atlas Engine — atlas_geo
// Range-ring geometry: selectable radius steps with concentric rings + spokes.
//
// Contract: ATLAS-GEO-RING-001 (behavior-contracts.md).
// SOURCE-VERIFIED (F-02, SRC-A :249-258/:1542-1564): steps in km
// [0.1, 0.25, 0.5, 1.0, 2.0, 5.0], four concentric rings per step, N/E/S/W
// spokes to the outer ring, 65-vertex circles, flat meters-per-degree
// approximation (sub-pixel below 10 km), center null/negative-step → empty.
// Honest approximations (method, not exact SRC-A constants — the precise
// METERS_PER_DEG values were not traced, so documented Atlas constants are
// used and RING-001 checks radii within 1% rather than exact vertices):
// - metersPerDegreeLatitude = 110540.0, longitude scaled by cos(latitude).
// - rings at fractions [0.25, 0.5, 0.75, 1.0] of the step radius (the observed
//   "4 concentric" structure; fractional layout PROPOSED for audit).
// Phase 0.5 slice. Depends on coordinate.dart only (no core symbols needed here).

import 'dart:math' as math;

import '../coordinates/coordinate.dart';

/// Concentric-ring + cardinal-spoke set for one range-ring step.
final class AtlasRingSet {
  const AtlasRingSet({
    required this.center,
    required this.radiusKm,
    required this.rings,
    required this.spokes,
  });

  final AtlasCoordinate center;
  final double radiusKm;

  /// Four closed rings (65 vertices each, first == last), ordered inner→outer.
  final List<List<AtlasCoordinate>> rings;

  /// Four spokes (N/E/S/W), each [center, outerPoint].
  final List<List<AtlasCoordinate>> spokes;

  /// Empty set (null center or inactive step).
  static AtlasRingSet empty() => const AtlasRingSet(
        center: AtlasCoordinate(latitude: 0, longitude: 0),
        radiusKm: 0,
        rings: [],
        spokes: [],
      );

  bool get isEmpty => rings.isEmpty && spokes.isEmpty;
}

/// Range-ring generator.
abstract final class AtlasRangeRings {
  /// Selectable radius steps in km (SOURCE-VERIFIED order and values).
  static const List<double> steps = [0.1, 0.25, 0.5, 1.0, 2.0, 5.0];

  static const int ringsPerStep = 4;
  static const int verticesPerRing = 65;

  /// Ring fractions of the step radius, inner → outer.
  ///
  /// PROVISIONAL — NOT ATLAS-NORMATIVE (0.5A Ruling 3c). Ownership:
  /// ATLAS-GEO-RING-001 contract text (PROPOSED measurement-construction
  /// sub-detail; no DEC assigned). RING-001 checks table/counts/radii-within-1%,
  /// never exact vertices, so this layout is not enshrined by tests.
  static const List<double> ringFractions = [0.25, 0.5, 0.75, 1.0];

  static const double metersPerDegreeLatitude = 110540.0;

  /// Radius for [stepIndex], or null when the step is inactive/out of range.
  static double? radiusForStep(int stepIndex) {
    if (stepIndex < 0 || stepIndex >= steps.length) return null;
    return steps[stepIndex];
  }

  /// Generates the ring set for [center] at [stepIndex].
  /// Returns [AtlasRingSet.empty] when center is null or the step is inactive
  /// (SOURCE-VERIFIED :782 behavior).
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
