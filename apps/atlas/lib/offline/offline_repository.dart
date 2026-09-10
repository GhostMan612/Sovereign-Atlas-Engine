// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_offline/atlas_offline.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:atlas_tiles/atlas_tiles.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import 'offline_pack.dart';

const int kPackStoreCapacity = 64;

const int kMaxSessionTiles = 4096;

const int kEventLogBound = 200;

const Duration kDefaultPerTileTimeout = Duration(seconds: 30);

const String kPackJournalDir = 'offline_packs';
const String kPackIndexFile = 'index.json';

AtlasChunkSource defaultChunkSource(AtlasProviderEndpoint endpoint) {
  final template = endpoint.urlTemplate!;
  return (tile) async {
    final url = AtlasTileRequest(
      identity: AtlasTileIdentity(
        provider: endpoint.descriptor.id,
        layer: const AtlasId(''),
        coordinate: tile,
      ),
      params: endpoint.params,
    ).resolveUrl(template);
    return httpTransport(Uri.parse(url), endpoint.headers);
  };
}

AtlasChunkSource withPerTileTimeout(AtlasChunkSource inner, Duration bound) {
  return (tile) => inner(tile).timeout(bound);
}

final class OfflineRepository extends ChangeNotifier {
  OfflineRepository({
    required this._registry,
    AtlasMemoryStore? store,
    AtlasChunkSource Function(AtlasProviderEndpoint)? chunkSourceFactory,
    int Function()? clock,
    this._sharedLimiter,
    Future<Directory> Function()? directoryProvider,
    this.perTileTimeout = kDefaultPerTileTimeout,
  })  : _store = store ?? AtlasMemoryStore(capacity: kPackStoreCapacity),
        _chunkSourceFactory = chunkSourceFactory ?? defaultChunkSource,
        _directoryProvider = directoryProvider ?? getApplicationDocumentsDirectory,
        _clock = clock ??
            (() => DateTime.now().millisecondsSinceEpoch ~/ 1000);

  final AtlasProviderRegistry _registry;
  final AtlasMemoryStore _store;
  final AtlasChunkSource Function(AtlasProviderEndpoint) _chunkSourceFactory;
  final AtlasRateLimiter? _sharedLimiter;
  final Future<Directory> Function() _directoryProvider;
  final int Function() _clock;

  final Duration perTileTimeout;

  final Map<String, OfflinePackRecord> _packs = {};
  final Map<String, Map<String, List<int>>> _receivedBytes = {};
  final Map<String, AtlasPackDownloader> _downloads = {};
  final Map<String, ExecutionCancellation> _cancellations = {};
  final List<String> _events = [];
  int _packSequence = 0;

  int _offlineTileHits = 0;
  int _networkTileRequests = 0;

  int get offlineTileHits => _offlineTileHits;
  int get networkTileRequests => _networkTileRequests;

  AtlasProviderRegistry get registry => _registry;
  AtlasMemoryStore get store => _store;

  final Map<String, Uint8List> _serveBytes = {};
  final Map<String, String> _servePacks = {};

  List<OfflinePackRecord> get packs => _packs.values.toList();

  List<String> get events => _events.reversed.toList();

  OfflinePackRecord? lookup(String packId) => _packs[packId];

  void _log(String message) {
    _events.add('${_clock()} $message');
    if (_events.length > kEventLogBound) {
      _events.removeRange(0, _events.length - kEventLogBound);
    }
  }

  static int countTiles({
    required int zMin,
    required int zMax,
    required int xMin,
    required int xMax,
    required int yMin,
    required int yMax,
  }) {
    var total = 0;
    for (var z = zMin; z <= zMax; z++) {
      final span = (xMax - xMin + 1) * (yMax - yMin + 1);
      total += span;
      if (total > kMaxSessionTiles) return kMaxSessionTiles + 1;
    }
    return total;
  }

