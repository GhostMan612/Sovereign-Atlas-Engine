// Sovereign Atlas — first host shell, offline rendering seam.
//
// AtlasOfflineTileProvider: the smallest renderer-side adapter between the
// offline store and flutter_map. Resolution order per tile:
//   1. Local hit → MemoryImage over repository bytes (engine index entry
//      must be resident — the repository gates this, never this file).
//   2. Local miss → the standard flutter_map network provider (URL
//      mechanics stay flutter_map-side here exactly as before; the pack
//      DOWNLOAD path still resolves URLs engine-side — no bypass either
//      way, no ad-hoc fetching invented in the UI).
//   3. Fallback construction/invocation throws (e.g. transport blocked) →
//      transparent tile (documented local-only degradation: missing tiles
//      degrade, served tiles render; nothing is ever faked).
//
// The provider is renderer-owned (flutter_map disposes it per TileLayer
// lifecycle); knowledge (what is available offline) stays repository/engine
// side. Counters live on the repository (hits + network requests) so they
// survive provider rebuilds and feed Diagnostics.

import 'package:flutter/widgets.dart';
import 'package:flutter_map/flutter_map.dart';

import 'offline_repository.dart';

/// Store-first tile provider with standard network fallback.
final class AtlasOfflineTileProvider extends TileProvider {
  AtlasOfflineTileProvider({
    required this.repository,
    required this.providerId,
    this._networkFallback,
    super.headers,
  });

  /// Engine provider id whose completed packs may serve (the map layer and
  /// the pack set must agree — the TileLayer key already rebuilds on
  /// provider switch, so this instance never serves stale-provider bytes).
  final String providerId;

  final OfflineRepository repository;

  /// Lazy network fallback (null in production until first miss; injected
  /// in tests). Laziness matters: under a blocked transport even client
  /// CONSTRUCTION throws, which must degrade per-tile, never at build.
  TileProvider? _networkFallback;

  TileProvider get _fallback =>
      _networkFallback ??= NetworkTileProvider();

  @override
  bool get supportsCancelLoading => true;

  @override
  ImageProvider getImageWithCancelLoadingSupport(
    TileCoordinates coordinates,
    TileLayer options,
    Future<void> cancelLoading,
  ) {
    final key = '${coordinates.z}/${coordinates.x}/${coordinates.y}';
    final bytes = repository.resolveTileBytes(providerId, key);
    if (bytes != null) return MemoryImage(bytes);
    repository.recordNetworkRequest();
    try {
      return _fallback.getImageWithCancelLoadingSupport(
        coordinates,
        options,
        cancelLoading,
      );
    } catch (_) {
      // Transport itself unavailable (blocked client construction, dead
      // stack): local-only degradation — transparent for the missing tile.
      return MemoryImage(TileProvider.transparentImage);
    }
  }

  @override
  Future<void> dispose() async {
    _networkFallback?.dispose();
    super.dispose();
  }
}
