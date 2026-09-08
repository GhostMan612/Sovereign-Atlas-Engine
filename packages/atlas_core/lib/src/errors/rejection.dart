// Sovereign Atlas Engine — atlas_core
// AtlasRejection: explicit domain failure carried as data, never thrown silently.
//
// Contract: ATLAS-VALID-001 (error-category vocabulary PROPOSED; DEC-006 open —
// categories travel as strings so no premature error-code enum is enshrined).
// Phase 0.5 slice. Pure Dart, zero dependencies beyond dart:core.

/// A classified domain rejection.
///
/// `category` uses the Phase 0.4 fixture vocabulary (e.g. `INVALID_COORDINATE`,
/// `MALFORMED`, `OUT_OF_RANGE`, `NON_FINITE`, `INVALID_ZOOM`). The category set
/// is intentionally open (a string, not an enum) until DEC-006 resolves the
/// error-reporting channel.
final class AtlasRejection {
  const AtlasRejection(this.category, this.message);

  /// Machine-readable category from the contract vocabulary.
  final String category;

  /// Human-readable detail. Never parsed by callers.
  final String message;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasRejection &&
          category == other.category &&
          message == other.message;

  @override
  int get hashCode => Object.hash(category, message);

  @override
  String toString() => 'AtlasRejection($category: $message)';
}

/// Exception wrapper for APIs that must surface an [AtlasRejection] through
/// a throwing call boundary (e.g. camera parsing).
///
/// Prefer returning [AtlasValidation] where the contract allows it; throw this
/// only where a value must be produced or the failure explained.
final class AtlasRejectionException implements Exception {
  const AtlasRejectionException(this.rejection);

  final AtlasRejection rejection;

  @override
  String toString() => 'AtlasRejectionException($rejection)';
}
