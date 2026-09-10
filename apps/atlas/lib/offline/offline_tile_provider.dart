// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_offline/atlas_offline.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_map/flutter_map.dart';

import 'offline_repository.dart';

final class AtlasOfflineTileProvider extends TileProvider {
  AtlasOfflineTileProvider({
    required this.repository,
    required this.providerId,
    this._networkFallback,
    super.headers,
  });

  final String providerId;

  final OfflineRepository repository;

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

    final key = AtlasPackDownloader.keyOf(
      AtlasTileCoordinate(
        z: coordinates.z,
        x: coordinates.x,
        y: coordinates.y,
      ),
    );
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

      return MemoryImage(TileProvider.transparentImage);
    }
  }

  @override
  Future<void> dispose() async {
    _networkFallback?.dispose();
    super.dispose();
  }
}
