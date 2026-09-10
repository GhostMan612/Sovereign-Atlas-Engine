// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import '../keys/cache_key.dart';
import '../lookup/cache_lookup.dart';
import 'pipeline_outcome.dart';
import 'pipeline_policy.dart';

abstract final class AtlasPipeline {

  static AtlasCacheKey handoffKeyFor(
    AtlasResourceIdentity identity,
  ) =>
      AtlasCacheKey(
        namespace: AtlasCacheNamespace.resource,
        value:
            '${identity.provider.value}/${identity.kind.name}/${identity.address}',
      );

  static AtlasCacheEntry cacheHandoffFor({
    required AtlasResolvedResource resource,
    required AtlasId payloadId,
    required int nowSeconds,
  }) =>
      AtlasCacheEntry(
        key: handoffKeyFor(resource.identity),
        resource: resource.identity,
        storedAt: nowSeconds,
        payloadId: payloadId,
      );

  static AtlasPipelineOutcome decide({
    required AtlasResolutionResult resolution,
    AtlasCacheEntry? cacheEntry,
    AtlasAcquisitionResult? acquisition,
    AtlasMaterialization? materialization,
    required int nowSeconds,
    required AtlasPipelinePolicy policy,
    AtlasAcquisitionPolicy acquisitionPolicy = const AtlasAcquisitionPolicy(),
  }) {

    switch (resolution.status) {
      case AtlasResolutionStatus.invalidRequest:
        return _terminal(AtlasPipelineStatus.invalidRequest, resolution.reason);
      case AtlasResolutionStatus.unsupported:
        return _terminal(AtlasPipelineStatus.unsupported, resolution.reason);
      case AtlasResolutionStatus.noMatch:
        return _terminal(AtlasPipelineStatus.noMatch, resolution.reason);
      case AtlasResolutionStatus.ambiguous:
        return _terminal(
          AtlasPipelineStatus.needsDisambiguation,
          resolution.reason,
        );
      case AtlasResolutionStatus.resolved:
        break;
    }

    final provider = resolution.provider;
    if (provider == null) {
      return _terminal(
        AtlasPipelineStatus.invalidRequest,
        'Resolved result carries no provider.',
      );
    }
    final tile = resolution.tile;
    final resource = AtlasResolvedResource(
      identity: AtlasResourceIdentity(
        provider: provider,
        kind: resolution.request.kind,
        address: tile == null
            ? ''
            : AtlasResolvedResource.tileAddressFor(
                tile,
                resolution.request.scheme,
              ),
      ),
      provider: provider,
      kind: resolution.request.kind,
      tile: tile,
      scheme: tile == null ? null : resolution.request.scheme,
    );
    final identityCheck = resource.validate();
    if (!identityCheck.isValid) {
      return _terminal(
        AtlasPipelineStatus.invalidRequest,
        identityCheck.rejection!.category,
      );
    }

    if (materialization != null) {
      if (materialization.resource != resource) {
        return const AtlasPipelineOutcome(
          status: AtlasPipelineStatus.invalidRequest,
          source: AtlasPipelineSource.materialization,
          reason: 'Materialization is for a different resource.',
        );
      }
      if (materialization.status == AtlasMaterializationStatus.invalid) {
        return AtlasPipelineOutcome(
          status: AtlasPipelineStatus.materializationFailed,
          source: AtlasPipelineSource.materialization,
          reason: materialization.reason,
          materialization: materialization,
        );
      }
      return AtlasPipelineOutcome(
        status: AtlasPipelineStatus.materialized,
        source: AtlasPipelineSource.materialization,
        reason: materialization.status.name,
        materialization: materialization,
      );
    }

    final decision = AtlasCache.lookup(cacheEntry, nowSeconds);

    if (decision.outcome == AtlasCacheOutcome.hit) {
      return AtlasPipelineOutcome(
        status: AtlasPipelineStatus.useCache,
        source: AtlasPipelineSource.cache,
        reason: 'usable cached entry present',
        cacheOutcome: decision.outcome,
        entry: decision.entry,
      );
    }

    if (acquisition != null) {
      if (acquisition.request.resource != resource.identity) {
        return const AtlasPipelineOutcome(
          status: AtlasPipelineStatus.invalidRequest,
          source: AtlasPipelineSource.acquisition,
          reason: 'Acquisition targets a different resource.',
        );
      }
      switch (acquisition.state) {
        case AtlasAcquisitionState.succeeded:
          final payloadId = acquisition.payloadId;
          if (payloadId == null) {
            return const AtlasPipelineOutcome(
              status: AtlasPipelineStatus.invalidRequest,
              source: AtlasPipelineSource.acquisition,
              reason: 'Succeeded acquisition carries no payload reference.',
            );
          }
          return AtlasPipelineOutcome(
            status: AtlasPipelineStatus.materialize,
            source: AtlasPipelineSource.acquisition,
            reason: 'acquisition succeeded; materialize then store handoff',
            cacheOutcome: decision.outcome,
            acquisition: acquisition,
            cacheHandoff: cacheHandoffFor(
              resource: resource,
              payloadId: payloadId,
              nowSeconds: nowSeconds,
            ),
          );
        case AtlasAcquisitionState.failed:
          if (policy.fallbackToStaleOnFailure &&
              decision.entry != null &&
              (decision.outcome == AtlasCacheOutcome.stale ||
                  decision.outcome == AtlasCacheOutcome.expired)) {
            return AtlasPipelineOutcome(
              status: AtlasPipelineStatus.useStaleCache,
              source: AtlasPipelineSource.cache,
              reason: 'declared fallback to a present entry',
              cacheOutcome: decision.outcome,
              entry: decision.entry,
            );
          }
          return AtlasPipelineOutcome(
            status: AtlasPipelineStatus.acquisitionFailed,
            source: AtlasPipelineSource.acquisition,
            reason: acquisition.failure?.name ?? acquisition.state.name,
            cacheOutcome: decision.outcome,
            acquisition: acquisition,
            failure: acquisition.failure,
          );
        case AtlasAcquisitionState.cancelled:
          return AtlasPipelineOutcome(
            status: AtlasPipelineStatus.acquisitionCancelled,
            source: AtlasPipelineSource.acquisition,
            reason: acquisition.failure?.name ?? acquisition.state.name,
            cacheOutcome: decision.outcome,
            acquisition: acquisition,
            failure: acquisition.failure,
          );
        case AtlasAcquisitionState.timedOut:
          return AtlasPipelineOutcome(
            status: AtlasPipelineStatus.acquisitionTimedOut,
            source: AtlasPipelineSource.acquisition,
            reason: acquisition.failure?.name ?? acquisition.state.name,
            cacheOutcome: decision.outcome,
            acquisition: acquisition,
            failure: acquisition.failure,
          );
        case AtlasAcquisitionState.notStarted:
        case AtlasAcquisitionState.inProgress:
          return const AtlasPipelineOutcome(
            status: AtlasPipelineStatus.invalidRequest,
            source: AtlasPipelineSource.acquisition,
            reason: 'Acquisition result is not terminal.',
          );
      }
    }

    final consent = switch (decision.outcome) {
      AtlasCacheOutcome.miss => true,
      AtlasCacheOutcome.stale => policy.acquireOnStale,
      AtlasCacheOutcome.expired => policy.acquireOnExpired,
      AtlasCacheOutcome.invalid => policy.acquireOnInvalid,
      AtlasCacheOutcome.hit => true,
    };
    if (!consent) {
      return AtlasPipelineOutcome(
        status: AtlasPipelineStatus.cacheRefused,
        source: AtlasPipelineSource.cache,
        reason: 'policy declined acquisition for ${decision.outcome.name}',
        cacheOutcome: decision.outcome,
        entry: decision.entry,
      );
    }
    final request = AtlasAcquisitionRequest(
      resource: resource.identity,
      policy: acquisitionPolicy,
    );
    final requestCheck = request.validate();
    if (!requestCheck.isValid) {
      return AtlasPipelineOutcome(
        status: AtlasPipelineStatus.invalidRequest,
        source: AtlasPipelineSource.acquisition,
        reason: requestCheck.rejection!.category,
      );
    }
    return AtlasPipelineOutcome(
      status: AtlasPipelineStatus.acquire,
      source: AtlasPipelineSource.acquisition,
      reason: 'no usable cache; acquisition required',
      cacheOutcome: decision.outcome,
      acquisitionRequest: request,
    );
  }

  static AtlasPipelineOutcome _terminal(
    AtlasPipelineStatus status,
    String reason,
  ) =>
      AtlasPipelineOutcome(
        status: status,
        source: AtlasPipelineSource.resolution,
        reason: reason,
      );
}
