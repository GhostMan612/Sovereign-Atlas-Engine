// Sovereign Atlas Engine — atlas_provider_api
// AtlasTileIdentity: the full namespaced identity of one tile.
//
// Contract: ATLAS-TILE-ID-001 (five-way distinction: identity ≠ URL ≠ key ≠
// entry ≠ payload). Status: ATLAS-NORMATIVE (TILE-001/002/003: provider, layer,
// and zoom are each identity-bearing dimensions).
// Identity is structural data. It performs no I/O, builds no URLs, and knows
// no provider SDK. Phase 1.4 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';
import 'tile_coordinate.dart';

/// Namespaced tile identity: which provider, which layer, which address,
// under which scheme. Equality is structural across all four dimensions.
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

  /// Identity is valid when its coordinate validates (ids validate via
  /// [AtlasIds.check] at composition boundaries, not here).
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