  OfflinePackRecord planPack({
    required String providerId,
    required int zMin,
    required int zMax,
    required int xMin,
    required int xMax,
    required int yMin,
    required int yMax,
    required int bytesPerTile,
    bool approvedBulk = false,
    bool isPrefetch = false,
  }) {
    _packSequence += 1;
    final now = _clock();

    var packId = 'pack-$now-$_packSequence';
    while (_packs.containsKey(packId)) {
      _packSequence += 1;
      packId = 'pack-$now-$_packSequence';
    }
    final endpoint = _registry.lookup(providerId);
    final record = OfflinePackRecord(
      packId: packId,
      providerId: providerId,
      providerTitle:
          endpoint?.descriptor.title ?? providerId,
      zMin: zMin,
      zMax: zMax,
      xMin: xMin,
      xMax: xMax,
      yMin: yMin,
      yMax: yMax,
      bytesPerTile: bytesPerTile,
      approvedBulk: approvedBulk,
      isPrefetch: isPrefetch,
      createdAtEpoch: now,
    );
    _packs[packId] = record;

    if (endpoint == null) {
      record.appBlock = 'UNKNOWN_PROVIDER: "$providerId" is not registered.';
      _log('plan $packId blocked: unknown provider');
      notifyListeners();
      return record;
    }
    if (bytesPerTile <= 0) {

      record.appBlock =
          'ESTIMATE_REQUIRED: bytes-per-tile must be positive (no default '
          'estimate is ever assumed).';
      _log('plan $packId blocked: estimate required');
      notifyListeners();
      return record;
    }
    if (endpoint.isLocal) {

      record.appBlock =
          'NO_LOCATOR: "${endpoint.descriptor.title}" is bundle-backed with '
          'no URL template; pack download has no byte source.';
      _log('plan $packId blocked: local endpoint has no locator');
      notifyListeners();
      return record;
    }
    if (countTiles(
          zMin: zMin,
          zMax: zMax,
          xMin: xMin,
          xMax: xMax,
          yMin: yMin,
          yMax: yMax,
        ) >
        kMaxSessionTiles) {
      record.appBlock =
          'APP_PACK_TOO_LARGE: ranges exceed the session cap of '
          '$kMaxSessionTiles tiles (enumeration and the RAM serve map are '
          'bounded by the cap; completed packs persist to the journal). '
          'Narrow the ranges.';
      _log('plan $packId blocked: exceeds session tile cap');
      notifyListeners();
      return record;
    }
    final outcome = AtlasPackPlanner.plan(
      endpoint: endpoint,
      zMin: zMin,
      zMax: zMax,
      xMin: xMin,
      xMax: xMax,
      yMin: yMin,
      yMax: yMax,
      bytesPerTileEstimate: bytesPerTile,
      approvedBulk: approvedBulk,
      isPrefetch: isPrefetch,
    );
    if (outcome is AtlasPackRefusal) {
      record.refusal = outcome;
      _log('plan $packId refused: ${outcome.reason} ${outcome.detail}');
    } else {
      record.plan = outcome as AtlasPackPlan;
      _log('plan $packId approved: ${record.tileCount} tiles, '
          '${record.estimatedBytes} B est.');
    }
    notifyListeners();
    return record;
  }

  bool get isDownloading =>
      _downloads.values.any((d) => d.state == AtlasDownloadState.downloading);

  Future<void> startDownload(String packId) async {
    final record = _packs[packId];
    if (record == null || record.plan == null || record.appBlock != null) {
      return;
    }
    if (_downloads[packId] != null) return;
    final endpoint = _registry.lookup(record.providerId);
    if (endpoint == null) return;

    final cancellation = ExecutionCancellation();
    _cancellations[packId] = cancellation;
    final downloader = AtlasPackDownloader(
      tiles: record.plan!.tiles,
      source: withPerTileTimeout(
        _chunkSourceFactory(endpoint),
        perTileTimeout,
      ),
      limiter: _sharedLimiter,
      cancellation: cancellation,
      received: _receivedBytes.putIfAbsent(packId, () => {}),
    );
    _downloads[packId] = downloader;
    record.lifecycle = OfflinePackLifecycle.downloading;
    _log('download $packId started (${record.receivedTiles} already held)');
    notifyListeners();
    try {
      final terminal = await downloader.download(_clock());

      if (!_stillOwned(record)) {
        _log('download $packId settled after delete; result discarded');
        return;
      }
      final progress = downloader.progress;
      record.receivedTiles = progress.received;
      record.receivedBytes = progress.bytes;
      switch (terminal) {
        case AtlasDownloadState.complete:
          await _completePack(record, endpoint);
        case AtlasDownloadState.failed:

          if (_cancellations[packId]?.isCancelled == true &&
              downloader.failureDetail.startsWith('TimeoutException')) {
            record.lifecycle = OfflinePackLifecycle.cancelled;
            _log('download $packId cancelled (cancel won over timeout at '
                '${record.receivedTiles}/${record.tileCount})');
          } else {
            record.lifecycle = OfflinePackLifecycle.failed;
            record.failureDetail = downloader.failureDetail;
            _log('download $packId FAILED: ${downloader.failureDetail}');
          }
        case AtlasDownloadState.cancelled:
          record.lifecycle = OfflinePackLifecycle.cancelled;
          _log('download $packId cancelled at '
              '${record.receivedTiles}/${record.tileCount}');
        case AtlasDownloadState.paused:
          record.lifecycle = OfflinePackLifecycle.paused;
          _log('download $packId paused at '
              '${record.receivedTiles}/${record.tileCount}');
        case AtlasDownloadState.quotaPaused:
          record.lifecycle = OfflinePackLifecycle.quotaPaused;
          record.failureDetail =
              'Rate quota exhausted at ${_clock()}; resume to continue '
              '(same resume path as pause — engine contract).';
          _log('download $packId quota-paused at '
              '${record.receivedTiles}/${record.tileCount}');
        case AtlasDownloadState.planned:
        case AtlasDownloadState.downloading:

          break;
      }
    } finally {
      _downloads.remove(packId);
      _cancellations.remove(packId);
      notifyListeners();
    }
  }

