// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:atlas/offline/offline_pack.dart';
import 'package:atlas/offline/offline_repository.dart';
import 'package:atlas/offline/offline_tile_provider.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';

final class FakeFallback extends TileProvider {
  int calls = 0;
  TileCoordinates? lastCoordinates;

  @override
  bool get supportsCancelLoading => true;

  @override
  ImageProvider getImageWithCancelLoadingSupport(
    TileCoordinates coordinates,
    TileLayer options,
    Future<void> cancelLoading,
  ) {
    calls += 1;
    lastCoordinates = coordinates;
    return MemoryImage(Uint8List.fromList(const [1, 2, 3]));
  }
}

final class ThrowingFallback extends TileProvider {
  @override
  bool get supportsCancelLoading => true;

  @override
  ImageProvider getImageWithCancelLoadingSupport(
    TileCoordinates coordinates,
    TileLayer options,
    Future<void> cancelLoading,
  ) {
    throw const SocketException('transport dead');
  }
}

OfflineRepository seededRepo(Directory dir) {
  return OfflineRepository(
    registry: AtlasBuiltinProviders.registry(),
    chunkSourceFactory: (_) => (tile) async => [tile.z, tile.x, tile.y],
    clock: () => 1000,
    directoryProvider: () async => dir,
  );
}

TileLayer testLayer() => TileLayer(
      urlTemplate: 'https://example.invalid/{z}/{x}/{y}.png',
    );

void main() {
  late Directory dir;

  setUp(() {
    dir = Directory.systemTemp.createTempSync('atlas_seam_');
  });

  tearDown(() {
    if (dir.existsSync()) dir.deleteSync(recursive: true);
  });

  Future<OfflinePackRecord> completeOne(OfflineRepository repo) async {
    final record = repo.planPack(
      providerId: 'esri-imagery',
      zMin: 0,
      zMax: 0,
      xMin: 0,
      xMax: 0,
      yMin: 0,
      yMax: 0,
      bytesPerTile: 100,
    );
    await repo.startDownload(record.packId);
    expect(record.lifecycle, OfflinePackLifecycle.complete);
    return record;
  }

  test('local hit serves pack bytes as MemoryImage', () async {
    final repo = seededRepo(dir);
    await completeOne(repo);
    final fallback = FakeFallback();
    final provider = AtlasOfflineTileProvider(
      repository: repo,
      providerId: 'esri-imagery',
      networkFallback: fallback,
    );
    final image = provider.getImageWithCancelLoadingSupport(
      const TileCoordinates(0, 0, 0),
      testLayer(),
      Completer<void>().future,
    );
    expect(image, isA<MemoryImage>());
    expect((image as MemoryImage).bytes, [0, 0, 0]);
    expect(repo.offlineTileHits, 1);
    expect(fallback.calls, 0);
    expect(repo.networkTileRequests, 0);
  });

  test('local miss delegates to the network fallback', () async {
    final repo = seededRepo(dir);
    await completeOne(repo);
    final fallback = FakeFallback();
    final provider = AtlasOfflineTileProvider(
      repository: repo,
      providerId: 'esri-imagery',
      networkFallback: fallback,
    );
    final image = provider.getImageWithCancelLoadingSupport(
      const TileCoordinates(1, 1, 0),
      testLayer(),
      Completer<void>().future,
    );
    expect(image, isA<MemoryImage>());
    expect((image as MemoryImage).bytes, [1, 2, 3]);
    expect(fallback.calls, 1);
    expect(fallback.lastCoordinates, const TileCoordinates(1, 1, 0));
    expect(repo.networkTileRequests, 1);
    expect(repo.offlineTileHits, 0);
  });

  test('unindexed bytes never serve (knowledge gate)', () async {
    final repo = seededRepo(dir);
    await completeOne(repo);

    repo.store.clear();
    final fallback = FakeFallback();
    final provider = AtlasOfflineTileProvider(
      repository: repo,
      providerId: 'esri-imagery',
      networkFallback: fallback,
    );
    provider.getImageWithCancelLoadingSupport(
      const TileCoordinates(0, 0, 0),
      testLayer(),
      Completer<void>().future,
    );
    expect(fallback.calls, 1);
    expect(repo.offlineTileHits, 0);
  });

  test('dead transport degrades to transparent (local-only)', () async {
    final repo = seededRepo(dir);
    await completeOne(repo);
    final provider = AtlasOfflineTileProvider(
      repository: repo,
      providerId: 'esri-imagery',
      networkFallback: ThrowingFallback(),
    );

    final hit = provider.getImageWithCancelLoadingSupport(
      const TileCoordinates(0, 0, 0),
      testLayer(),
      Completer<void>().future,
    );
    expect((hit as MemoryImage).bytes, [0, 0, 0]);

    final miss = provider.getImageWithCancelLoadingSupport(
      const TileCoordinates(1, 1, 0),
      testLayer(),
      Completer<void>().future,
    );
    expect(miss, isA<MemoryImage>());
    expect(
      (miss as MemoryImage).bytes,
      TileProvider.transparentImage,
    );
  });

  test('provider isolation: no cross-provider byte leakage', () async {
    final repo = seededRepo(dir);
    await completeOne(repo);
    final fallback = FakeFallback();
    final provider = AtlasOfflineTileProvider(
      repository: repo,
      providerId: 'osm-standard',
      networkFallback: fallback,
    );
    provider.getImageWithCancelLoadingSupport(
      const TileCoordinates(0, 0, 0),
      testLayer(),
      Completer<void>().future,
    );
    expect(fallback.calls, 1);
    expect(repo.offlineTileHits, 0);
  });
}
