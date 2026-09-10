// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

final class AtlasTileKey {
  const AtlasTileKey({
    required this.layer,
    required this.z,
    required this.x,
    required this.y,
  });

  final String layer;
  final int z;
  final int x;
  final int y;

  String get keyString => '$layer/${z}_${x}_${y}';

  static AtlasTileKey parse(String keyString) {
    AtlasRejectionException malformed(String detail) =>
        AtlasRejectionException(AtlasRejection('MALFORMED', detail));
    final slash = keyString.indexOf('/');
    if (slash < 0) {
      throw malformed('Tile key requires "<layer>/{z}_{x}_{y}".');
    }
    final layer = keyString.substring(0, slash);
    final rest = keyString.substring(slash + 1).split('_');
    if (layer.isEmpty || rest.length != 3) {
      throw malformed('Tile key requires "<layer>/{z}_{x}_{y}".');
    }
    final z = int.tryParse(rest[0]);
    final x = int.tryParse(rest[1]);
    final y = int.tryParse(rest[2]);
    if (z == null || x == null || y == null) {
      throw malformed('Tile key coordinates must be integers: "$keyString".');
    }
    return AtlasTileKey(layer: layer, z: z, x: x, y: y);
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasTileKey &&
          layer == other.layer &&
          z == other.z &&
          x == other.x &&
          y == other.y;

  @override
  int get hashCode => Object.hash(layer, z, x, y);

  @override
  String toString() => 'AtlasTileKey($keyString)';
}
