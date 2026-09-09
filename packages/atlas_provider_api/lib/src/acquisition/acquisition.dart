// Sovereign Atlas Engine — atlas_provider_api
// AtlasAcquisition: deterministic lifecycle holder (no engine inside).
//
// Contract: 1.8-E/I (inventory: PROPOSED → PROVISIONAL transitions).
// - Starts via `AtlasAcquisition.start(request, nowSeconds)` (validates first;
//   invalid requests throw with their validation rejection — construction ≠
//   silent acceptance).
// - Transitions (all pure, all explicit, all synchronous):
//   begin() notStarted→inProgress · cancel(now) notStarted/inProgress→cancelled
//   · checkTimeout(now): inProgress + deadline exceeded → timedOut (strictly
//   greater; equal stays — mirrors the freshness inclusive convention) ·
//   complete(payloadId, now) inProgress→succeeded · fail(failure, now)
//   inProgress→failed. Anything else throws INVALID_STATE (explicit, never
//   silent no-op).
// - Time arrives ONLY as explicit int epoch parameters (1.7-J law extended).
//   No clock, no timers, no tokens, no ids, no randomness, no globals.
// Phase 1.8 slice. Depends on atlas_core (+ siblings) only.

import '../../../../atlas_core/lib/atlas_core.dart';
import 'acquisition_request.dart';
import 'acquisition_result.dart';

/// A single acquisition's semantic state. Immutable; transitions return copies.
///
/// Construction ≠ validation by design (a held request may be invalid; only
/// [start] and transitions enforce). The default state is notStarted.
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

  /// Explicit start instant (epoch seconds). Null until begun.
  final int? startedAt;

  /// Explicit last-transition instant. Null until the first transition.
  final int? updatedAt;
  final AtlasAcquisitionFailure? failure;
  final AtlasId? payloadId;

  /// Starts an acquisition (validates the request first).
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

  /// notStarted → inProgress. Only from notStarted.
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

  /// notStarted/inProgress → cancelled. No token, no platform call.
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

  /// Pure deadline evaluation (not a timer): inProgress + elapsed >
  /// timeoutSeconds → timedOut. All other states pass through unchanged.
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

  /// inProgress → succeeded, binding an opaque payload reference (never bytes).
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

  /// inProgress → failed with an explicit taxonomy category.
  AtlasAcquisition fail(AtlasAcquisitionFailure failure, int nowSeconds) {
    _requireState(AtlasAcquisitionState.inProgress, 'fail requires inProgress');
    return _copyWith(
      state: AtlasAcquisitionState.failed,
      failure: failure,
      updatedAt: nowSeconds,
    );
  }

  /// Current semantic result (no execution performed by reading it).
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
  }) => AtlasAcquisition(
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
