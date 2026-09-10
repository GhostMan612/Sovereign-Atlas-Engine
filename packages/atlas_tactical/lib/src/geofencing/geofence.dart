// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

enum AtlasGeofenceKind { radial, polygon }

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
