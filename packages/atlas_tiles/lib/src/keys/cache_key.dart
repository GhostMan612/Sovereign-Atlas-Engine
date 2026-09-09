// Sovereign Atlas Engine — atlas_tiles
// AtlasCacheKey: namespaced, validated, storage-free cache identity.
//
// Contract: 1.7-C (inventory: PROPOSED → PROVISIONAL; key rules below).
// - Identity-bearing dimensions: namespace + value (both required, non-empty).
// - `tile` namespace values MUST parse as AtlasTileKey (structural enforcement
//   turns URL-masquerade into INVALID_KEY instead of a collision risk).
// - `resource` namespace values MUST be non-empty and URL-free (mirrors the
//   resource-identity prohibition; 1.7-L adversarial target).
// - Unknown namespaces reject (no silent acceptance of new dimensions).
// - Canonical form is the validated value string (deterministic; no storage,
//   no hashing, no timestamps, no locale).
// Phase 1.7 slice. Depends on atlas_core + atlas_provider_api (TileKey shape).

import '../../../../atlas_core/lib/atlas_core.dart';
import '../../../../atlas_provider_api/lib/atlas_provider_api.dart';

/// Cache-key namespace: which construction rule governs the value.
enum AtlasCacheNamespace {
  /// Tile-grid scope; values obey the TileKey shape.
  tile,

  /// Resource scope; values obey the resource-identity string rule.
  resource,
}

/// Storage-free cache identity: WHAT a lookup asks for (never where it lives).
final class AtlasCacheKey {
  const AtlasCacheKey({required this.namespace, required this.value});

  final AtlasCacheNamespace namespace;
  final String value;

  AtlasValidation validate() {
    if (value.isEmpty) {
      return const AtlasValidation.invalid(
        AtlasRejection('INVALID_KEY', 'Cache key values must be non-empty.'),
      );
    }
    switch (namespace) {
      case AtlasCacheNamespace.tile:
        try {
          AtlasTileKey.parse(value);
        } on AtlasRejectionException {
          return const AtlasValidation.invalid(
            AtlasRejection(
              'INVALID_KEY',
              'Tile-namespace keys must parse as tile keys (URL-shaped values refuse here).',
            ),
          );
        }
        return const AtlasValidation.valid();
      case AtlasCacheNamespace.resource:
        if (value.contains('://')) {
          return const AtlasValidation.invalid(
            AtlasRejection(
              'INVALID_KEY',
              'Resource-namespace keys must never contain URLs.',
            ),
          );
        }
        return const AtlasValidation.valid();
    }
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasCacheKey &&
          namespace == other.namespace &&
          value == other.value;

  @override
  int get hashCode => Object.hash(namespace, value);

  @override
  String toString() => 'AtlasCacheKey(${namespace.name}/$value)';
}
