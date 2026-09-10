// Sovereign Atlas — first host shell, Offline Areas track (blueprint 3.5).
//
// Boundary: this file is app-side RECORD bookkeeping only. Planning,
// downloading, manifests, seals, rate limits, and store semantics all live
// in engine packages (atlas_providers / atlas_offline / atlas_tiles); the
// repository below orchestrates them. No geospatial math, no URL building,
// no policy invention here — policy text is quoted from engine declarations.

import 'package:atlas_offline/atlas_offline.dart';
import 'package:atlas_providers/atlas_providers.dart';

/// Lifecycle of one pack record as the APP tracks it. Mirrors
/// [AtlasDownloadState] plus two app-side pre-states: a record exists from
/// planning time, so `planned` covers both engine-planned and app-blocked
/// (refusal/block detail on the record explains which).
enum OfflinePackLifecycle {
  planned,
  downloading,
  paused,
  quotaPaused,
  complete,
  failed,
  cancelled,
}

/// One pack as the Offline Areas UI tracks it.
///
/// Engine values ([AtlasPackPlan], [AtlasPackRefusal], manifest, seal) are
/// held by reference/value — never re-derived. Bytes are session-resident in
/// the repository (no file persistence exists yet; NOT claimed).
final class OfflinePackRecord {
  OfflinePackRecord({
    required this.packId,
    required this.providerId,
    required this.providerTitle,
    required this.zMin,
    required this.zMax,
    required this.xMin,
    required this.xMax,
    required this.yMin,
    required this.yMax,
    required this.bytesPerTile,
    required this.approvedBulk,
    required this.isPrefetch,
    required this.createdAtEpoch,
  });

  final String packId;
  final String providerId;
  final String providerTitle;
  final int zMin;
  final int zMax;
  final int xMin;
  final int xMax;
  final int yMin;
  final int yMax;

  /// Explicit estimate basis (engine rule: no invented averages — the UI
  /// requires this field, never defaults it).
  final int bytesPerTile;
  final bool approvedBulk;
  final bool isPrefetch;
  final int createdAtEpoch;

  /// Engine plan when the planner approved (null when refused/blocked).
  AtlasPackPlan? plan;

  /// Engine refusal when the planner refused (null otherwise). Reasons stay
  /// inside the engine set (BULK_GUARD | PACK_TOO_LARGE | PREFETCH_REFUSED |
  /// INVALID_RANGE) — app-side blocks use [appBlock], never this field.
  AtlasPackRefusal? refusal;

  /// App-side block (validation the engine planner cannot see: estimate
  /// missing, tile-count cap, local endpoint without locator). Plain text,
  /// never dressed as an engine refusal.
  String? appBlock;

  OfflinePackLifecycle lifecycle = OfflinePackLifecycle.planned;
  int receivedTiles = 0;
  int receivedBytes = 0;

  /// Manifest seal once a download completes and verifies (null before).
  String? seal;

  /// Export-shape JSON of the verified manifest (diagnostics display).
  String? manifestJson;

  /// Engine failure detail (failed terminal) or quota note (quotaPaused).
  String failureDetail = '';

  /// False after Manage-Storage clears the store entry out from under a
  /// completed pack (bytes stay session-resident; the index entry is gone).
  bool cacheEntryPresent = false;

  int get tileCount => plan?.entryCount ?? 0;
  int get estimatedBytes => plan?.estimatedBytes ?? 0;

  bool get isTerminal =>
      lifecycle == OfflinePackLifecycle.complete ||
      lifecycle == OfflinePackLifecycle.failed ||
      lifecycle == OfflinePackLifecycle.cancelled;

  /// Raw age in seconds against [nowEpoch] (no stale threshold exists —
  /// inventing one would be fabrication; the UI shows age, never "stale").
  int ageSeconds(int nowEpoch) => nowEpoch - createdAtEpoch;
}
