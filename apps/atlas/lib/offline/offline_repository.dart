// Sovereign Atlas — first host shell, Offline Areas track (blueprint 3.5).
//
// OfflineRepository: app-side orchestrator over engine offline primitives.
// Owns: the engine memory store (pack-slot accounting), pack records,
// downloader lifecycles, the HTTP chunk source (engine URL resolution +
// engine transport — the app states WHICH tile, the engine states HOW the
// URL reads), manifest assembly + seal verification on completion, the disk
// journal (byte persistence + pack index for relaunch), the RAM serve map
// feeding the renderer adapter, and a bounded event log feeding
// Diagnostics.
//
// What this file does NOT do (engine-owned, never duplicated):
// - tile enumeration or refusal decisions (AtlasPackPlanner),
// - chunk looping, resume, quota, cancellation (AtlasPackDownloader),
// - checksums or seals (fnv1a64 / AtlasPackManifest),
// - store semantics (AtlasMemoryStore),
// - URL template mechanics (AtlasTileRequest.resolveUrl).
//
// Byte/knowledge split (engine 1.7-H): the engine store holds KNOWLEDGE
// (index entries); bytes live downstream here (RAM serve map + disk
// journal). Resolution serves bytes ONLY when the engine index entry is
// resident (live gate in resolveTileBytes) — unindexed bytes are pending
// deletion, never "available offline".

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

/// Pack slots in the engine store. Pack-level accounting (one entry per
/// pack), NOT tile-level: a 512-entry tile store would make pack sizes
/// absurd. 64 is an explicit app choice (documented, adjustable), not an
/// engine or provider declaration.
const int kPackStoreCapacity = 64;

/// Session-memory guard: plan tile counts above this are refused app-side.
/// Bytes are session-resident (no file persistence yet), so unbounded plans
/// would OOM the host. Counted by pure arithmetic BEFORE the engine planner
/// ever enumerates (the planner has no cap of its own when the provider
/// declares no maxTiles — all builtins declare none).
const int kMaxSessionTiles = 4096;

/// Diagnostics event log bound (oldest drops first).
const int kEventLogBound = 200;

/// Disk journal layout under the app documents directory:
/// `offline_packs/<packId>/<z>_<x>_<y>.tile` + `offline_packs/index.json`.
const String kPackJournalDir = 'offline_packs';
const String kPackIndexFile = 'index.json';

/// Default chunk source: engine URL resolution over the endpoint template +
/// engine production transport. Throws [AtlasTransportException] on HTTP
/// failure (surfaces as the downloader `failed` terminal with detail).
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