  Future<void> _completePack(
    OfflinePackRecord record,
    AtlasProviderEndpoint endpoint,
  ) async {
    final held = _receivedBytes[record.packId] ?? const {};
    final keys = held.keys.toList()..sort();
    final entries = [
      for (final key in keys)
        AtlasPackEntry(address: key, checksum: fnv1a64(held[key]!)),
    ];
    final manifest = AtlasPackManifest(
      packId: AtlasId(record.packId),
      provider: endpoint.descriptor.id,
      zoomMin: record.zMin,
      zoomMax: record.zMax,
      createdAt: record.createdAtEpoch,
      entries: entries,

      attribution: endpoint.descriptor.attribution,
    );
    final check = manifest.validate();
    if (!check.isValid || manifest.seal.isEmpty) {
      record.lifecycle = OfflinePackLifecycle.failed;
      record.failureDetail =
          'MANIFEST_INVALID: assembled manifest failed self-verification.';
      _log('download ${record.packId} FAILED: manifest self-check');
      return;
    }
    record.seal = manifest.seal;
    record.manifestJson =
        const JsonEncoder.withIndent('  ').convert(manifest.toJson());
    record.tileKeys = keys.toSet();

    record.lifecycle = OfflinePackLifecycle.complete;
    final evicted = _store.put(
      AtlasCacheEntry(
        key: AtlasCacheKey(
          namespace: AtlasCacheNamespace.resource,
          value: record.packId,
        ),
        storedAt: _clock(),
        payloadId: AtlasId(manifest.seal),
      ),
    );

    if (evicted != null) _noteEvicted(evicted);
    try {
      await _persistPack(record);
    } catch (error) {

      _store.remove(
        AtlasCacheKey(
          namespace: AtlasCacheNamespace.resource,
          value: record.packId,
        ),
      );
      record.cacheEntryPresent = false;
      record.bytesHeld = false;
      record.lifecycle = OfflinePackLifecycle.failed;
      record.failureDetail = 'PERSIST_FAILED: $error';
      _rebuildServe();
      await _dropPackJournalQuietly(record.packId);
      _log('download ${record.packId} FAILED: journal write ($error)');
      return;
    }

    if (!_stillOwned(record)) {
      _store.remove(
        AtlasCacheKey(
          namespace: AtlasCacheNamespace.resource,
          value: record.packId,
        ),
      );
      await _dropPackJournalQuietly(record.packId);
      _log('complete ${record.packId} discarded: deleted during persist');
      return;
    }
    record.cacheEntryPresent = true;
    record.bytesHeld = true;
    _rebuildServe();
    _log('download ${record.packId} COMPLETE: ${record.receivedTiles} tiles, '
        '${record.receivedBytes} B, seal ${manifest.seal}');
  }

  bool _stillOwned(OfflinePackRecord record) =>
      identical(_packs[record.packId], record);

  void _noteEvicted(AtlasCacheEntry evicted) {
    final victim = _packs[evicted.key.value];
    if (victim == null || !victim.cacheEntryPresent) return;
    victim.cacheEntryPresent = false;
    _log('pack ${victim.packId} evicted from index by LRU (bytes unserved)');
  }

  Future<void> _dropPackJournalQuietly(String packId) async {
    try {
      final journal = await _journalDir();
      final dir = Directory('${journal.path}/$packId');
      if (await dir.exists()) await dir.delete(recursive: true);
      await _writeIndex(journal);
    } catch (_) {

    }
  }

