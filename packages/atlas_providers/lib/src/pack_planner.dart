// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'provider_endpoint.dart';

final class AtlasPackRefusal {
  const AtlasPackRefusal({required this.reason, this.detail = ''});

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

abstract final class AtlasPackPlanner {

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
