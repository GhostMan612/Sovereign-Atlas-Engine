// Sovereign Atlas Engine — atlas_core
// AtlasComparison: tolerance-bounded float comparison for deterministic checks.
//
// Contract: determinism-policy.md (tolerance-based comparison only; no
// bit-for-bit equality for geospatial floats). Phase 0.5 slice.

/// Float comparison helpers. Exact equality is offered only for the
/// zero-tolerance case (e.g. DIST-001 zero distance); all geospatial
/// comparisons SHOULD pass an explicit tolerance.
abstract final class AtlasComparison {
  /// True when `|actual - expected| <= tolerance`.
  ///
  /// Non-finite inputs always compare false — they must be rejected upstream
  /// (ADV-005/ADV-006), never compared.
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
