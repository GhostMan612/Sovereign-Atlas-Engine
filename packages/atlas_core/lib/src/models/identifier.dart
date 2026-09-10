// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'validation.dart';
import '../errors/rejection.dart';

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

abstract final class AtlasIds {

  static AtlasValidation check(String value) {
    if (value.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection('INVALID_IDENTITY', 'Identifier must not be empty.'),
      );
    }
    return const AtlasValidation.valid();
  }
}
