// Sovereign Atlas Engine — atlas_tactical
// MGRS hooks: grid-zone designator only (full conversion stays blocked).
//
// Contract: MGRS-001 stays BLOCKED (no 100k-grid/easting/northing claims,
// no precision promises). What IS honest: the UTM zone number + latitude
// band letter derive from closed formulas both sources agree on, so the
// hook exposes exactly that pair and refuses the rest by absence (no fake
// converter, no precision beyond the designator).
// Phase 10 slice. Depends on atlas_core + atlas_geo (coordinates) only.

import 'package:atlas_geo/atlas_geo.dart';

/// Grid-zone designator hook (zone + band; nothing finer).
final class AtlasMgrsZone {
  const AtlasMgrsZone({required this.zone, required this.band});

  /// UTM zone number 1–60.
  final int zone;

  /// Latitude band letter C–X (I/O excluded, X spans 12°).
  final String band;

  /// Derives the designator for [point] (closed formulas, no precision
  /// beyond zone/band — full MGRS stays MGRS-001-blocked).
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
