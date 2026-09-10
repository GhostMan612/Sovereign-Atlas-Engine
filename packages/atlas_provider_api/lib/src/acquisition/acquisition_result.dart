// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'acquisition_request.dart';

enum AtlasAcquisitionState {
  notStarted,
  inProgress,
  succeeded,
  failed,
  cancelled,
  timedOut,
}

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

final class AtlasAcquisitionResult {
  const AtlasAcquisitionResult({
    required this.request,
    required this.state,
    this.failure,
    this.payloadId,
  });

  final AtlasAcquisitionRequest request;
  final AtlasAcquisitionState state;

  final AtlasAcquisitionFailure? failure;

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
