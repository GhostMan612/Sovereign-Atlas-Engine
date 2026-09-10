// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

final class AtlasWaypoint {
  const AtlasWaypoint({
    required this.id,
    required this.position,
    required this.createdAt,
    this.label = '',
    this.note = '',
  });

  final AtlasId id;
  final AtlasCoordinate position;
  final int createdAt;
  final String label;
  final String note;

  AtlasValidation validate() {
    final idCheck = AtlasIds.check(id.value);
    if (!idCheck.isValid) return idCheck;
    return AtlasCoordinates.validate(
      position.latitude,
      position.longitude,
      crs: position.crs,
    );
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasWaypoint &&
          id == other.id &&
          position == other.position &&
          createdAt == other.createdAt &&
          label == other.label &&
          note == other.note;

  @override
  int get hashCode => Object.hash(id, position, createdAt, label, note);
}
