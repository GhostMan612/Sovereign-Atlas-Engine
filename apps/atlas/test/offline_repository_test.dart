// Sovereign Atlas — Offline Areas track tests.
//
// Repository contract proofs with injected fakes (deterministic clock +
// scripted chunk sources). Every engine decision (refusals, terminals, seal,
// store indexing) is asserted through the app orchestrator — the wiring,
// not the engine, is what's under test (engine behavior is fixture-proven).

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:atlas/offline/offline_page.dart';
import 'package:atlas/offline/offline_pack.dart';
import 'package:atlas/offline/offline_repository.dart';
import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_offline/atlas_offline.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:atlas_tiles/atlas_tiles.dart';
import 'package:flutter_test/flutter_test.dart';

OfflineRepository testRepo({
  AtlasChunkSource? source,
  int now = 1000,
  int Function()? clock,
  Directory? dir,
  AtlasRateLimiter? sharedLimiter,
  Duration? perTileTimeout,
}) {
  return OfflineRepository(
    registry: AtlasBuiltinProviders.registry(),
    chunkSourceFactory: (_) =>
        source ?? (tile) async => [tile.z, tile.x, tile.y],
    clock: clock ?? () => now,
    // Isolated journal per repository (never touches the host docs dir).
    directoryProvider: () async =>
        dir ?? Directory.systemTemp.createTempSync('atlas_repo_'),
    sharedLimiter: sharedLimiter,
    perTileTimeout: perTileTimeout ?? kDefaultPerTileTimeout,
  );
}

