// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import '../fix/location_fix.dart';

final class AtlasTrackLog {
  AtlasTrackLog([Iterable<AtlasLocationFix>? fixes]) : _fixes = [...?fixes];

  final List<AtlasLocationFix> _fixes;

  void append(AtlasLocationFix fix) => _fixes.add(fix);

  List<AtlasLocationFix> get fixes => List.unmodifiable(_fixes);

  int get fixCount => _fixes.length;

  AtlasLocationFix? get latest => _fixes.isEmpty ? null : _fixes.last;
}
