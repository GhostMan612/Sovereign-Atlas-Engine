// Sovereign Atlas Engine — atlas_tiles
// AtlasCacheEntry: what the cache KNOWS (never where it is stored).
//
// Contract: 1.7-D (inventory: PROPOSED → PROVISIONAL; per-field justification).
// Fields and why each exists:
// - key (required): lookup identity. Without it the entry is unaddressable.
// - resource (optional): originating resource identity for non-tile entries
//   and provenance linkage; absent is valid (tile entries address by key).
// - storedAt (required int epoch seconds): explicit time anchor. No DateTime,
//   no now() — time always arrives as input (1.7-J).
// - maxAgeSeconds (optional): declared freshness horizon. Null = unknown
//   freshness policy (lookup treats as STALE per 1.7-F provisional rule).
// - payloadId (optional): opaque payload reference (bytes live downstream;
//   1.7-H: identity without decoding).
// - revoked (default false): invalidation marker. Invalidation is a pure copy
//   ([invalidate]); revocation affects lookup, never structural validity.
// What is NOT here: file paths, rows, HTTP responses, bytes, TTL timers,
// eviction state, renderer handles (all 1.7-D prohibitions).
// Phase 1.7 slice. Depends on atlas_core + atlas_provider_api (identity type).

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../keys/cache_key.dart';

/// Semantic cache entry: knowledge about a cached resource, storage-free.
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

  /// Pure invalidation: a revoked copy; this entry is untouched (immutable).
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
