// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../coordinates/coordinate.dart';
import '../coordinates/distance.dart';

final class AtlasPolyline {
  const AtlasPolyline(this.points);

  final List<AtlasCoordinate> points;

  AtlasValidation validateStructure() {
    for (final point in points) {
      final member = AtlasCoordinates.validate(point.latitude, point.longitude);
      if (!member.isValid) return member;
    }
    return const AtlasValidation.valid();
  }

  bool get isLengthMeaningful => points.length >= 2;

  double lengthKm({double radiusKm = AtlasGeoMath.referenceRadiusKm}) {
    var total = 0.0;
    for (var i = 1; i < points.length; i++) {
      total += AtlasGeoMath.haversineKm(
        points[i - 1],
        points[i],
        radiusKm: radiusKm,
      );
    }
    return total;
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPolyline && _equalPoints(points, other.points);

  static bool _equalPoints(List<AtlasCoordinate> a, List<AtlasCoordinate> b) {
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hashAll(points);
}
