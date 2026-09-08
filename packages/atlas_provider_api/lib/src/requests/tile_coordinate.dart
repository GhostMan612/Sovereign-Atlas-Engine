// Sovereign Atlas Engine — atlas_provider_api
// Tile coordinates and schemes: what a tile address means (never a fetch).
//
// Contracts: ATLAS-TILE-ID-001 (tile-contract.md).
// Status: ATLAS-NORMATIVE slippy-grid math (TILE fixtures); the z ≤ 32 bound
// is PROPOSED (practical limit so `1 << z` stays exact; documented, not
// derived from sources).
// - Identity (z/x/y integers) is validated: non-negative, and x/y below 2^z.
// - Schemes: `xyz` (origin top-left, OSM/OTM order) vs `tms` (origin
//   bottom-left; row flips as y_tms = 2^z − 1 − y). The Esri `{z}/{y}/{x}`
//   ordering difference observed in forensics (F-10) is a TEMPLATE concern
//   (see tile_request.dart), not a scheme: both orders address the same tile.
// Phase 1.4 slice. Depends on atlas_core only.

import '../../../../atlas_core/lib/atlas_core.dart';

/// Tile grid scheme: which corner row 0 addresses.
enum AtlasTileScheme {
  /// Origin top-left (OSM / OpenTopoMap / default web-mercator addressing).
  xyz,

  /// Origin bottom-left (TMS). Row flips against XYZ as `2^z − 1 − y`.
  tms,
}

/// Slippy-grid tile address.
final class AtlasTileCoordinate {
  const AtlasTileCoordinate({
    required this.z,
    required this.x,
    required this.y,
  });

  /// Practical maximum zoom so `1 << z` stays exact (PROPOSED bound).
  static const int maxZoom = 32;

  final int z;
  final int x;
  final int y;

  /// Row index under [scheme] (TMS flips; XYZ is identity).
  int rowFor(AtlasTileScheme scheme) =>
      scheme == AtlasTileScheme.xyz ? y : (1 << z) - 1 - y;

  /// Grid validation: z in [0, 32], x/y in [0, 2^z).
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
