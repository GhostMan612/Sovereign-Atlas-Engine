// Sovereign Atlas Engine — atlas_provider_api
// Acquisition policy: declared bounds for an acquisition (never a mechanism).
//
// Contract: 1.8-C/I (inventory: PROPOSED → PROVISIONAL).
// - timeoutSeconds (optional): deadline duration in seconds. Null = no deadline
//   declared. Negative values are structurally invalid (ACQ-014).
// - allowRetry (default true): caller-declared retry posture, consumed by
//   FUTURE engines alongside per-failure retryability — this package performs
//   no retries, backoff, counters, timers, or queues (1.8-H refusal).
// Phase 1.8 slice. Depends on atlas_core only.

import '../../../../atlas_core/lib/atlas_core.dart';

/// Declared acquisition bounds. Data, not machinery.
final class AtlasAcquisitionPolicy {
  const AtlasAcquisitionPolicy({this.timeoutSeconds, this.allowRetry = true});

  /// Deadline duration in seconds from start. Null = none declared.
  final int? timeoutSeconds;

  /// Whether the caller permits a future engine to retry failures that are
  /// retryable by category. Advisory only.
  final bool allowRetry;

  AtlasValidation validate() {
    if (timeoutSeconds != null && timeoutSeconds! < 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_REQUEST',
          'Acquisition timeout must be non-negative when declared.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasAcquisitionPolicy &&
          timeoutSeconds == other.timeoutSeconds &&
          allowRetry == other.allowRetry;

  @override
  int get hashCode => Object.hash(timeoutSeconds, allowRetry);
}
