// Sovereign Atlas Engine — atlas_core
// AtlasValidation: the explicit valid/invalid outcome of a domain check.
//
// Contract: ATLAS-VALID-001. Invalid values MUST NOT silently become valid
// (coordinate-contract §2, ATLAS-NORMATIVE). Validation reports the reason
// instead of coercing. Phase 0.5 slice. Pure Dart, zero dependencies.

import '../errors/rejection.dart';

/// Outcome of validating a domain value.
///
/// Valid outcomes carry no rejection. Invalid outcomes MUST carry one —
/// callers are expected to surface it, never to guess a replacement value.
final class AtlasValidation {
  /// Successful validation. No information is attached.
  const AtlasValidation.valid()
      : isValid = true,
        rejection = null;

  /// Failed validation. [rejection] explains the category and detail.
  const AtlasValidation.invalid(AtlasRejection rejection)
      : isValid = false,
        rejection = rejection;

  final bool isValid;

  /// Present if and only if [isValid] is false.
  final AtlasRejection? rejection;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasValidation &&
          isValid == other.isValid &&
          rejection == other.rejection;

  @override
  int get hashCode => Object.hash(isValid, rejection);

  @override
  String toString() =>
      isValid ? 'AtlasValidation.valid' : 'AtlasValidation($rejection)';
}
