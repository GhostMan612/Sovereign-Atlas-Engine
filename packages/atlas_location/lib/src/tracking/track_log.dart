// Sovereign Atlas Engine — atlas_location
// Append-only fix log (no edit, no reorder — observation honesty).
//
// Contract: ADR-001 location charter (tracking abstractions).
// Location slice. Depends on atlas_core + sibling fix type only.

import '../fix/location_fix.dart';

/// Append-only fix log value holder.
final class AtlasTrackLog {
  AtlasTrackLog([Iterable<AtlasLocationFix>? fixes]) : _fixes = [...?fixes];

  final List<AtlasLocationFix> _fixes;

  void append(AtlasLocationFix fix) => _fixes.add(fix);

  List<AtlasLocationFix> get fixes => List.unmodifiable(_fixes);

  int get fixCount => _fixes.length;

  AtlasLocationFix? get latest => _fixes.isEmpty ? null : _fixes.last;
}
