// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'acquisition_request.dart';
import 'acquisition_result.dart';

final class AtlasAcquisition {
  const AtlasAcquisition({
    required this.request,
    this.state = AtlasAcquisitionState.notStarted,
    this.startedAt,
    this.updatedAt,
    this.failure,
    this.payloadId,
  });

  final AtlasAcquisitionRequest request;
  final AtlasAcquisitionState state;

  final int? startedAt;

  final int? updatedAt;
  final AtlasAcquisitionFailure? failure;
  final AtlasId? payloadId;

  static AtlasAcquisition start(
    AtlasAcquisitionRequest request,
    int nowSeconds,
  ) {
    final check = request.validate();
    if (!check.isValid) {
      throw AtlasRejectionException(check.rejection!);
    }
    return AtlasAcquisition(request: request).begin(nowSeconds);
  }

  AtlasAcquisition begin(int nowSeconds) {
    _requireState(
      AtlasAcquisitionState.notStarted,
      'begin requires notStarted',
    );
    return _copyWith(
      state: AtlasAcquisitionState.inProgress,
      startedAt: nowSeconds,
      updatedAt: nowSeconds,
    );
  }

  AtlasAcquisition cancel(int nowSeconds) {
    if (state != AtlasAcquisitionState.notStarted &&
        state != AtlasAcquisitionState.inProgress) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'INVALID_STATE',
          'cancel requires notStarted or inProgress.',
        ),
      );
    }
    return _copyWith(
      state: AtlasAcquisitionState.cancelled,
      failure: AtlasAcquisitionFailure.cancelled,
      updatedAt: nowSeconds,
    );
  }

  AtlasAcquisition checkTimeout(int nowSeconds) {
    final timeout = request.policy.timeoutSeconds;
    if (state != AtlasAcquisitionState.inProgress ||
        timeout == null ||
        startedAt == null) {
      return this;
    }
    if (nowSeconds - startedAt! > timeout) {
      return _copyWith(
        state: AtlasAcquisitionState.timedOut,
        failure: AtlasAcquisitionFailure.timeout,
        updatedAt: nowSeconds,
      );
    }
    return this;
  }

  AtlasAcquisition complete(AtlasId payloadId, int nowSeconds) {
    _requireState(
      AtlasAcquisitionState.inProgress,
      'complete requires inProgress',
    );
    return _copyWith(
      state: AtlasAcquisitionState.succeeded,
      payloadId: payloadId,
      updatedAt: nowSeconds,
    );
  }

  AtlasAcquisition fail(AtlasAcquisitionFailure failure, int nowSeconds) {
    _requireState(AtlasAcquisitionState.inProgress, 'fail requires inProgress');
    return _copyWith(
      state: AtlasAcquisitionState.failed,
      failure: failure,
      updatedAt: nowSeconds,
    );
  }

  AtlasAcquisitionResult toResult() => AtlasAcquisitionResult(
        request: request,
        state: state,
        failure: failure,
        payloadId: payloadId,
      );

  void _requireState(AtlasAcquisitionState required, String message) {
    if (state != required) {
      throw AtlasRejectionException(AtlasRejection('INVALID_STATE', message));
    }
  }

  AtlasAcquisition _copyWith({
    AtlasAcquisitionState? state,
    int? startedAt,
    int? updatedAt,
    AtlasAcquisitionFailure? failure,
    AtlasId? payloadId,
  }) =>
      AtlasAcquisition(
        request: request,
        state: state ?? this.state,
        startedAt: startedAt ?? this.startedAt,
        updatedAt: updatedAt ?? this.updatedAt,
        failure: failure ?? this.failure,
        payloadId: payloadId ?? this.payloadId,
      );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasAcquisition &&
          request == other.request &&
          state == other.state &&
          startedAt == other.startedAt &&
          updatedAt == other.updatedAt &&
          failure == other.failure &&
          payloadId == other.payloadId;

  @override
  int get hashCode =>
      Object.hash(request, state, startedAt, updatedAt, failure, payloadId);
}
