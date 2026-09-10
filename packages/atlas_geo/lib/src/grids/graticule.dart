// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import '../geometry/polygon_types.dart';

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

abstract final class AtlasGrids {

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

  static double intervalForZoom(int zoom) {
    for (final step in zoomSteps) {
      if (zoom <= step.$1) return step.$2;
    }
    return 0.005;
  }

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
