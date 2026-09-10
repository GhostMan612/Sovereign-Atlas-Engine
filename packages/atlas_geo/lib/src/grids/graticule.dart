// Sovereign Atlas Engine — atlas_geo
// Graticule + custom grid spacing (pure parameters, never pixels).
//
// Contract: blueprint 4.5 (grid engine, engine side). Zoom→interval selection
// is an explicit step table (no invented continuity); line generation emits
// coordinate values for a bounding box (rendering consumes them downstream).
// UTM/MGRS/H3 visualization stays hooks (blocked/deferred precedents).
// Phase 4 slice. Depends on atlas_core + siblings only.

import '../geometry/polygon_types.dart';

/// Graticule line set value (meridians + parallels in decimal degrees).
final class AtlasGraticule {
  const AtlasGraticule({required this.meridians, required this.parallels});

  final List<double> meridians;
  final List<double> parallels;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasGraticule &&
          _equalDoubles(meridians, other.meridians) &&
          _equalDoubles(parallels, other.parallels);

  static bool _equalDoubles(List<double> a, List<double> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode =>
      Object.hash(Object.hashAll(meridians), Object.hashAll(parallels));
}

/// Zoom-stepped graticule parameters (4.5 grid density by zoom, declared).
abstract final class AtlasGrids {
  /// Nice-degree intervals keyed by maximum zoom (first match wins).
  static const List<(int, double)> zoomSteps = [
    (2, 30.0),
    (4, 10.0),
    (6, 5.0),
    (8, 1.0),
    (10, 0.5),
    (12, 0.1),
    (14, 0.05),
    (16, 0.01),
    (99, 0.005),
  ];

  /// Interval in decimal degrees for integer [zoom] (declared table).
  static double intervalForZoom(int zoom) {
    for (final step in zoomSteps) {
      if (zoom <= step.$1) return step.$2;
    }
    return 0.005;
  }

  /// Meridian/parallel values covering [bounds] at [interval] degrees.
  static AtlasGraticule graticuleFor(
    AtlasBoundingBox bounds,
    double interval,
  ) {
    List<double> axis(double min, double max) {
      final lines = <double>[];
      var value = (min / interval).ceil() * interval;
      while (value <= max + 1e-9) {
        lines.add(double.parse(value.toStringAsFixed(6)));
        value += interval;
      }
      return lines;
    }

    return AtlasGraticule(
      meridians: axis(bounds.west, bounds.east),
      parallels: axis(bounds.south, bounds.north),
    );
  }
}
