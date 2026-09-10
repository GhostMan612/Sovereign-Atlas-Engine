// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import '../requests/tile_identity.dart';
import '../requests/tile_key.dart';

final class AtlasTilePayload {
  const AtlasTilePayload({required this.id, required this.byteLength});

  final AtlasId id;

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

final class AtlasTileEntry {
  const AtlasTileEntry({
    required this.identity,
    required this.key,
    required this.payloadId,
  });

  final AtlasTileIdentity identity;
  final AtlasTileKey key;
  final AtlasId payloadId;

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
