// Sovereign Atlas Engine — atlas_location
// Location fix + heading + append-only track log (pure models).
//
// Contract: ADR-001 location charter (GPS/tracking/heading abstractions;
// UI widgets and tactical semantics stay out). Fixes carry explicit time +
// accuracy (unknown accuracy = null, never zero-filled); heading is degrees
// clockwise from north with optional source tag; the log appends (no edit,
// no reorder — history honesty matches the timeline precedent).
// Location slice. Depends on atlas_core + atlas_geo only.

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

/// Single position observation (platform-agnostic fix value).
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
