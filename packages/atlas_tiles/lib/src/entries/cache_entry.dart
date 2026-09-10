// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../keys/cache_key.dart';

final class AtlasCacheEntry {
  const AtlasCacheEntry({
    required this.key,
    required this.storedAt,
    this.resource,
    this.maxAgeSeconds,
    this.payloadId,
    this.revoked = false,
  });

  final AtlasCacheKey key;
  final AtlasResourceIdentity? resource;
  final int storedAt;
  final int? maxAgeSeconds;
  final AtlasId? payloadId;
  final bool revoked;

  AtlasValidation validate() {
    final keyCheck = key.validate();
    if (!keyCheck.isValid) return keyCheck;
    if (storedAt < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_ENTRY',
          'Entry storedAt must be non-negative epoch seconds.',
        ),
      );
    }
    if (maxAgeSeconds != null && maxAgeSeconds! < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_ENTRY',
          'Entry maxAgeSeconds must be non-negative when declared.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  AtlasCacheEntry invalidate() => AtlasCacheEntry(
        key: key,
        resource: resource,
        storedAt: storedAt,
        maxAgeSeconds: maxAgeSeconds,
        payloadId: payloadId,
        revoked: true,
      );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasCacheEntry &&
          key == other.key &&
          resource == other.resource &&
          storedAt == other.storedAt &&
          maxAgeSeconds == other.maxAgeSeconds &&
          payloadId == other.payloadId &&
          revoked == other.revoked;

  @override
  int get hashCode =>
      Object.hash(key, resource, storedAt, maxAgeSeconds, payloadId, revoked);
}
