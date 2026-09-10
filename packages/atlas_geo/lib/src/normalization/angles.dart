// Sovereign Atlas Engine — atlas_geo
// AtlasAngles: explicit, domain-named angle normalization operations.
//
// Directive §9/§15 (Phase 1.1): validation, normalization, and canonicalization
// are DIFFERENT operations and must not collapse into one helper. There is no
// universal magic normalizer: bearing, signed-angle, and longitude-position
// domains each get their own named operation with their own contract.
//
// - normalizeBearingDeg: ATLAS-NORMATIVE extraction of the long-standing inline
//   behavior (SOURCE-VERIFIED F-02 readout + compass convention).
// - normalizeSignedDeg: PROPOSED → PROVISIONAL (contract-owned angular op).
// - normalizeLongitudeDeg: PROPOSED → PROVISIONAL, owned by DEC-004. It does NOT
//   settle DEC-004: validation still REJECTS out-of-range longitudes; this op
//   exists so any future normalization path is explicit and named (§5).
// - Non-finite input throws AtlasRejectionException(NON_FINITE) — ATLAS-NORMATIVE
//   consistency with AtlasComparison (never compare, never normalize NaN/Inf).
// Phase 1.1 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';

/// Named angle-domain normalizations. See module docs for status per operation.
abstract final class AtlasAngles {
  /// Normalizes any finite bearing to [0, 360).
  ///
  /// ATLAS-NORMATIVE: identical math to the Phase 0 inline behavior
  /// (`(deg % 360 + 360) % 360`; Dart `%` is Euclidean so the outer wrap is
  /// belt-and-braces, not behavior). 360 → 0; −1 → 359; 720 → 0.
  static double normalizeBearingDeg(double degrees) {
    _requireFinite(degrees, 'bearing');
    return ((degrees % 360.0) + 360.0) % 360.0;
  }

  /// Normalizes any finite signed angle to (−180, 180].
  ///
  /// PROVISIONAL — NOT ATLAS-NORMATIVE (contract-owned angular op, no DEC
  /// assigned). Boundary: 180 stays 180; −180 maps to 180 (documented
  /// asymmetry of the half-open choice, not a claim about domains that need
  /// the seam elsewhere).
  static double normalizeSignedDeg(double degrees) {
    _requireFinite(degrees, 'signed angle');
    final wrapped = normalizeBearingDeg(degrees);
    return wrapped > 180.0 ? wrapped - 360.0 : wrapped;
  }

  /// Normalizes a longitude POSITION to (−180, 180] by wrapping.
  ///
  /// PROVISIONAL — NOT ATLAS-NORMATIVE. Ownership: DEC-004 (still open).
  /// Calling this op never validates: `AtlasCoordinates.validate` continues to
  /// REJECT out-of-range longitudes. Any future decision that adopts wrapping
  /// must route through DEC-004; until then this is a named utility only.
  static double normalizeLongitudeDeg(double longitude) =>
      normalizeSignedDeg(longitude);

  static void _requireFinite(double value, String what) {
    if (!value.isFinite) {
      throw AtlasRejectionException(
        AtlasRejection('NON_FINITE', '$what must be finite to normalize.'),
      );
    }
  }
}
