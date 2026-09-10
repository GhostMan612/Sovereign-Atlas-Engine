// Sovereign Atlas — first host shell, Offline Areas track (blueprint 3.5).
//
// OfflineRepository: app-side orchestrator over engine offline primitives.
// Owns: the engine memory store (pack-slot accounting), pack records,
// downloader lifecycles, the HTTP chunk source (engine URL resolution +
// engine transport — the app states WHICH tile, the engine states HOW the
// URL reads), manifest assembly + seal verification on completion, and a
// bounded event log feeding the Diagnostics page.
//
// What this file does NOT do (engine-owned, never duplicated):
// - tile enumeration or refusal decisions (AtlasPackPlanner),
// - chunk looping, resume, quota, cancellation (AtlasPackDownloader),
// - checksums or seals (fnv1a64 / AtlasPackManifest),
// - store semantics (AtlasMemoryStore),
// - URL template mechanics (AtlasTileRequest.resolveUrl).

import 'dart:convert';

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_offline/atlas_offline.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import 'package:atlas_providers/atlas_providers.dart';
import 'package:atlas_tiles/atlas_tiles.dart';
import 'package:flutter/foundation.dart';

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

/// App orchestrator for the Offline Areas track. Testable: [clock], and
/// [chunkSourceFactory] inject fakes (widget/integration builds use the
/// production default).
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
  })  : _store = store ?? AtlasMemoryStore(capacity: kPackStoreCapacity),
        _chunkSourceFactory = chunkSourceFactory ?? defaultChunkSource,
        _clock = clock ??
            (() => DateTime.now().millisecondsSinceEpoch ~/ 1000);

  final AtlasProviderRegistry _registry;
  final AtlasMemoryStore _store;
  final AtlasChunkSource Function(AtlasProviderEndpoint) _chunkSourceFactory;
  final AtlasRateLimiter? _sharedLimiter;
  final int Function() _clock;

  final Map<String, OfflinePackRecord> _packs = {};
  final Map<String, Map<String, List<int>>> _receivedBytes = {};
  final Map<String, AtlasPackDownloader> _downloads = {};
  final Map<String, ExecutionCancellation> _cancellations = {};
  final List<String> _events = [];
  int _packSequence = 0;

  AtlasProviderRegistry get registry => _registry;
  AtlasMemoryStore get store => _store;

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
  /// (validate + stable seal), and indexes the pack in the engine store.
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
    record.lifecycle = OfflinePackLifecycle.complete;
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

  /// Clean deletion: drops the store index entry, session bytes, and record.
  void deletePack(String packId) {
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
      _log('pack $packId deleted (index entry + session bytes dropped)');
    }
    notifyListeners();
  }

  /// Manage Storage: clears the engine store. Completed records keep their
  /// bytes and seal but are flagged (index entry gone — shown honestly).
  void clearStore() {
    _store.clear();
    for (final record in _packs.values) {
      record.cacheEntryPresent = false;
    }
    _log('store cleared by operator (${_packs.length} records kept)');
    notifyListeners();
  }
}
