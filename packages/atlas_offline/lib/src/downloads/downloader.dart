// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_tiles/atlas_tiles.dart';
import '../policy/rate_limiter.dart';

typedef AtlasChunkSource = Future<List<int>> Function(AtlasTileCoordinate tile);

enum AtlasDownloadState {
  planned,
  downloading,
  paused,
  quotaPaused,
  complete,
  failed,
  cancelled,
}

final class AtlasDownloadProgress {
  const AtlasDownloadProgress({
    required this.received,
    required this.planned,
    required this.bytes,
  });

  final int received;
  final int planned;
  final int bytes;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasDownloadProgress &&
          received == other.received &&
          planned == other.planned &&
          bytes == other.bytes;

  @override
  int get hashCode => Object.hash(received, planned, bytes);

  @override
  String toString() => 'progress $received/$planned ($bytes B)';
}

final class AtlasPackDownloader {
  AtlasPackDownloader({
    required this.tiles,
    required this.source,
    this.limiter,
    this.cancellation,
    Map<String, List<int>>? received,
  })  : _received = received ?? <String, List<int>>{},
        state = AtlasDownloadState.planned;

  final List<AtlasTileCoordinate> tiles;
  final AtlasChunkSource source;
  final AtlasRateLimiter? limiter;
  final ExecutionCancellation? cancellation;
  final Map<String, List<int>> _received;

  AtlasDownloadState state;
  String failureDetail = '';

  static String keyOf(AtlasTileCoordinate tile) =>
      '${tile.z}/${tile.x}/${tile.y}';

  AtlasDownloadProgress get progress => AtlasDownloadProgress(
        received: _received.length,
        planned: tiles.length,
        bytes: _received.values.fold(0, (sum, bytes) => sum + bytes.length),
      );

  Map<String, List<int>> get received => Map.unmodifiable(_received);

  void pause() {
    if (state == AtlasDownloadState.downloading) {
      state = AtlasDownloadState.paused;
    }
  }

  void discard() {
    _received.clear();
    failureDetail = '';
    state = AtlasDownloadState.planned;
  }

  Future<AtlasDownloadState> download(int nowSeconds) async {
    if (state == AtlasDownloadState.complete ||
        state == AtlasDownloadState.cancelled ||
        state == AtlasDownloadState.failed) {
      return state;
    }
    state = AtlasDownloadState.downloading;
    for (final tile in tiles) {
      if (state != AtlasDownloadState.downloading) return state;
      if (cancellation?.isCancelled ?? false) {
        state = AtlasDownloadState.cancelled;
        return state;
      }
      if (limiter != null && !limiter!.take(nowSeconds)) {
        state = AtlasDownloadState.quotaPaused;
        return state;
      }
      final key = keyOf(tile);
      if (_received.containsKey(key)) continue;
      try {
        _received[key] = await source(tile);
      } catch (error) {
        state = AtlasDownloadState.failed;
        failureDetail = '${error.runtimeType}: $error';
        return state;
      }
    }
    state = AtlasDownloadState.complete;
    return state;
  }
}
