// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

final class AtlasPipelinePolicy {
  const AtlasPipelinePolicy({
    required this.acquireOnStale,
    required this.acquireOnExpired,
    required this.acquireOnInvalid,
    required this.fallbackToStaleOnFailure,
  });

  final bool acquireOnStale;
  final bool acquireOnExpired;
  final bool acquireOnInvalid;
  final bool fallbackToStaleOnFailure;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasPipelinePolicy &&
          acquireOnStale == other.acquireOnStale &&
          acquireOnExpired == other.acquireOnExpired &&
          acquireOnInvalid == other.acquireOnInvalid &&
          fallbackToStaleOnFailure == other.fallbackToStaleOnFailure;

  @override
  int get hashCode => Object.hash(
        acquireOnStale,
        acquireOnExpired,
        acquireOnInvalid,
        fallbackToStaleOnFailure,
      );

  @override
  String toString() =>
      'AtlasPipelinePolicy(stale=$acquireOnStale expired=$acquireOnExpired '
      'invalid=$acquireOnInvalid fallback=$fallbackToStaleOnFailure)';
}
