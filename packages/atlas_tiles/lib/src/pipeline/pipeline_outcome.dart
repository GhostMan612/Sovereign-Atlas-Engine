// Sovereign Atlas Engine — atlas_tiles
// Pipeline outcome: one routing decision with its provenance (never execution).
//
// Contract: 1.9-D/G/H/I/J (inventory §§2–4).
// - Statuses are directives (useCache/acquire/useStaleCache/materialize — the
//   execution layer serves them later by any means) or terminals (everything
//   else). Directives name WHAT next, never HOW.
// - Failure provenance is preserved, never merged: resolution terminals stay
//   distinct (invalidRequest/unsupported/noMatch/needsDisambiguation —
//   ambiguity is not failure); cache miss is a directive, never failure;
//   acquisition failed/cancelled/timedOut are three terminals (FAILED differs
//   from CANCELLED per the acquisition contract); materialization-invalid
//   terminates even after acquisition success (no silent conversion).
// - `source` names the stage whose result determined the outcome (1.9-I:
//   decisions and results never mix types, and the two never masquerade).
// - Outcomes carry value types only: entries, requests, results,
//   materializations, identities. No bytes, no paths, no handles, no views.
// Phase 1.9 slice. Depends on atlas_core + atlas_provider_api (+ siblings).

import 'package:atlas_provider_api/atlas_provider_api.dart';
import '../entries/cache_entry.dart';
import '../lookup/cache_lookup.dart';

/// Pipeline routing outcome. Only contract-justified values exist.
enum AtlasPipelineStatus {
  /// Resolution rejected its own request (or a stage result proved corrupt).
  invalidRequest,

  /// Resolution found nothing to match (kind unserved / unaddressable here).
  unsupported,

  /// Resolution serves the kind but no provider is eligible.
  noMatch,

  /// Resolution is ambiguous: caller must disambiguate (not a failure).
  needsDisambiguation,

  /// Directive: a usable cached entry exists — serve it (then materialize).
  useCache,

  /// Directive: no usable cache — run an acquisition for the carried request.
  acquire,

  /// Directive: acquisition failed but a present entry may serve instead.
  useStaleCache,

  /// Directive: acquisition succeeded — materialize, then store the handoff.
  materialize,

  /// Terminal success: the materialization boundary is reached (ready OR
  /// deferred — deferred is success without representation, never failure).
  materialized,

  /// Terminal: acquisition ran and failed (category preserved, no fallback).
  acquisitionFailed,

  /// Terminal: acquisition was cancelled (never a failure, never retried).
  acquisitionCancelled,

  /// Terminal: acquisition exceeded its declared deadline.
  acquisitionTimedOut,

  /// Terminal: materialization itself is invalid (even after success above).
  materializationFailed,

  /// Terminal: policy declined acquisition for a stale/expired/invalid entry.
  cacheRefused,
}

/// Which stage's result determined the outcome (provenance, 1.9-I).
enum AtlasPipelineSource { resolution, cache, acquisition, materialization }

/// One deterministic routing decision with its provenance and payloads.
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

  /// Deterministic reason (categories/counts/ids, never timestamps).
  final String reason;

  /// The cache decision that led here (directives consulting cache).
  final AtlasCacheOutcome? cacheOutcome;

  /// The usable entry (useCache) or fallback entry (useStaleCache).
  final AtlasCacheEntry? entry;

  /// The acquisition to run (acquire directive only).
  final AtlasAcquisitionRequest? acquisitionRequest;

  /// The acquisition result routed on (materialize + acquisition terminals).
  final AtlasAcquisitionResult? acquisition;

  /// The materialization routed on (materialized + materializationFailed).
  final AtlasMaterialization? materialization;

  /// Candidate entry for the acquired payload (materialize directive only).
  /// Pure construction — storing it is the execution layer's concern.
  final AtlasCacheEntry? cacheHandoff;

  /// Preserved failure category (acquisitionFailed/Cancelled/TimedOut).
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
