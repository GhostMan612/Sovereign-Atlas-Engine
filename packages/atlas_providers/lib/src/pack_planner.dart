// Sovereign Atlas Engine — atlas_providers
// Pack planner: declarations become refusals HERE (not Phase 4).
//
// Contract: blueprint 2.1/3.4 policy declarations + phase-3 note §4.
// - Pure tile enumeration over explicit inclusive ranges (no discovery).
// - Refusals (total function, never throws): BULK_GUARD (guarded provider +
//   unapproved bulk), PACK_TOO_LARGE (count > maxTiles), PREFETCH_REFUSED
//   (disallowed prefetch), INVALID_RANGE (incoherent bounds).
// - Estimates require explicit bytes-per-tile (no invented averages).
// Phase 3 slice. Depends on atlas_core + atlas_provider_api only.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'provider_endpoint.dart';

/// Pack refusal (declared policy, not an error throw).
final class AtlasPackRefusal {
  const AtlasPackRefusal({required this.reason, this.detail = ''});

  /// BULK_GUARD | PACK_TOO_LARGE | PREFETCH_REFUSED | INVALID_RANGE.
  final String reason;
  final String detail;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPackRefusal &&
          reason == other.reason &&
          detail == other.detail;

  @override
  int get hashCode => Object.hash(reason, detail);
}

/// Approved pack plan: explicit tile list + declared estimate.
final class AtlasPackPlan {
  const AtlasPackPlan({
    required this.tiles,
    required this.estimatedBytes,
    required this.provider,
  });

  final List<AtlasTileCoordinate> tiles;
  final int estimatedBytes;
  final AtlasId provider;

  int get entryCount => tiles.length;
}

/// Pure pack planner (enumeration + enforcement, no IO).
abstract final class AtlasPackPlanner {
  /// Plans [endpoint] over inclusive [zMin]..[zMax] × [xMin]..[xMax] ×
  /// [yMin]..[yMax]. Returns a plan or a refusal (never throws on policy).
  static Object plan({
    required AtlasProviderEndpoint endpoint,
    required int zMin,
    required int zMax,
    required int xMin,
    required int xMax,
    required int yMin,
    required int yMax,
    required int bytesPerTileEstimate,
    bool approvedBulk = false,
    bool isPrefetch = false,
  }) {
    if (zMin < 0 ||
        zMax < 0 ||
        xMin < 0 ||
        xMax < 0 ||
        yMin < 0 ||
        yMax < 0 ||
        zMin > zMax ||
        xMin > xMax ||
        yMin > yMax) {
      return const AtlasPackRefusal(
        reason: 'INVALID_RANGE',
        detail: 'Pack bounds must be non-negative with min <= max.',
      );
    }
    if (isPrefetch && !endpoint.policy.prefetchAllowed) {
      return AtlasPackRefusal(
        reason: 'PREFETCH_REFUSED',
        detail: '${endpoint.descriptor.id.value} disallows prefetch.',
      );
    }
    if (endpoint.policy.bulkGuard != null && !approvedBulk) {
      return AtlasPackRefusal(
        reason: 'BULK_GUARD',
        detail: endpoint.policy.bulkGuard!,
      );
    }
    final tiles = <AtlasTileCoordinate>[];
    for (var z = zMin; z <= zMax; z++) {
      for (var x = xMin; x <= xMax; x++) {
        for (var y = yMin; y <= yMax; y++) {
          tiles.add(AtlasTileCoordinate(z: z, x: x, y: y));
        }
      }
    }
    final maxTiles = endpoint.policy.maxTiles;
    if (maxTiles != null && tiles.length > maxTiles) {
      return AtlasPackRefusal(
        reason: 'PACK_TOO_LARGE',
        detail: '${tiles.length} tiles exceeds maxTiles=$maxTiles.',
      );
    }
    return AtlasPackPlan(
      tiles: tiles,
      estimatedBytes: tiles.length * bytesPerTileEstimate,
      provider: endpoint.descriptor.id,
    );
  }
}
