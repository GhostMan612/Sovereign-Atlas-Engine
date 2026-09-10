// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

abstract final class AtlasAngles {

  static double normalizeBearingDeg(double degrees) {
    _requireFinite(degrees, 'bearing');
    return ((degrees % 360.0) + 360.0) % 360.0;
  }

  static double normalizeSignedDeg(double degrees) {
    _requireFinite(degrees, 'signed angle');
    final wrapped = normalizeBearingDeg(degrees);
    return wrapped > 180.0 ? wrapped - 360.0 : wrapped;
  }

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