  void pauseDownload(String packId) {
    _downloads[packId]?.pause();
  }

  void cancelDownload(String packId) {
    _cancellations[packId]?.requestCancel();
    _log('cancel requested for $packId');
    notifyListeners();
  }

  Future<void> deletePack(String packId) async {
    _cancellations[packId]?.requestCancel();
    _downloads[packId]?.pause();
    final record = _packs.remove(packId);
    _receivedBytes.remove(packId);
    _downloads.remove(packId);
    _cancellations.remove(packId);
    if (record != null) {
      _store.remove(
        AtlasCacheKey(
          namespace: AtlasCacheNamespace.resource,
          value: packId,
        ),
      );
      await _deletePackFiles(packId);
      _rebuildServe();
      _log('pack $packId deleted (index entry + RAM/disk bytes dropped)');
    }
    notifyListeners();
  }

  Future<void> clearStore() async {
    _store.clear();
    _receivedBytes.clear();
    for (final record in _packs.values) {
      record.cacheEntryPresent = false;
      record.bytesHeld = false;
      record.tileKeys = {};
      record.seal = null;
      record.manifestJson = null;
      record.receivedTiles = 0;
      record.receivedBytes = 0;
    }
    await _clearJournal();
    _rebuildServe();
    _log('store cleared by operator (${_packs.length} records kept, '
        'bytes evicted)');
    notifyListeners();
  }

  Uint8List? resolveTileBytes(String providerId, String tileKey) {
    final serveKey = '$providerId/$tileKey';
    final bytes = _serveBytes[serveKey];
    if (bytes == null) return null;
    final packId = _servePacks[serveKey]!;
    final entry = _store.get(
      AtlasCacheKey(namespace: AtlasCacheNamespace.resource, value: packId),
    );
    if (entry == null) return null;
    _offlineTileHits += 1;
    return bytes;
  }

  void recordNetworkRequest() {
    _networkTileRequests += 1;
  }

  void _rebuildServe() {
    _serveBytes.clear();
    _servePacks.clear();
    for (final record in _packs.values) {
      if (record.lifecycle != OfflinePackLifecycle.complete ||
          !record.bytesHeld) {
        continue;
      }
      final held = _receivedBytes[record.packId];
      if (held == null) continue;
      for (final key in record.tileKeys) {
        final bytes = held[key];
        if (bytes == null) continue;
        final serveKey = '${record.providerId}/$key';

        _serveBytes[serveKey] = Uint8List.fromList(bytes);
        _servePacks[serveKey] = record.packId;
      }
    }
  }

  static String _fileNameFor(String tileKey) =>
      '${tileKey.replaceAll('/', '_')}.tile';

