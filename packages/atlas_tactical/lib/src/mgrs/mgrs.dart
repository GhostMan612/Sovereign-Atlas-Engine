// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_geo/atlas_geo.dart';

final class AtlasMgrsZone {
  const AtlasMgrsZone({required this.zone, required this.band});

  final int zone;

  final String band;

  factory AtlasMgrsZone.of(AtlasCoordinate point) {
    final zone = ((point.longitude + 180.0) / 6.0).floor() + 1;
    const bands = 'CDEFGHJKLMNPQRSTUVWX';
    final index = ((point.latitude + 80.0) / 8.0).floor().clamp(0, 19);
    return AtlasMgrsZone(zone: zone.clamp(1, 60), band: bands[index]);
  }

  @override
  String toString() => '$zone$band';

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasMgrsZone && zone == other.zone && band == other.band;

  @override
  int get hashCode => Object.hash(zone, band);
}
