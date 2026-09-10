// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_providers/atlas_providers.dart';

enum OfflinePackLifecycle {
  planned,
  downloading,
  paused,
  quotaPaused,
  complete,
  failed,
  cancelled,
}

final class OfflinePackRecord {
  OfflinePackRecord({
    required this.packId,
    required this.providerId,
    required this.providerTitle,
    required this.zMin,
    required this.zMax,
    required this.xMin,
    required this.xMax,
    required this.yMin,
    required this.yMax,
    required this.bytesPerTile,
    required this.approvedBulk,
    required this.isPrefetch,
    required this.createdAtEpoch,
  });

  final String packId;
  final String providerId;
  final String providerTitle;
  final int zMin;
  final int zMax;
  final int xMin;
  final int xMax;
  final int yMin;
  final int yMax;

  final int bytesPerTile;
  final bool approvedBulk;
  final bool isPrefetch;
  final int createdAtEpoch;

  AtlasPackPlan? plan;

  AtlasPackRefusal? refusal;

  String? appBlock;

  OfflinePackLifecycle lifecycle = OfflinePackLifecycle.planned;
  int receivedTiles = 0;
  int receivedBytes = 0;

  String? seal;

  String? manifestJson;

  String failureDetail = '';

  bool cacheEntryPresent = false;

  bool bytesHeld = true;

  Set<String> tileKeys = {};

  int persistedTileCount = 0;
  int persistedEstimatedBytes = 0;

  int get tileCount => plan?.entryCount ?? persistedTileCount;
  int get estimatedBytes => plan?.estimatedBytes ?? persistedEstimatedBytes;

  bool get isTerminal =>
      lifecycle == OfflinePackLifecycle.complete ||
      lifecycle == OfflinePackLifecycle.failed ||
      lifecycle == OfflinePackLifecycle.cancelled;

  int ageSeconds(int nowEpoch) => nowEpoch - createdAtEpoch;
}
