// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'tile_coordinate.dart';

final class AtlasTileIdentity {
  const AtlasTileIdentity({
    required this.provider,
    required this.layer,
    required this.coordinate,
    this.scheme = AtlasTileScheme.xyz,
  });

  final AtlasId provider;
  final AtlasId layer;
  final AtlasTileCoordinate coordinate;
  final AtlasTileScheme scheme;

  AtlasValidation validate() => coordinate.validate();

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasTileIdentity &&
          provider == other.provider &&
          layer == other.layer &&
          coordinate == other.coordinate &&
          scheme == other.scheme;

  @override
  int get hashCode => Object.hash(provider, layer, coordinate, scheme);

  @override
  String toString() => 'AtlasTileIdentity(${provider.value}/${layer.value}/'
      '${coordinate.z}/${coordinate.x}/${coordinate.y}/${scheme.name})';
}
