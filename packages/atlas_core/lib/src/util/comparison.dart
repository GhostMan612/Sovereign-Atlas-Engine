// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

abstract final class AtlasComparison {

  static bool withinTolerance(
    double actual,
    double expected,
    double tolerance,
  ) {
    if (!actual.isFinite || !expected.isFinite || !tolerance.isFinite) {
      return false;
    }
    if (tolerance < 0) return false;
    return (actual - expected).abs() <= tolerance;
  }
}
