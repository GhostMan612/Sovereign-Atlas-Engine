// Sovereign Atlas — Offline Areas track tests.
//
// Repository contract proofs with injected fakes (deterministic clock +
// scripted chunk sources). Every engine decision (refusals, terminals, seal,
// store indexing) is asserted through the app orchestrator — the wiring,
// not the engine, is what's under test (engine behavior is fixture-proven).

import 'dart:async';
import 'dart:convert';

import 'package:atlas/offline/offline_page.dart';
import 'package:atlas/offline/offline_pack.dart';
import 'package:atlas/offline/offline_repository.dart';
import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_offline/atlas_offline.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:flutter_test/flutter_test.dart';

OfflineRepository testRepo({
  AtlasChunkSource? source,
  int now = 1000,
  int Function()? clock,
}) {
  return OfflineRepository(
    registry: AtlasBuiltinProviders.registry(),
    chunkSourceFactory: (_) =>
        source ?? (tile) async => [tile.z, tile.x, tile.y],
    clock: clock ?? () => now,
  );
}

OfflinePackRecord planOne(
  OfflineRepository repo, {
  String provider = 'esri-imagery',
  int bytes = 100,
}) {
  return repo.planPack(
    providerId: provider,
    zMin: 0,
    zMax: 0,
    xMin: 0,
    xMax: 0,
    yMin: 0,
    yMax: 0,
    bytesPerTile: bytes,
  );
}

