// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

enum AtlasTileScheme {

  xyz,

  tms,
}

final class AtlasTileCoordinate {
  const AtlasTileCoordinate({
    required this.z,
    required this.x,
    required this.y,
  });

  static const int maxZoom = 32;

  final int z;
  final int x;
  final int y;

  int rowFor(AtlasTileScheme scheme) =>
      scheme == AtlasTileScheme.xyz ? y : (1 << z) - 1 - y;

  AtlasValidation validate() {
    if (z < 0 || z > maxZoom) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_TILE',
          'Tile zoom must be within [0, 32] (PROPOSED practical bound).',
        ),
      );
    }
    final extent = 1 << z;
    if (x < 0 || x >= extent || y < 0 || y >= extent) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_TILE',
          'Tile x/y must be within [0, 2^z) for the given zoom.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasTileCoordinate &&
          z == other.z &&
          x == other.x &&
          y == other.y;

  @override
  int get hashCode => Object.hash(z, x, y);

  @override
  String toString() => 'AtlasTileCoordinate($z/$x/$y)';
}
