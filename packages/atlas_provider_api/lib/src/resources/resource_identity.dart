// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../provider/data_kind.dart';

final class AtlasResourceIdentity {
  const AtlasResourceIdentity({
    required this.provider,
    required this.kind,
    this.address = '',
  });

  final AtlasId provider;
  final AtlasDataKind kind;

  final String address;

  AtlasValidation validate() {
    final providerCheck = AtlasIds.check(provider.value);
    if (!providerCheck.isValid) return providerCheck;
    if (address.contains('://')) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_IDENTITY',
          'Resource identity must never be or contain a URL.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasResourceIdentity &&
          provider == other.provider &&
          kind == other.kind &&
          address == other.address;

  @override
  int get hashCode => Object.hash(provider, kind, address);

  @override
  String toString() =>
      'AtlasResourceIdentity(${provider.value}/${kind.name}/$address)';
}
