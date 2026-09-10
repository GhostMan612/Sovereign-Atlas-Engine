// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import '../entries/cache_entry.dart';

enum AtlasCacheOutcome {

  hit,

  miss,

  stale,

  expired,

  invalid,
}

final class AtlasCacheDecision {
  const AtlasCacheDecision({required this.outcome, this.entry});

  final AtlasCacheOutcome outcome;
  final AtlasCacheEntry? entry;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasCacheDecision &&
          outcome == other.outcome &&
          entry == other.entry;

  @override
  int get hashCode => Object.hash(outcome, entry);
}

abstract final class AtlasCache {

  static AtlasCacheDecision lookup(AtlasCacheEntry? entry, int nowSeconds) {
    if (entry == null) {
      return const AtlasCacheDecision(outcome: AtlasCacheOutcome.miss);
    }
    if (!entry.validate().isValid || entry.revoked) {
      return AtlasCacheDecision(
        outcome: AtlasCacheOutcome.invalid,
        entry: entry,
      );
    }
    final age = nowSeconds - entry.storedAt;
    if (age < 0) {
      return AtlasCacheDecision(
        outcome: AtlasCacheOutcome.invalid,
        entry: entry,
      );
    }
    final maxAge = entry.maxAgeSeconds;
    if (maxAge == null) {
      return AtlasCacheDecision(outcome: AtlasCacheOutcome.stale, entry: entry);
    }
    if (age <= maxAge) {
      return AtlasCacheDecision(outcome: AtlasCacheOutcome.hit, entry: entry);
    }
    return AtlasCacheDecision(outcome: AtlasCacheOutcome.expired, entry: entry);
  }
}
