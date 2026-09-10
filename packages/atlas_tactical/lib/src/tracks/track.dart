// Sovereign Atlas Engine — atlas_tactical
// Ordered waypoint trail (append-only value; shared-service length).
//
// Contract: blueprint Phase 10 (tracks). Length reuses haversine legs
// (4.4 shared-services rule — no parallel math stack).
// Phase 10 slice. Depends on atlas_core + atlas_geo + sibling waypoint.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import '../waypoints/waypoint.dart';

/// Ordered waypoint trail value.
final class AtlasTrack {
  const AtlasTrack({
    required this.id,
    this.points = const [],
    this.createdAt,
  });

  final AtlasId id;
  final List<AtlasWaypoint> points;
  final int? createdAt;

  int get pointCount => points.length;

  /// Trail length in meters (haversine legs; empty/singleton = 0).
  double get lengthMeters {
    var totalKm = 0.0;
    for (var i = 0; i + 1 < points.length; i++) {
      totalKm += AtlasGeoMath.haversineKm(
        points[i].position,
        points[i + 1].position,
      );
    }
    return AtlasLengthUnits.fromKilometers(totalKm);
  }

  AtlasTrack append(AtlasWaypoint point) =>
      AtlasTrack(id: id, points: [...points, point], createdAt: createdAt);

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasTrack &&
          id == other.id &&
          pointCount == other.pointCount &&
          createdAt == other.createdAt;

  @override
  int get hashCode => Object.hash(id, pointCount, createdAt);
}
