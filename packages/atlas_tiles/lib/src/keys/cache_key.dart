// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';

enum AtlasCacheNamespace {

  tile,

  resource,
}

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
