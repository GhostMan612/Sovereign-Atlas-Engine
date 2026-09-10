// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import '../errors/rejection.dart';

final class AtlasValidation {

  const AtlasValidation.valid()
      : isValid = true,
        rejection = null;

  const AtlasValidation.invalid(AtlasRejection rejection)
      : isValid = false,
        rejection = rejection;

  final bool isValid;

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