/// A transport that accepts and never answers (deterministic stall — the
/// shape of the observed Esri incident, without any network).
AtlasChunkSource get stalledSource =>
    (_) => Completer<List<int>>().future;

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
      // Wording must describe the true rationale (bounded enumeration +
      // RAM serve map; completed packs persist) — never "future work".
      expect(record.appBlock, contains('journal'));
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
      final repo = testRepo(
        clock: () => now,
        sharedLimiter: AtlasRateLimiter(capacity: 2, refillPerSecond: 1),
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

    test('stalled tile reaches failed with TimeoutException identity',
        () async {
      final repo = testRepo(
        source: stalledSource,
        perTileTimeout: const Duration(milliseconds: 50),
      );
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      // DEC-020 mapping: existing failed terminal, timeout identity kept
      // in the detail (no new state, no taxonomy change).
      expect(record.lifecycle, OfflinePackLifecycle.failed);
      expect(record.failureDetail, contains('TimeoutException'));
      // No false completion, no false indexing, nothing served.
      expect(repo.store.stats.entryCount, 0);
      expect(repo.resolveTileBytes('esri-imagery', '0/0/0'), isNull);
      expect(record.seal, isNull);
    });

    test('timeout preserves received tiles; resume completes', () async {
      var stallSecond = true;
      final repo = testRepo(
        source: (tile) async {
          if (tile.x == 1 && stallSecond) {
            await Completer<void>().future;
          }
          return [tile.z];
        },
        perTileTimeout: const Duration(milliseconds: 50),
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
      expect(record.failureDetail, contains('TimeoutException'));
      // The completed tile survives; only the stalled in-flight one is lost.
      expect(record.receivedTiles, 1);
      stallSecond = false;
      await repo.startDownload(record.packId);
      expect(record.lifecycle, OfflinePackLifecycle.complete);
      expect(record.receivedTiles, 2);
      expect(record.seal, isNotNull);
    });

    test('cancel wins over a pending timeout', () async {
      final repo = testRepo(
        source: stalledSource,
        perTileTimeout: const Duration(milliseconds: 80),
      );
      final record = planOne(repo);
      final future = repo.startDownload(record.packId);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      // Cancel lands mid-await: the engine cannot observe it there, so the
      // timeout fires — but the app records the user's act, not the clock.
      repo.cancelDownload(record.packId);
      await future;
      expect(record.lifecycle, OfflinePackLifecycle.cancelled);
      expect(record.failureDetail, isEmpty);
    });

    test('delete during stall settles promptly (wedge closed)', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_repo_');
      final repo = testRepo(
        dir: dir,
        source: stalledSource,
        perTileTimeout: const Duration(milliseconds: 50),
      );
      final record = planOne(repo);
      final watch = Stopwatch()..start();
      final future = repo.startDownload(record.packId);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      await repo.deletePack(record.packId);
      // Without the bound this await would hang forever (the observed
      // wedge: finally never runs, restart silently ignored).
      await future;
      watch.stop();
      expect(watch.elapsed, lessThan(const Duration(seconds: 5)));
      expect(repo.lookup(record.packId), isNull);
      expect(repo.packs, isEmpty);
      expect(repo.store.stats.entryCount, 0);
      // And the restart path is alive afterwards (no stale running entry:
      // a fresh download runs to its own terminal instead of being
      // silently ignored). The source still stalls, so that terminal is
      // failed — the point is that it TERMINATES.
      expect(repo.isDownloading, isFalse);
      final again = planOne(repo);
      await repo.startDownload(again.packId);
      expect(again.lifecycle, OfflinePackLifecycle.failed);
      expect(again.failureDetail, contains('TimeoutException'));
    });

    test('delete during download is authoritative (no resurrection)',
        () async {
      final dir = Directory.systemTemp.createTempSync('atlas_repo_');
      final gate = Completer<void>();
      var calls = 0;
      final repo = testRepo(
        dir: dir,
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
      // Delete while the first chunk is still gated.
      await repo.deletePack(record.packId);
      expect(repo.lookup(record.packId), isNull);
      gate.complete();
      // The orphaned future must settle WITHOUT throwing and WITHOUT
      // re-registering anything.
      await future;
      expect(repo.lookup(record.packId), isNull);
      expect(repo.packs, isEmpty);
      expect(repo.store.stats.entryCount, 0);
      expect(
        Directory('${dir.path}/offline_packs/${record.packId}').existsSync(),
        isFalse,
      );
      expect(repo.resolveTileBytes('esri-imagery', '0/0/0'), isNull);
      expect(repo.events.join('\n'), contains('settled after delete'));
      // Repository remains usable.
      final again = planOne(repo);
      await repo.startDownload(again.packId);
      expect(again.lifecycle, OfflinePackLifecycle.complete);
    });

    test('delete during persist window is authoritative', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_repo_');
      final persistGate = Completer<void>();
      final repo = OfflineRepository(
        registry: AtlasBuiltinProviders.registry(),
        chunkSourceFactory: (_) => (tile) async => [tile.z],
        clock: () => 1000,
        directoryProvider: () async {
          await persistGate.future;
          return dir;
        },
      );
      final record = planOne(repo);
      final download = repo.startDownload(record.packId);
      // Let the chunk loop finish so the flow parks inside _persistPack.
      await Future<void>.delayed(const Duration(milliseconds: 20));
      // Delete now: store entry removed synchronously, then both paths
      // park at the same journal gate.
      final removal = repo.deletePack(record.packId);
      expect(repo.lookup(record.packId), isNull);
      persistGate.complete();
      await download;
      await removal;
      // The post-persist ownership checkpoint must undo the completion's
      // registrations: no resurrection through the persist window either.
      expect(repo.lookup(record.packId), isNull);
      expect(repo.packs, isEmpty);
      expect(repo.store.stats.entryCount, 0);
      expect(
        Directory('${dir.path}/offline_packs/${record.packId}').existsSync(),
        isFalse,
      );
      expect(repo.resolveTileBytes('esri-imagery', '0/0/0'), isNull);
      expect(
        repo.events.join('\n'),
        contains('discarded: deleted during persist'),
      );
    });

    test('LRU eviction unflags the victim (engine-reported)', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_repo_');
      final repo = OfflineRepository(
        registry: AtlasBuiltinProviders.registry(),
        store: AtlasMemoryStore(capacity: 2),
        chunkSourceFactory: (_) => (tile) async => [tile.z],
        clock: () => 1000,
        directoryProvider: () async => dir,
      );
      Future<OfflinePackRecord> completeAt(int x) async {
        final record = repo.planPack(
          providerId: 'esri-imagery',
          zMin: 0,
          zMax: 0,
          xMin: x,
          xMax: x,
          yMin: 0,
          yMax: 0,
          bytesPerTile: 100,
        );
        await repo.startDownload(record.packId);
        expect(record.lifecycle, OfflinePackLifecycle.complete);
        return record;
      }

      final first = await completeAt(0);
      final second = await completeAt(1);
      expect(repo.store.stats.entryCount, 2);
      // Third insert evicts the oldest (first) — engine-reported, consumed.
      final third = await completeAt(2);
      expect(repo.store.stats.entryCount, 2);
      expect(first.cacheEntryPresent, isFalse);
      expect(second.cacheEntryPresent, isTrue);
      expect(third.cacheEntryPresent, isTrue);
      // The live gate agrees with the corrected flags.
      expect(repo.resolveTileBytes('esri-imagery', '0/0/0'), isNull);
      expect(repo.resolveTileBytes('esri-imagery', '0/2/0'), [0]);
      expect(repo.events.join('\n'), contains('evicted from index by LRU'));
    });

    test('journal-write failure reaches failed, repo stays usable', () async {
      var failJournal = true;
      final dir = Directory.systemTemp.createTempSync('atlas_repo_');
      final repo = OfflineRepository(
        registry: AtlasBuiltinProviders.registry(),
        chunkSourceFactory: (_) => (tile) async => [tile.z],
        clock: () => 1000,
        directoryProvider: () async {
          if (failJournal) throw const FileSystemException('disk gone');
          return dir;
        },
      );
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      expect(record.lifecycle, OfflinePackLifecycle.failed);
      expect(record.failureDetail, contains('PERSIST_FAILED'));
      expect(record.cacheEntryPresent, isFalse);
      // No false completion, no false indexing, nothing served.
      expect(repo.store.stats.entryCount, 0);
      expect(repo.resolveTileBytes('esri-imagery', '0/0/0'), isNull);
      // Recovery path: resume retries the persist once the journal heals.
      failJournal = false;
      await repo.startDownload(record.packId);
      expect(record.lifecycle, OfflinePackLifecycle.complete);
      expect(record.cacheEntryPresent, isTrue);
      // And unrelated packs work throughout.
      final other = repo.planPack(
        providerId: 'esri-imagery',
        zMin: 1,
        zMax: 1,
        xMin: 0,
        xMax: 0,
        yMin: 0,
        yMax: 0,
        bytesPerTile: 100,
      );
      await repo.startDownload(other.packId);
      expect(other.lifecycle, OfflinePackLifecycle.complete);
    });

    test('delete drops record, bytes, index entry, and disk journal',
        () async {
      final dir = Directory.systemTemp.createTempSync('atlas_repo_');
      final repo = testRepo(dir: dir);
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      expect(repo.store.stats.entryCount, 1);
      expect(
        Directory('${dir.path}/offline_packs/${record.packId}').existsSync(),
        isTrue,
      );
      await repo.deletePack(record.packId);
      expect(repo.lookup(record.packId), isNull);
      expect(repo.store.stats.entryCount, 0);
      expect(repo.packs, isEmpty);
      expect(
        Directory('${dir.path}/offline_packs/${record.packId}').existsSync(),
        isFalse,
      );
      expect(repo.resolveTileBytes('esri-imagery', '0/0/0'), isNull);
    });

    test('clearStore evicts everything, keeps records as history', () async {
      final repo = testRepo();
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      await repo.clearStore();
      expect(repo.store.stats.entryCount, 0);
      expect(repo.lookup(record.packId), isNotNull);
      expect(record.cacheEntryPresent, isFalse);
      // Eviction is total: bytes, seal, and manifest go with the index
      // (half-held state would make "available offline" a lie).
      expect(record.bytesHeld, isFalse);
      expect(record.seal, isNull);
      expect(record.manifestJson, isNull);
      expect(record.tileKeys, isEmpty);
      expect(record.receivedTiles, 0);
      expect(repo.resolveTileBytes('esri-imagery', '0/0/0'), isNull);
    });

    test('event log is bounded and newest-visible', () async {
      final repo = testRepo();
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      await repo.deletePack(record.packId);
      expect(repo.events.length, lessThanOrEqualTo(kEventLogBound));
      expect(repo.events.first, contains('deleted'));
    });
  });

  group('disk journal + relaunch restore', () {
    Future<(OfflineRepository, OfflinePackRecord, Directory)> completeIn(
      Directory dir,
    ) async {
      final repo = testRepo(dir: dir);
      final record = planOne(repo);
      await repo.startDownload(record.packId);
      expect(record.lifecycle, OfflinePackLifecycle.complete);
      return (repo, record, dir);
    }

    test('completion persists tile files + index', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final (_, record, _) = await completeIn(dir);
      final packDir =
          Directory('${dir.path}/offline_packs/${record.packId}');
      expect(packDir.existsSync(), isTrue);
      expect(
        packDir
            .listSync()
            .whereType<File>()
            .map((f) => f.path.split(Platform.pathSeparator).last)
            .toList(),
        ['0_0_0.tile'],
      );
      expect(
        File('${dir.path}/offline_packs/$kPackIndexFile').existsSync(),
        isTrue,
      );
    });

    test('fresh repository restores packs from disk (relaunch)', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final (repo, record, _) = await completeIn(dir);

      final relaunched = testRepo(dir: dir);
      expect(relaunched.packs, isEmpty);
      await relaunched.restore();

      final restored = relaunched.lookup(record.packId);
      expect(restored, isNotNull);
      expect(restored!.lifecycle, OfflinePackLifecycle.complete);
      expect(restored.seal, record.seal);
      expect(restored.tileKeys, {'0/0/0'});
      expect(restored.receivedTiles, 1);
      expect(relaunched.store.stats.entryCount, 1);
      // Bytes resolve through the relaunched instance (same code path the
      // renderer uses after a real process restart).
      expect(
        relaunched.resolveTileBytes('esri-imagery', '0/0/0'),
        [0, 0, 0],
      );
      expect(relaunched.offlineTileHits, 1);
      // Manifest export shape survives the journal round-trip.
      final manifest = AtlasPackManifest.fromJson(
        (jsonDecode(restored.manifestJson!) as Map).cast<String, dynamic>(),
      );
      expect(manifest.seal, record.seal);
      expect(repo.offlineTileHits, 0); // untouched original
    });

    test('restore with no journal is a logged no-op', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final repo = testRepo(dir: dir);
      await repo.restore();
      expect(repo.packs, isEmpty);
      expect(repo.events.first, contains('no journal'));
    });

    test('restore skips packs with missing tile files', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final (_, record, _) = await completeIn(dir);
      File('${dir.path}/offline_packs/${record.packId}/0_0_0.tile')
          .deleteSync();
      final relaunched = testRepo(dir: dir);
      await relaunched.restore();
      expect(relaunched.lookup(record.packId), isNull);
      expect(relaunched.store.stats.entryCount, 0);
      expect(relaunched.events.join('\n'), contains('skipped'));
    });

    test('restore skips packs exceeding the session cap', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final journal = Directory('${dir.path}/offline_packs')..createSync();
      final keys = [
        for (var i = 0; i < kMaxSessionTiles + 1; i++) '0/$i/0',
      ];
      File('${journal.path}/$kPackIndexFile').writeAsStringSync(
        jsonEncode([
          {
            'pack_id': 'pack-evil',
            'provider': 'esri-imagery',
            'zoom_min': 0,
            'zoom_max': 0,
            'x_min': 0,
            'x_max': kMaxSessionTiles,
            'y_min': 0,
            'y_max': 0,
            'bytes_per_tile': 1,
            'created_at': 1000,
            'tile_count': keys.length,
            'keys': keys,
          },
        ]),
      );
      final repo = testRepo(dir: dir);
      await repo.restore();
      expect(repo.lookup('pack-evil'), isNull);
      expect(repo.events.join('\n'), contains('exceeds session cap'));
    });

    test('restore skips an unparseable index wholesale', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final journal = Directory('${dir.path}/offline_packs')..createSync();
      File('${journal.path}/$kPackIndexFile')
          .writeAsStringSync('this is not json');
      final repo = testRepo(dir: dir);
      await repo.restore();
      expect(repo.packs, isEmpty);
      expect(repo.events.first, contains('unparseable'));
    });

    test('mixed journal: valid restores, corrupt skips with logs', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final (_, record, _) = await completeIn(dir);
      final indexFile = File('${dir.path}/offline_packs/$kPackIndexFile');
      final entries =
          (jsonDecode(indexFile.readAsStringSync()) as List).toList();
      entries.add(42); // not an object: must not abort the restore
      entries.add({'pack_id': 'pack-ghost'}); // missing keys: malformed
      indexFile.writeAsStringSync(jsonEncode(entries));

      final repo = testRepo(dir: dir);
      await repo.restore();
      final restored = repo.lookup(record.packId);
      expect(restored, isNotNull);
      expect(restored!.lifecycle, OfflinePackLifecycle.complete);
      expect(restored.seal, record.seal);
      // No half-loaded index state: exactly the valid pack is indexed.
      expect(repo.store.stats.entryCount, 1);
      expect(repo.lookup('pack-ghost'), isNull);
      final log = repo.events.join('\n');
      expect(log, contains('not an object'));
      expect(log, contains('malformed'));
      // Repository remains usable after the mixed restore.
      final next = planOne(repo);
      await repo.startDownload(next.packId);
      expect(next.lifecycle, OfflinePackLifecycle.complete);
      expect(repo.store.stats.entryCount, 2);
    });

    test('restore with dead directory provider logs and stays usable',
        () async {
      final repo = OfflineRepository(
        registry: AtlasBuiltinProviders.registry(),
        chunkSourceFactory: (_) => (tile) async => [tile.z],
        clock: () => 1000,
        directoryProvider: () async =>
            throw const FileSystemException('no docs dir'),
      );
      await repo.restore(); // must not throw
      expect(repo.packs, isEmpty);
      expect(repo.events.first, contains('aborted on unexpected error'));
    });

    test('pack ids loop past restored collisions (no overwrite)', () async {
      final dir = Directory.systemTemp.createTempSync('atlas_journal_');
      final (_, first, _) = await completeIn(dir);
      expect(first.packId, 'pack-1000-1');

      // Same directory, same fixed clock: naive minting would collide.
      final repo = testRepo(dir: dir, clock: () => 1000);
      await repo.restore();
      expect(repo.lookup('pack-1000-1')!.seal, first.seal);

      final second = planOne(repo);
      expect(second.packId, 'pack-1000-2');
      final third = planOne(repo);
      expect(third.packId, 'pack-1000-3');
      // Original restored record intact; new records intact and distinct.
      expect(repo.lookup('pack-1000-1')!.seal, first.seal);
      expect(repo.lookup('pack-1000-2'), same(second));
      expect(repo.lookup('pack-1000-3'), same(third));
      // Repeated collisions keep walking safely.
      for (var i = 0; i < 3; i++) {
        planOne(repo);
      }
      expect(repo.lookup('pack-1000-1')!.seal, first.seal);
      expect(repo.packs.length, 1 + 2 + 3);
    });
  });
}
