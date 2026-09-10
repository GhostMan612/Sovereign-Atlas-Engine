// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import '../snapshots/snapshot.dart';

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

  List<String> get provenance {
    final seen = <String>[];
    for (final snapshot in _snapshots) {
      if (!seen.contains(snapshot.sourceId)) seen.add(snapshot.sourceId);
    }
    return seen;
  }
}
