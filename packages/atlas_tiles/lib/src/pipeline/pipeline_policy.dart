// Sovereign Atlas Engine — atlas_tiles
// Pipeline policy: declared routing posture (never a mechanism).
//
// Contract: 1.9-F (inventory §4: policy flags explicit).
// - All four flags are REQUIRED bools with no defaults: stale/expired/invalid
//   outcomes never imply acquisition by themselves, and fallback after a failed
//   acquisition needs declared consent. No invented defaults.
// - acquireOnStale/acquireOnExpired/acquireOnInvalid: whether the matching
//   cache decision routes to the acquire directive (true) or terminates as
//   cacheRefused (false). Refusal deletes nothing (no removal verb exists).
// - fallbackToStaleOnFailure: whether a failed acquisition may fall back to a
//   PRESENT entry (stale/expired decisions only). Miss/invalid entries back
//   nothing, and cancellation/timeout never fall back (distinct terminals).
// Phase 1.9 slice. Depends on atlas_core only.

/// Declared pipeline routing posture. Data, not machinery.
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
