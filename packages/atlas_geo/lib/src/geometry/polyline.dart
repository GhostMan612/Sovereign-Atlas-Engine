// Sovereign Atlas Engine — atlas_geo
// AtlasPolyline: ordered position sequence with structural honesty.
//
// Contracts: ATLAS-GEOM-001 area (directive §12, Phase 1.1).
// Status: PROPOSED → PROVISIONAL (contract-owned geometry primitive; no DEC
// assigned; dup-tolerance is contract text, NOT ATLAS-NORMATIVE).
// Three levels are kept distinct by API:
// - STRUCTURALLY VALID: every member is a valid coordinate (empty allowed).
// - MEANINGFUL: >= 2 positions (a length can be computed).
// - USEFUL: downstream judgment, never decided here.
// Duplicates (incl. coincident consecutive points) are PRESERVED, never
// rejected or dropped: consumers decide their own tolerance policy.
// No serialization is defined (not fixture-required; directive §17).
// Phase 1.1 slice. Depends on atlas_core + coordinate.dart + distance.dart.

import '../../../../atlas_core/lib/atlas_core.dart';
import '../coordinates/coordinate.dart';
import '../coordinates/distance.dart';

/// Ordered polyline. Order is significant.
final class AtlasPolyline {
  const AtlasPolyline(this.points);

  final List<AtlasCoordinate> points;

  /// Structural validity: all members validate. Empty is structurally valid
  /// (meaningless, but not malformed — §12 distinction).
  AtlasValidation validateStructure() {
    for (final point in points) {
      final member = AtlasCoordinates.validate(point.latitude, point.longitude);
      if (!member.isValid) return member;
    }
    return const AtlasValidation.valid();
  }

  /// Whether a length is computable (≥2 positions). Single-point and empty
  /// polylines are valid structures with no meaningful length.
  bool get isLengthMeaningful => points.length >= 2;

  /// Great-circle length in km. Returns 0 for fewer than 2 points (no throw:
  /// absence of length is not an error, it is a property of the shape).
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
