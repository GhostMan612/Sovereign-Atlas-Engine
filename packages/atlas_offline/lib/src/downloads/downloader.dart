// Sovereign Atlas Engine — atlas_offline
// Sequential pack downloader: resumable/cancellable/quota-aware retrieval.
//
// Contract: blueprint 3.2 checklist (resumable, cancellable, progress,
// clean deletion via discard) + phase-3 note §5.
// - Chunk loop is SEQUENTIAL over an injected source (no concurrency — no
//   scheduler exists). Progress is a POLLED value (no callbacks, 2.0-D).
// - Resume = construct/download again with the received map (same path as
//   pause and quotaPause exits). Cancel reuses ExecutionCancellation
//   (tiles execution vocabulary — engine continuity, not a new model).
// - Terminals: complete (seal-verified self-consistency) / failed (chunk
//   throw) / cancelled / quotaPaused. Deletion = discard() (received
//   cleared, state back to planned).
// Phase 3 slice. Depends on core + provider_api + tiles (cancellation).

import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_tiles/atlas_tiles.dart';
import '../policy/rate_limiter.dart';

/// Injected chunk source: tile in, bytes out (or throw). Production wires
/// transports/bundles; tests wire scripted maps.
typedef AtlasChunkSource = Future<List<int>> Function(AtlasTileCoordinate tile);

/// Downloader lifecycle (planned doubles as fresh + discarded).
enum AtlasDownloadState {
  planned,
  downloading,
  paused,
  quotaPaused,
  complete,
  failed,
  cancelled,
}

/// Polled download progress value (no callbacks).
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

/// Sequential resumable download over explicit tile lists.
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

  /// Requests pause (observed between chunks; same resume path as quota).
  void pause() {
    if (state == AtlasDownloadState.downloading) {
      state = AtlasDownloadState.paused;
    }
  }

  /// Clears received bytes (clean deletion); state returns to planned.
  void discard() {
    _received.clear();
    failureDetail = '';
    state = AtlasDownloadState.planned;
  }

  /// Runs/resumes the sequential loop at explicit [nowSeconds]. Returns the
  /// terminal state reached by THIS call (resume again after pause/quota).
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
