// Sovereign Atlas Engine — atlas_tiles
// Lookup semantics: deterministic outcomes from (entry?, policy-in-entry, now).
//
// Contract: 1.7-E/F/G (inventory: PROPOSED → PROVISIONAL rules below).
// Outcome determination order (first match wins; each documented):
// 1. Absent entry → MISS (says nothing about upstream existence).
// 2. Structurally invalid entry → INVALID (never coerced, never a miss).
// 3. Revoked entry → INVALID (invalidation beats freshness; still present).
// 4. storedAt in the future of now → INVALID (time anomaly; PROVISIONAL —
//    clock skew must not read as fresh).
// 5. Undeclared maxAge → STALE (PROVISIONAL unknown-freshness rule: the
//    Recovery stale-forever correction — unknown policy must not grant
//    silent fresh hits, nor auto-expire by assumption).
// 6. age ≤ maxAge → HIT/fresh (boundary inclusive, PROVISIONAL convention).
// 7. Otherwise → EXPIRED (present and valid, only the decision changes).
// Time arrives ONLY as explicit `nowSeconds` (1.7-J); no clock, no randomness,
// no filesystem/network state, no iteration over unordered collections.
// Phase 1.7 slice. Depends on atlas_core (+ sibling entry) only.

import '../entries/cache_entry.dart';

/// Cache lookup outcome. Only contract-justified values exist.
enum AtlasCacheOutcome {
  /// Fresh entry present.
  hit,

  /// No entry present (nothing implied about upstream).
  miss,

  /// Present but revalidation-advised (unknown policy or per-policy staleness).
  stale,

  /// Present, valid, past its horizon (still present — not deletion).
  expired,

  /// Present but unusable (invalid structure, revoked, or time anomaly).
  invalid,
}

/// Deterministic lookup decision: outcome + the evaluated entry (null on miss).
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

/// Pure cache lookup over optional entries.
abstract final class AtlasCache {
  /// Decides [entry] at explicit [nowSeconds] (epoch seconds).
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