  Future<Directory> _journalDir() async {
    final docs = await _directoryProvider();
    final dir = Directory('${docs.path}/$kPackJournalDir');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  Map<String, dynamic> _indexEntry(OfflinePackRecord record) => {
        'pack_id': record.packId,
        'provider': record.providerId,
        'provider_title': record.providerTitle,
        'zoom_min': record.zMin,
        'zoom_max': record.zMax,
        'x_min': record.xMin,
        'x_max': record.xMax,
        'y_min': record.yMin,
        'y_max': record.yMax,
        'bytes_per_tile': record.bytesPerTile,
        'approved_bulk': record.approvedBulk,
        'is_prefetch': record.isPrefetch,
        'created_at': record.createdAtEpoch,
        'seal': record.seal,
        'manifest': record.manifestJson,
        'tile_count': record.tileCount,
        'estimated_bytes': record.estimatedBytes,
        'received_bytes': record.receivedBytes,
        'keys': record.tileKeys.toList()..sort(),
      };

  Future<void> _persistPack(OfflinePackRecord record) async {
    final journal = await _journalDir();
    final packDir = Directory('${journal.path}/${record.packId}');
    if (!await packDir.exists()) await packDir.create(recursive: true);
    final held = _receivedBytes[record.packId] ?? const {};
    for (final key in record.tileKeys) {
      final bytes = held[key];
      if (bytes == null) continue;
      await File('${packDir.path}/${_fileNameFor(key)}')
          .writeAsBytes(bytes, flush: true);
    }
    await _writeIndex(journal);
  }

  Future<void> _writeIndex(Directory journal) async {
    final entries = [
      for (final record in _packs.values)
        if (record.lifecycle == OfflinePackLifecycle.complete &&
            record.bytesHeld)
          _indexEntry(record),
    ];
    await File('${journal.path}/$kPackIndexFile')
        .writeAsString(jsonEncode(entries), flush: true);
  }

  Future<void> _deletePackFiles(String packId) async {
    final journal = await _journalDir();
    final packDir = Directory('${journal.path}/$packId');
    if (await packDir.exists()) await packDir.delete(recursive: true);
    await _writeIndex(journal);
  }

  Future<void> _clearJournal() async {
    final journal = await _journalDir();
    if (await journal.exists()) await journal.delete(recursive: true);
  }

  Future<void> restore() async {
    try {
      await _restoreUnsafe();
    } catch (error) {
      _log('restore: aborted on unexpected error ($error)');
      notifyListeners();
    }
  }

  Future<void> _restoreUnsafe() async {
    final journal = await _journalDir();
    final indexFile = File('${journal.path}/$kPackIndexFile');
    if (!await indexFile.exists()) {
      _log('restore: no journal present');
      notifyListeners();
      return;
    }
    late final List<dynamic> entries;
    try {
      entries = jsonDecode(await indexFile.readAsString()) as List<dynamic>;
    } catch (error) {
      _log('restore: index unparseable, skipped whole journal ($error)');
      notifyListeners();
      return;
    }
    var restored = 0;
    for (final raw in entries) {

      if (raw is! Map) {
        _log('restore: entry skipped (not an object)');
        continue;
      }
      final restoredRecord = await _restoreOne(
        journal,
        raw.cast<String, dynamic>(),
      );
      if (restoredRecord != null) restored += 1;
    }
    _rebuildServe();
    _log('restore: $restored/${entries.length} packs re-indexed');
    notifyListeners();
  }

  Future<OfflinePackRecord?> _restoreOne(
    Directory journal,
    Map<String, dynamic> json,
  ) async {
    try {
      final packId = json['pack_id'] as String;

      if (_packs.containsKey(packId)) {
        _log('restore: $packId skipped (id already resident)');
        return null;
      }
      final keys = (json['keys'] as List).cast<String>();
      if (keys.length > kMaxSessionTiles) {
        _log('restore: $packId skipped (key list exceeds session cap)');
        return null;
      }
      final packDir = Directory('${journal.path}/$packId');
      final held = <String, List<int>>{};
      var bytes = 0;
      for (final key in keys) {
        final file = File('${packDir.path}/${_fileNameFor(key)}');
        if (!await file.exists()) {
          _log('restore: $packId skipped (missing tile file for $key)');
          return null;
        }
        final content = await file.readAsBytes();
        held[key] = content;
        bytes += content.length;
      }
      final record = OfflinePackRecord(
        packId: packId,
        providerId: json['provider'] as String,
        providerTitle:
            (json['provider_title'] as String?) ?? json['provider'] as String,
        zMin: (json['zoom_min'] as num).toInt(),
        zMax: (json['zoom_max'] as num).toInt(),
        xMin: (json['x_min'] as num).toInt(),
        xMax: (json['x_max'] as num).toInt(),
        yMin: (json['y_min'] as num).toInt(),
        yMax: (json['y_max'] as num).toInt(),
        bytesPerTile: (json['bytes_per_tile'] as num).toInt(),
        approvedBulk: (json['approved_bulk'] as bool?) ?? false,
        isPrefetch: (json['is_prefetch'] as bool?) ?? false,
        createdAtEpoch: (json['created_at'] as num).toInt(),
      )
        ..lifecycle = OfflinePackLifecycle.complete
        ..tileKeys = keys.toSet()
        ..persistedTileCount = (json['tile_count'] as num).toInt()
        ..persistedEstimatedBytes =
            (json['estimated_bytes'] as num?)?.toInt() ?? 0
        ..seal = json['seal'] as String?
        ..manifestJson = json['manifest'] as String?
        ..receivedTiles = keys.length
        ..receivedBytes = bytes
        ..cacheEntryPresent = true
        ..bytesHeld = true;
      _packs[packId] = record;
      _receivedBytes[packId] = held;
      final evicted = _store.put(
        AtlasCacheEntry(
          key: AtlasCacheKey(
            namespace: AtlasCacheNamespace.resource,
            value: packId,
          ),
          storedAt: _clock(),
          payloadId:
              record.seal == null ? null : AtlasId(record.seal!),
        ),
      );
      if (evicted != null) _noteEvicted(evicted);
      return record;
    } catch (error) {
      _log('restore: entry skipped (malformed: $error)');
      return null;
    }
  }
}
