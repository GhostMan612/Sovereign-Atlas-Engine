// Sovereign Atlas Engine — atlas_core
// AtlasId: non-empty domain identifier value object.
//
// Contract: domain-model.md (identity concept, PROPOSED). Uniqueness scope is
// owned by the containing collection, not by this type. Phase 0.5 slice.

import 'validation.dart';
import '../errors/rejection.dart';

/// Opaque domain identifier.
///
/// Equality is by exact string value. Empty identifiers are rejected by
/// [AtlasIds.check]; the const constructor itself performs no validation so
/// validated data stays cheap to construct.
final class AtlasId {
  const AtlasId(this.value);

  final String value;

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is AtlasId && value == other.value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => 'AtlasId($value)';
}

/// Constructors and checks for [AtlasId].
abstract final class AtlasIds {
  /// Rejects empty identifiers (`DUPLICATE`/empty-identity class of failures
  /// is reported as `INVALID_IDENTITY`; duplicate detection itself belongs to
  /// collections — see geometry-contract §5 and DEC-013).
  static AtlasValidation check(String value) {
    if (value.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection('INVALID_IDENTITY', 'Identifier must not be empty.'),
      );
    }
    return const AtlasValidation.valid();
  }
}
