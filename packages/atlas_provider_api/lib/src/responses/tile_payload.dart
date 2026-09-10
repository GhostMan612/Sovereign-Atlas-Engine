// Sovereign Atlas Engine — atlas_provider_api
// Tile payload/entry shapes: pure data for a FUTURE cache, never a cache.
//
// Contract area: ATLAS-TILE-CACHE-001 (shapes only; behavior deferred with
// atlas_tiles/atlas_offline).
// Status: PROPOSED → PROVISIONAL. These types answer "what would a stored tile
// consist of" so later engines share vocabulary: an opaque payload identity +
// byte length, and an entry binding identity + key + payload with a
// consistency check (key address must equal identity address). No storage, no
// eviction, no TTL, no I/O, no hashing (crypto would be a dependency).
// Phase 1.4 slice. Depends on atlas_core (+ requests siblings) only.

import 'package:atlas_core/atlas_core.dart';
import '../requests/tile_identity.dart';
import '../requests/tile_key.dart';

/// Opaque payload reference: identity + byte length. Bytes themselves live
/// outside this model (transport/cache concern, deferred).
final class AtlasTilePayload {
  const AtlasTilePayload({required this.id, required this.byteLength});

  final AtlasId id;

  /// Declared length in bytes. Must be non-negative (zero = explicitly empty,
  /// distinct from missing — cf. cache 200+non-empty gate, F-10).
  final int byteLength;

  AtlasValidation validate() {
    if (byteLength < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_PAYLOAD',
          'Payload byte length must be non-negative.',
        ),
      );
    }
    return AtlasIds.check(id.value);
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasTilePayload &&
          id == other.id &&
          byteLength == other.byteLength;

  @override
  int get hashCode => Object.hash(id, byteLength);
}

/// Stored-tile shape: which tile, under which key, pointing at which payload.
final class AtlasTileEntry {
  const AtlasTileEntry({
    required this.identity,
    required this.key,
    required this.payloadId,
  });

  final AtlasTileIdentity identity;
  final AtlasTileKey key;
  final AtlasId payloadId;

  /// Structural validation: payload id non-empty, key address consistent with
  /// identity address (PROPOSED integrity rule — a key pointing elsewhere
  /// than its identity claims is malformed, not merely unusual).
  AtlasValidation validate() {
    final payloadCheck = AtlasIds.check(payloadId.value);
    if (!payloadCheck.isValid) return payloadCheck;
    final coordinate = identity.coordinate;
    if (key.z != coordinate.z ||
        key.x != coordinate.x ||
        key.y != coordinate.y) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INCONSISTENT_ENTRY',
          'Entry key address must equal identity coordinate.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasTileEntry &&
          identity == other.identity &&
          key == other.key &&
          payloadId == other.payloadId;

  @override
  int get hashCode => Object.hash(identity, key, payloadId);
}
