// Sovereign Atlas Engine — atlas_history
// Timeline: ordered snapshots + instant lookup + provenance listing.
//
// Contract: blueprint Phase 7 (timeline through time). Ordering is by event
// time ascending (ties broken by id — deterministic, never insertion luck).
// `at(instant)` returns the latest snapshot at or before the instant (null
// when nothing yet exists — absence, not an error). Provenance lists every
// distinct source id in first-appearance order (audit trail, not analytics).
// Phase 7 slice. Depends on atlas_core + siblings only.

import '../snapshots/snapshot.dart';

/// Ordered historical timeline value.
final class AtlasTimeline {
  AtlasTimeline([Iterable<AtlasHistoricalSnapshot>? snapshots])
      : _snapshots = (snapshots?.toList() ?? [])..sort(_compare);

  static int _compare(AtlasHistoricalSnapshot a, AtlasHistoricalSnapshot b) {
    final byTime = a.at.compareTo(b.at);
    if (byTime != 0) return byTime;
    return a.id.value.compareTo(b.id.value);
  }

  final List<AtlasHistoricalSnapshot> _snapshots;

  List<AtlasHistoricalSnapshot> get snapshots => List.unmodifiable(_snapshots);

  /// Latest snapshot at or before [instant] (null = nothing yet).
  AtlasHistoricalSnapshot? at(int instant) {
    AtlasHistoricalSnapshot? best;
    for (final snapshot in _snapshots) {
      if (snapshot.at <= instant) {
        best = snapshot;
      } else {
        break;
      }
    }
    return best;
  }

  /// Distinct source ids in first-appearance order.
  List<String> get provenance {
    final seen = <String>[];
    for (final snapshot in _snapshots) {
      if (!seen.contains(snapshot.sourceId)) seen.add(snapshot.sourceId);
    }
    return seen;
  }
}