/// App orchestrator for the Offline Areas track. Testable: [clock],
/// [chunkSourceFactory], and [directoryProvider] inject fakes
/// (widget/integration builds use the production defaults).
///
/// Rate limiting is CENTRALIZED (blueprint 3.4): one optional [sharedLimiter]
/// throttles every pack download. This is structural, not cosmetic: the
/// engine downloader takes one timestamp per run and re-walks every tile on
/// resume (one limiter take per iteration, received tiles included), so a
/// per-pack limiter can never bank enough tokens to resume past a pause it
/// caused itself (capacity < tiles ⇒ pause; resume needs ≥ tiles takes but
/// can hold at most capacity). A shared limiter refills across packs and
/// resumes, which is the only shape under which quota-pause is recoverable.
final class OfflineRepository extends ChangeNotifier {
  OfflineRepository({
    required this._registry,
    AtlasMemoryStore? store,
    AtlasChunkSource Function(AtlasProviderEndpoint)? chunkSourceFactory,
    int Function()? clock,
    this._sharedLimiter,
    Future<Directory> Function()? directoryProvider,
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

  final Map<String, OfflinePackRecord> _packs = {};
  final Map<String, Map<String, List<int>>> _receivedBytes = {};
  final Map<String, AtlasPackDownloader> _downloads = {};
  final Map<String, ExecutionCancellation> _cancellations = {};
  final List<String> _events = [];
  int _packSequence = 0;

  /// Renderer-observable counters (polled by Diagnostics; deliberately NOT
  /// notified per tile — notifyListeners per tile would rebuild the UI at
  /// tile rate).
  int _offlineTileHits = 0;
  int _networkTileRequests = 0;

  int get offlineTileHits => _offlineTileHits;
  int get networkTileRequests => _networkTileRequests;

  AtlasProviderRegistry get registry => _registry;
  AtlasMemoryStore get store => _store;

  /// Serve map: `$providerId/$tileKey` → bytes (RAM). Gated by the engine
  /// index at resolve time (see [resolveTileBytes]).
  final Map<String, Uint8List> _serveBytes = {};
  final Map<String, String> _servePacks = {};

  /// Insertion-ordered records (plan order = display order).
  List<OfflinePackRecord> get packs => _packs.values.toList();

  /// Newest-first event log copy (Diagnostics page).
  List<String> get events => _events.reversed.toList();

  OfflinePackRecord? lookup(String packId) => _packs[packId];

  void _log(String message) {
    _events.add('${_clock()} $message');
    if (_events.length > kEventLogBound) {
      _events.removeRange(0, _events.length - kEventLogBound);
    }
  }

  /// Saturating tile count over inclusive ranges (no enumeration). Returns
  /// [kMaxSessionTiles] + 1 as the saturating "too many" sentinel.
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

  /// Plans a pack: app-side validation first, then the engine planner.
  /// Always registers a record (approved plans AND refusals/blocks are both
  /// "available offline" knowledge worth showing). Returns the record.
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
    final packId = 'pack-$now-$_packSequence';
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
      // Engine rule: estimates require an explicit basis. The form requires
      // the field; this is the defense in depth (never defaulted here).
      record.appBlock =
          'ESTIMATE_REQUIRED: bytes-per-tile must be positive (no default '
          'estimate is ever assumed).';
      _log('plan $packId blocked: estimate required');
      notifyListeners();
      return record;
    }
    if (endpoint.isLocal) {
      // Bundle-backed endpoints have no URL template: nothing exists yet
      // that turns them into bytes (bundle ingestion is future work).
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
          'APP_PACK_TOO_LARGE: ranges exceed the session-memory cap of '
          '$kMaxSessionTiles tiles (bytes are session-resident; file '
          'persistence is future work). Narrow the ranges.';
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

  /// Starts or resumes the download for [packId]. One engine
  /// [AtlasPackDownloader.download] call runs to its terminal; UI pause
  /// requests interleave between chunk awaits (engine cooperative design).
  /// Received bytes persist in [_receivedBytes] across resume calls.
  Future<void> startDownload(String packId) async {
    final record = _packs[packId];
    if (record == null || record.plan == null || record.appBlock != null) {
      return;
    }
    if (_downloads[packId] != null) return; // already running
    final endpoint = _registry.lookup(record.providerId);
    if (endpoint == null) return;

    final cancellation = ExecutionCancellation();
    _cancellations[packId] = cancellation;
    final downloader = AtlasPackDownloader(
      tiles: record.plan!.tiles,
      source: _chunkSourceFactory(endpoint),
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
      final progress = downloader.progress;
      record.receivedTiles = progress.received;
      record.receivedBytes = progress.bytes;
      switch (terminal) {
        case AtlasDownloadState.complete:
          await _completePack(record, endpoint);
        case AtlasDownloadState.failed:
          record.lifecycle = OfflinePackLifecycle.failed;
          record.failureDetail = downloader.failureDetail;
          _log('download $packId FAILED: ${downloader.failureDetail}');
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
          // Unreachable: download() only returns terminals.
          break;
      }
    } finally {
      _downloads.remove(packId);
      _cancellations.remove(packId);
      notifyListeners();
    }
  }

  /// Assembles the manifest from received bytes, verifies self-consistency
  /// (validate + stable seal), indexes the pack in the engine store, and
  /// persists bytes + index to the disk journal.
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
      // sourceVersion: undeclared by every builtin endpoint (no version
      // field exists on descriptors) — null is honest, never defaulted.
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
    _store.put(
      AtlasCacheEntry(
        key: AtlasCacheKey(
          namespace: AtlasCacheNamespace.resource,
          value: record.packId,
        ),
        storedAt: _clock(),
        payloadId: AtlasId(manifest.seal),
      ),
    );
    record.cacheEntryPresent = true;
    record.bytesHeld = true;
    record.lifecycle = OfflinePackLifecycle.complete;
    _rebuildServe();
    await _persistPack(record);
    _log('download ${record.packId} COMPLETE: ${record.receivedTiles} tiles, '
        '${record.receivedBytes} B, seal ${manifest.seal}');
  }

  /// Cooperative pause (observed between chunks; resume via [startDownload]).
  void pauseDownload(String packId) {
    _downloads[packId]?.pause();
  }

  /// Cooperative cancel (observed between chunks; bytes held for resume —
  /// use [deletePack] for clean deletion per engine discard semantics).
  void cancelDownload(String packId) {
    _cancellations[packId]?.requestCancel();
    _log('cancel requested for $packId');
    notifyListeners();
  }

  /// Clean deletion: drops the store index entry, RAM + disk bytes, the
  /// journal entry, and the record.
  Future<void> deletePack(String packId) async {
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

  /// Manage Storage: evicts EVERYTHING (engine index, RAM serve bytes, disk
  /// journal) while keeping records as history, flagged unindexed +
  /// bytelss. Eviction is total by design: half-held bytes (indexed but
  /// deleted, or held but unindexed) would make "available offline" a lie.
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

  // ------------------------------------------------------------------
  // Resolution: the renderer seam.
  // ------------------------------------------------------------------

  /// Serves one tile's bytes for [providerId] + engine-format [tileKey]
  /// (`z/x/y`). Bytes serve ONLY when the pack's engine index entry is
  /// resident (live knowledge gate — unindexed bytes are pending deletion,
  /// never "available offline"). Null = local miss (caller falls back to
  /// network where policy permits, or degrades when it does not).
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

  /// Records a renderer miss that fell through to the network path
  /// (Diagnostics-observable; no per-tile notify — see counter note).
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
        // Newest completed record wins (insertion order).
        _serveBytes[serveKey] = Uint8List.fromList(bytes);
        _servePacks[serveKey] = record.packId;
      }
    }
  }

  // ------------------------------------------------------------------
  // Disk journal (app-side persistence; engine never touches disk).
  // ------------------------------------------------------------------

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

  /// Restores disk-persisted packs into a FRESH repository (relaunch path:
  /// engine store is empty, RAM is empty — the journal rebuilds both).
  /// Corrupt entries (missing files, oversized key lists, unparseable JSON)
  /// are SKIPPED with a log line, never half-loaded (integrity honesty).
  Future<void> restore() async {
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
      final restoredRecord = await _restoreOne(
        journal,
        (raw as Map).cast<String, dynamic>(),
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
      _store.put(
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
      return record;
    } catch (error) {
      _log('restore: entry skipped (malformed: $error)');
      return null;
    }
  }
}
