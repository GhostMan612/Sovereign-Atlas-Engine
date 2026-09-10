// Sovereign Atlas Engine — atlas_tactical
// Geofences: radial + polygon zones with explicit breach semantics.
//
// Contract: blueprint Phase 10 (geofencing contracts). Radial membership
// reuses haversine; polygon membership reuses the shared PIP service (no
// parallel geometry). A fence is armed or not (explicit); evaluation is a
// pure function of fence + position (no tracking state inside the fence).
// Phase 10 slice. Depends on atlas_core + atlas_geo only.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

/// Geofence kinds (kept apart — radial math never approximates polygons).
enum AtlasGeofenceKind { radial, polygon }

/// Armed zone value with pure breach evaluation.
final class AtlasGeofence {
  const AtlasGeofence.radial({
    required this.id,
    required this.center,
    required this.radiusMeters,
    this.armed = true,
  })  : kind = AtlasGeofenceKind.radial,
        polygon = null;

  const AtlasGeofence.polygon({
    required this.id,
    required AtlasPolygon this.polygon,
    this.armed = true,
  })  : kind = AtlasGeofenceKind.polygon,
        center = null,
        radiusMeters = null;

  final AtlasId id;
  final AtlasGeofenceKind kind;
  final AtlasCoordinate? center;
  final double? radiusMeters;
  final AtlasPolygon? polygon;
  final bool armed;

  /// True when armed and [position] sits inside (boundary counts as inside,
  /// consistent with shared PIP edge rules).
  bool breachedBy(AtlasCoordinate position) {
    if (!armed) return false;
    if (kind == AtlasGeofenceKind.radial) {
      return AtlasGeoMath.haversineKm(center!, position) * 1000.0 <=
          radiusMeters!;
    }
    return AtlasMeasure.containsPoint(polygon!, position);
  }

  AtlasValidation validate() {
    final idCheck = AtlasIds.check(id.value);
    if (!idCheck.isValid) return idCheck;
    if (kind == AtlasGeofenceKind.radial) {
      if (radiusMeters == null || radiusMeters! <= 0) {
        return const AtlasValidation.invalid(
          AtlasRejection(
            'INVALID_GEOFENCE',
            'Radial fences need a positive radius.',
          ),
        );
      }
      return AtlasCoordinates.validate(
        center!.latitude,
        center!.longitude,
        crs: center!.crs,
      );
    }
    return polygon!.validate();
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasGeofence &&
          id == other.id &&
          kind == other.kind &&
          center == other.center &&
          radiusMeters == other.radiusMeters &&
          polygon == other.polygon &&
          armed == other.armed;

  @override
  int get hashCode =>
      Object.hash(id, kind, center, radiusMeters, polygon, armed);
}
