// Sovereign Atlas Engine — atlas_provider_api
// Acquisition states, failure taxonomy, and results (pure semantics).
//
// Contracts: 1.8-E/F/G/H (inventory: PROPOSED → PROVISIONAL where noted).
// - States implemented: notStarted/inProgress/succeeded/failed/cancelled/
//   timedOut. PARTIAL/DEFERRED/UNAVAILABLE-as-state rejected as unjustified
//   (no contract supports them here; unavailability is a FAILURE category).
// - FAILED ≠ UNAVAILABLE: failed = an attempt ran and did not succeed;
//   unavailable = the resource cannot currently be provided (no attempt
//   semantics implied). CANCELLED ≠ FAILED: cancellation is an explicit
//   terminal outcome with different downstream meaning.
// - Failure taxonomy is CLOSED (8 string-free enum values — no HTTP codes,
//   ADV-052 guards this): invalidTarget, unsupported, unavailable, timeout,
//   cancelled, policyRejected, integrityFailure, unknown.
// - Retry table (PROVISIONAL, 1.8-H): timeout/unavailable/unknown retryable;
//   invalidTarget/unsupported/cancelled/policyRejected/integrityFailure not.
//   Advisory flags only — no backoff/counters/timers exist anywhere here.
// - Results carry request echo + state + optional failure + optional opaque
//   payload ID (never bytes — 1.8-J). No paths/entries/HTTP/renderer fields.
// Phase 1.8 slice. Depends on atlas_core (+ sibling request) only.

import 'package:atlas_core/atlas_core.dart';
import 'acquisition_request.dart';

/// Minimum justified acquisition lifecycle states.
enum AtlasAcquisitionState {
  notStarted,
  inProgress,
  succeeded,
  failed,
  cancelled,
  timedOut,
}

/// Semantic failure categories (closed set — no transport codes).
enum AtlasAcquisitionFailure {
  invalidTarget,
  unsupported,
  unavailable,
  timeout,
  cancelled,
  policyRejected,
  integrityFailure,
  unknown,
}

/// Advisory retry eligibility per failure (PROVISIONAL table, 1.8-H).
extension AtlasAcquisitionRetry on AtlasAcquisitionFailure {
  bool get retryable {
    switch (this) {
      case AtlasAcquisitionFailure.timeout:
      case AtlasAcquisitionFailure.unavailable:
      case AtlasAcquisitionFailure.unknown:
        return true;
      case AtlasAcquisitionFailure.invalidTarget:
      case AtlasAcquisitionFailure.unsupported:
      case AtlasAcquisitionFailure.cancelled:
      case AtlasAcquisitionFailure.policyRejected:
      case AtlasAcquisitionFailure.integrityFailure:
        return false;
    }
  }
}

/// Semantic acquisition outcome. No transport/storage/renderer members.
final class AtlasAcquisitionResult {
  const AtlasAcquisitionResult({
    required this.request,
    required this.state,
    this.failure,
    this.payloadId,
  });

  final AtlasAcquisitionRequest request;
  final AtlasAcquisitionState state;

  /// Failure category (failed/timedOut/cancelled states carry their own).
  final AtlasAcquisitionFailure? failure;

  /// Opaque payload reference on success (never bytes — 1.8-J boundary).
  final AtlasId? payloadId;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasAcquisitionResult &&
          request == other.request &&
          state == other.state &&
          failure == other.failure &&
          payloadId == other.payloadId;

  @override
  int get hashCode => Object.hash(request, state, failure, payloadId);
}