void main() {
  group('pure formatting (no thresholds invented)', () {
    test('formatAge shows raw age, never stale', () {
      expect(formatAge(5), '5s');
      expect(formatAge(90), '1m');
      expect(formatAge(7200), '2h');
      expect(formatAge(90000), '1d');
      expect(formatAge(-3), 'clock-skew');
    });

    test('formatBytes uses binary units', () {
      expect(formatBytes(512), '512 B');
      expect(formatBytes(2048), '2.0 KiB');
      expect(formatBytes(2097152), '2.00 MiB');
    });

    test('countTiles saturates without enumerating', () {
      expect(
        OfflineRepository.countTiles(
          zMin: 0,
          zMax: 0,
          xMin: 0,
          xMax: 0,
          yMin: 0,
          yMax: 0,
        ),
        1,
      );
      // Would enumerate ~1.5B tiles if attempted: must saturate instead.
      expect(
        OfflineRepository.countTiles(
          zMin: 0,
          zMax: 19,
          xMin: 0,
          xMax: 524287,
          yMin: 0,
          yMax: 524287,
        ),
        kMaxSessionTiles + 1,
      );
    });
  });

  group('planning: approvals, refusals, blocks', () {
    test('approved plan carries engine tile list + explicit estimate', () {
      final repo = testRepo();
      final record = planOne(repo);
      expect(record.refusal, isNull);
      expect(record.appBlock, isNull);
      expect(record.plan, isNotNull);
      expect(record.tileCount, 1);
      expect(record.estimatedBytes, 100);
      expect(record.lifecycle, OfflinePackLifecycle.planned);
      expect(repo.events, isNotEmpty);
    });

    test('OSM without bulk approval surfaces BULK_GUARD', () {
      final repo = testRepo();
      final record = planOne(repo, provider: 'osm-standard');
      expect(record.plan, isNull);
      expect(record.refusal?.reason, 'BULK_GUARD');
      expect(record.refusal!.detail, contains('Tile Usage Policy'));
    });

    test('OSM with approval plans (guard is consent-gated, not absolute)', () {
      final repo = testRepo();
      final record = repo.planPack(
        providerId: 'osm-standard',
        zMin: 0,
        zMax: 0,
        xMin: 0,
        xMax: 0,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
        approvedBulk: true,
      );
      expect(record.refusal, isNull);
      expect(record.tileCount, 1);
    });

    test('prefetch against OSM surfaces PREFETCH_REFUSED', () {
      final repo = testRepo();
      final record = repo.planPack(
        providerId: 'osm-standard',
        zMin: 0,
        zMax: 0,
        xMin: 0,
        xMax: 0,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
        approvedBulk: true,
        isPrefetch: true,
      );
      expect(record.refusal?.reason, 'PREFETCH_REFUSED');
    });

    test('incoherent range surfaces INVALID_RANGE', () {
      final repo = testRepo();
      final record = repo.planPack(
        providerId: 'esri-imagery',
        zMin: 2,
        zMax: 1,
        xMin: 0,
        xMax: 0,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
      );
      expect(record.refusal?.reason, 'INVALID_RANGE');
    });

    test('missing estimate blocks app-side (never defaulted)', () {
      final repo = testRepo();
      final record = planOne(repo, bytes: 0);
      expect(record.plan, isNull);
      expect(record.appBlock, contains('ESTIMATE_REQUIRED'));
    });

    test('unknown provider blocks app-side', () {
      final repo = testRepo();
      final record = planOne(repo, provider: 'no-such-provider');
      expect(record.appBlock, contains('UNKNOWN_PROVIDER'));
    });

    test('local bundle blocks app-side (no locator)', () {
      final repo = testRepo();
      final record = planOne(repo, provider: 'local-bundle');
      expect(record.appBlock, contains('NO_LOCATOR'));
    });

    test('huge ranges block at the session cap before enumeration', () {
      final repo = testRepo();
      final record = repo.planPack(
        providerId: 'esri-imagery',
        zMin: 0,
        zMax: 12,
        xMin: 0,
        xMax: 4095,
        yMin: 0,
        yMax: 4095,
        bytesPerTile: 100,
      );
      expect(record.plan, isNull);
      expect(record.appBlock, contains('APP_PACK_TOO_LARGE'));
    });

    test('engine PACK_TOO_LARGE propagates (custom capped endpoint)', () {
      const endpoint = AtlasProviderEndpoint(
        descriptor: AtlasProviderDescriptor(
          id: AtlasId('capped-demo'),
          kinds: {AtlasDataKind.rasterTiles},
          title: 'Capped Demo',
          capabilities: {AtlasProviderCapability.tileServing},
        ),
        policy: AtlasProviderPolicy(
          onlineAllowed: true,
          cacheAllowed: true,
          prefetchAllowed: true,
          maxTiles: 1,
        ),
        urlTemplate: 'https://example.invalid/{z}/{x}/{y}.png',
      );
      final repo = OfflineRepository(
        registry: AtlasProviderRegistry([endpoint]),
        clock: () => 1000,
      );
      final record = repo.planPack(
        providerId: 'capped-demo',
        zMin: 0,
        zMax: 0,
        xMin: 0,
        xMax: 1,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
      );
      expect(record.refusal?.reason, 'PACK_TOO_LARGE');
    });
  });

  group('download lifecycle over fake sources', () {
    test('complete: seal, manifest round-trip, store index entry', () async {
      final repo = testRepo();
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      expect(record.lifecycle, OfflinePackLifecycle.complete);
      expect(record.receivedTiles, 1);
      expect(record.receivedBytes, 3); // [z, x, y]
      expect(record.seal, matches(RegExp(r'^[0-9a-f]{16}$')));
      expect(record.cacheEntryPresent, isTrue);
      expect(repo.store.stats.entryCount, 1);

      final manifest = AtlasPackManifest.fromJson(
        (jsonDecode(record.manifestJson!) as Map).cast<String, dynamic>(),
      );
      expect(manifest.seal, record.seal);
      expect(manifest.entryCount, 1);
      expect(manifest.entries.single.address, '0/0/0');
    });

    test('chunk throw surfaces failed with engine detail', () async {
      final repo = testRepo(
        source: (_) async => throw StateError('boom'),
      );
      final record = repo.planPack(
        providerId: 'esri-imagery',
        zMin: 0,
        zMax: 0,
        xMin: 0,
        xMax: 1,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
      );
      await repo.startDownload(record.packId);
      expect(record.lifecycle, OfflinePackLifecycle.failed);
      expect(record.failureDetail, contains('boom'));
      expect(repo.store.stats.entryCount, 0);
    });

    test('pause between chunks then resume completes', () async {
      final gate = Completer<void>();
      var calls = 0;
      final repo = testRepo(
        source: (tile) async {
          calls += 1;
          if (calls == 1) await gate.future;
          return [tile.z];
        },
      );
      final record = repo.planPack(
        providerId: 'esri-imagery',
        zMin: 0,
        zMax: 0,
        xMin: 0,
        xMax: 1,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
      );
      final future = repo.startDownload(record.packId);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      repo.pauseDownload(record.packId);
      gate.complete();
      await future;
      expect(record.lifecycle, OfflinePackLifecycle.paused);
      expect(record.receivedTiles, 1);

      await repo.startDownload(record.packId);
      expect(record.lifecycle, OfflinePackLifecycle.complete);
      expect(record.receivedTiles, 2);
    });

    test('cancel between chunks holds bytes, reports cancelled', () async {
      final gate = Completer<void>();
      var calls = 0;
      final repo = testRepo(
        source: (_) async {
          calls += 1;
          if (calls == 1) await gate.future;
          return [9];
        },
      );
      final record = repo.planPack(
        providerId: 'esri-imagery',
        zMin: 0,
        zMax: 0,
        xMin: 0,
        xMax: 1,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
      );
      final future = repo.startDownload(record.packId);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      repo.cancelDownload(record.packId);
      gate.complete();
      await future;
      expect(record.lifecycle, OfflinePackLifecycle.cancelled);
      expect(record.receivedTiles, 1); // bytes held for resume
    });

    test('quota pause then resume after refill completes', () async {
      // Centralized limiter (capacity 2, refill 1/s): pack A spends both
      // tokens completing; pack B pauses immediately on the empty bucket;
      // after one refill second pack B resumes to completion. This is the
      // recoverable shape (shared bucket refills across packs); a per-pack
      // limiter could never resume past its own pause (resume re-walks
      // every tile at one take per iteration against a capped bucket).
      var now = 1000;
      final repo = OfflineRepository(
        registry: AtlasBuiltinProviders.registry(),
        chunkSourceFactory: (_) => (tile) async => [tile.z],
        clock: () => now,
        sharedLimiter:
            AtlasRateLimiter(capacity: 2, refillPerSecond: 1),
      );
      OfflinePackRecord planTwo() => repo.planPack(
            providerId: 'esri-imagery',
            zMin: 0,
            zMax: 0,
            xMin: 0,
            xMax: 1,
            yMin: 0,
            yMax: 0,
            bytesPerTile: 100,
          );
      final first = planTwo();
      await repo.startDownload(first.packId);
      expect(first.lifecycle, OfflinePackLifecycle.complete);

      final second = planTwo();
      await repo.startDownload(second.packId);
      expect(second.lifecycle, OfflinePackLifecycle.quotaPaused);
      expect(second.receivedTiles, 0);
      expect(second.failureDetail, contains('quota'));

      now += 2; // two tokens refill the shared bucket (one per tile)
      await repo.startDownload(second.packId);
      expect(second.lifecycle, OfflinePackLifecycle.complete);
      expect(second.receivedTiles, 2);
    });

    test('delete drops record, bytes, and index entry', () async {
      final repo = testRepo();
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      expect(repo.store.stats.entryCount, 1);
      repo.deletePack(record.packId);
      expect(repo.lookup(record.packId), isNull);
      expect(repo.store.stats.entryCount, 0);
      expect(repo.packs, isEmpty);
    });

    test('clearStore keeps records, flags index entries gone', () async {
      final repo = testRepo();
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      repo.clearStore();
      expect(repo.store.stats.entryCount, 0);
      expect(repo.lookup(record.packId), isNotNull);
      expect(record.cacheEntryPresent, isFalse);
      expect(record.seal, isNotNull); // bytes + seal survive; index is gone
    });

    test('event log is bounded and newest-visible', () async {
      final repo = testRepo();
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      repo.deletePack(record.packId);
      expect(repo.events.length, lessThanOrEqualTo(kEventLogBound));
      expect(repo.events.first, contains('deleted'));
    });
  });
}
