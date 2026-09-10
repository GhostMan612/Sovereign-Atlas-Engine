// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import '../lookup/cache_lookup.dart';

enum AtlasPipelineStatus {

  invalidRequest,

  unsupported,

  noMatch,

  needsDisambiguation,

  useCache,

  acquire,

  useStaleCache,

  materialize,

  materialized,

  acquisitionFailed,

  acquisitionCancelled,

  acquisitionTimedOut,

  materializationFailed,

  cacheRefused,
}

enum AtlasPipelineSource { resolution, cache, acquisition, materialization }

final class AtlasPipelineOutcome {
  const AtlasPipelineOutcome({
    required this.status,
    required this.source,
    this.reason = '',
    this.cacheOutcome,
    this.entry,
    this.acquisitionRequest,
    this.acquisition,
    this.materialization,
    this.cacheHandoff,
    this.failure,
  });

  final AtlasPipelineStatus status;
  final AtlasPipelineSource source;

  final String reason;

  final AtlasCacheOutcome? cacheOutcome;

  final AtlasCacheEntry? entry;

  final AtlasAcquisitionRequest? acquisitionRequest;

  final AtlasAcquisitionResult? acquisition;

  final AtlasMaterialization? materialization;

  final AtlasCacheEntry? cacheHandoff;

  final AtlasAcquisitionFailure? failure;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPipelineOutcome &&
          status == other.status &&
          source == other.source &&
          reason == other.reason &&
          cacheOutcome == other.cacheOutcome &&
          entry == other.entry &&
          acquisitionRequest == other.acquisitionRequest &&
          acquisition == other.acquisition &&
          materialization == other.materialization &&
          cacheHandoff == other.cacheHandoff &&
          failure == other.failure;

  @override
  int get hashCode => Object.hash(
        status,
        source,
        reason,
        cacheOutcome,
        entry,
        acquisitionRequest,
        acquisition,
        materialization,
        cacheHandoff,
        failure,
      );

  @override
  String toString() => 'AtlasPipelineOutcome($status via $source)';
}
