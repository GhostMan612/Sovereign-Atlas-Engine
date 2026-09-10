// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

final class AtlasLocationFix {
  const AtlasLocationFix({
    required this.position,
    required this.at,
    this.accuracyMeters,
    this.speedMetersPerSecond,
    this.headingDeg,
    this.source = '',
  });

  final AtlasCoordinate position;
  final int at;
  final double? accuracyMeters;
  final double? speedMetersPerSecond;
  final double? headingDeg;
  final String source;

  AtlasValidation validate() => AtlasCoordinates.validate(
        position.latitude,
        position.longitude,
        crs: position.crs,
      );

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasLocationFix &&
          position == other.position &&
          at == other.at &&
          accuracyMeters == other.accuracyMeters &&
          speedMetersPerSecond == other.speedMetersPerSecond &&
          headingDeg == other.headingDeg &&
          source == other.source;

  @override
  int get hashCode => Object.hash(
        position,
        at,
        accuracyMeters,
        speedMetersPerSecond,
        headingDeg,
        source,
      );
}
