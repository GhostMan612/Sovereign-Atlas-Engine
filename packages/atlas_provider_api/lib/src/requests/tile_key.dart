// Sovereign Atlas Engine — atlas_provider_api
// AtlasTileKey: the layer-scoped storage-key SHAPE (not a cache engine).
//
// Contract: ATLAS-TILE-ID-001 (tile-contract.md §1).
// Status: SOURCE-VERIFIED shape `<layer>/{z}_{x}_{y}` (SRC-C F-10, TILE-004),
// carried as the cache-relative key with exact round-trip. The key is
// deliberately layer-scoped, matching the observed shape: cross-provider
// distinction lives in [AtlasTileIdentity], never in this string (TILE-001).
// No namespace extension is added (extension DEFERRED per 1.4-A inventory).
// Phase 1.4 slice. Depends on atlas_core only.

import '../../../../atlas_core/lib/atlas_core.dart';

/// Layer-scoped tile key with exact string round-trip.
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

  /// Renders `<layer>/{z}_{x}_{y}` (SOURCE-VERIFIED shape).
  String get keyString => '$layer/${z}_${x}_${y}';

  /// Parses [keyString] back. Throws [AtlasRejectionException] (`MALFORMED`)
  /// on structural or numeric failure — never a silent default.
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
