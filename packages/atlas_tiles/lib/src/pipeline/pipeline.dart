// Sovereign Atlas Engine — atlas_tiles
// Pipeline semantics: deterministic coordination of closed contracts.
//
// Contract: 1.9-C/D/E/H/K/L (inventory §§2–5; placement §6).
// - `decide` is a STATELESS pure function over supplied stage results: the
//   resolution result, an optional cache entry, an optional acquisition
//   result, an optional materialization, explicit time, and declared policy.
//   No retained state object exists (retention implies an engine); no stage
//   is executed here — resolution/acquisition/materialization arrive as
//   results, cache arrives as an entry evaluated by the owned lookup.
// - Evaluation order (each step documented at the call site):
//   resolution terminals → identity binding → materialization short-circuit
//   → cache evaluation → usable-hit short-circuit → acquisition routing →
//   policy routing. Cache evaluation always precedes acquisition routing
//   (a null entry evaluates to miss); a usable hit outranks a supplied
//   acquisition result (usable cache is authoritative for usability).
// - Binding needs no provider descriptor: the identity triple is fully
//   determined by the result (provider id, request kind, tile rendering or
//   the empty qualifier). Descriptor hooks stay null here and reattach
//   downstream (equality is identity-based, so routing is unaffected).
// - Handoff construction is replacement-by-construction (cache precedent):
//   key + identity + explicit storedAt + payload id, maxAge left undeclared
//   (undeclared freshness reads stale downstream — honest, never silent).
// - Kind-agnostic throughout: tile fields are never read here, so local,
//   geojson, elevation and every other kind flow exactly like tiles.
// - Time arrives ONLY as explicit int epoch parameters. No clock, no ids,
//   no randomness, no platform state.
// Phase 1.9 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import '../../../../atlas_core/lib/atlas_core.dart';
import '../../../../atlas_provider_api/lib/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import '../keys/cache_key.dart';
import '../lookup/cache_lookup.dart';
import 'pipeline_outcome.dart';
import 'pipeline_policy.dart';

/// Stateless semantic pipeline: coordination decisions, never machinery.
abstract final class AtlasPipeline {
  /// Candidate handoff key for [identity]: resource namespace, canonical
  /// `provider/kind/address` value (URL-free by identity validation).
  static AtlasCacheKey handoffKeyFor(
    AtlasResourceIdentity identity,
  ) => AtlasCacheKey(
    namespace: AtlasCacheNamespace.resource,
    value:
        '${identity.provider.value}/${identity.kind.name}/${identity.address}',
  );

  /// Candidate handoff entry for an acquired payload: pure construction with
  /// explicit [nowSeconds]; maxAge stays undeclared (see header).
  static AtlasCacheEntry cacheHandoffFor({
    required AtlasResolvedResource resource,
    required AtlasId payloadId,
    required int nowSeconds,
  }) => AtlasCacheEntry(
    key: handoffKeyFor(resource.identity),
    resource: resource.identity,
    storedAt: nowSeconds,
    payloadId: payloadId,
  );

  /// Routes one pipeline step over supplied stage results (see header order).
  static AtlasPipelineOutcome decide({
    required AtlasResolutionResult resolution,
    AtlasCacheEntry? cacheEntry,
    AtlasAcquisitionResult? acquisition,
    AtlasMaterialization? materialization,
    required int nowSeconds,
    required AtlasPipelinePolicy policy,
    AtlasAcquisitionPolicy acquisitionPolicy = const AtlasAcquisitionPolicy(),
  }) {
    // 1. Resolution terminals terminate with their own provenance intact.
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
    // 2. Bind the resource identity (descriptor-free, see header). A resolved
    // result without a provider, or with a URL-bearing address, cannot bind.
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
    // 3. Materialization short-circuit: the boundary is already reached
    // (ready OR deferred). A foreign materialization is caller misuse.
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
    // 4. Cache evaluation always precedes acquisition routing.
    final decision = AtlasCache.lookup(cacheEntry, nowSeconds);
    // 5. A usable hit outranks everything below (authoritative for usability).
    if (decision.outcome == AtlasCacheOutcome.hit) {
      return AtlasPipelineOutcome(
        status: AtlasPipelineStatus.useCache,
        source: AtlasPipelineSource.cache,
        reason: 'usable cached entry present',
        cacheOutcome: decision.outcome,
        entry: decision.entry,
      );
    }
    // 6. A supplied acquisition result routes on its terminal state. Pending
    // states are not results (refused); foreign results are caller misuse.
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
    // 7. No acquisition supplied: miss always acquires; stale/expired/invalid
    // acquire only with declared consent, else terminate as cacheRefused.
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
  ) => AtlasPipelineOutcome(
    status: status,
    source: AtlasPipelineSource.resolution,
    reason: reason,
  );
}
