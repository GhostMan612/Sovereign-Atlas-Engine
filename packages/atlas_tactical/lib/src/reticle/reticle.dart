// Sovereign Atlas Engine — atlas_tactical
// Reticle model: named angular-offset marker set (pure display data).
//
// Contract: blueprint Phase 10 (reticle). A reticle is a named collection
// of mil/moa-indexed stadia offsets plus an optional center label —
// geometry for renderers to draw, never drawing itself. No ballistics
// beyond explicit click values carried as data (drop tables stay
// caller-side datasets, never invented constants).
// Phase 10 slice. Depends on atlas_core only.

import 'package:atlas_core/atlas_core.dart';

/// Angular unit for reticle offsets.
enum AtlasAngularUnit { mils, moa }

/// Single stadia mark: direction + offset from center.
final class AtlasStadia {
  const AtlasStadia({
    required this.clockDirection,
    required this.offset,
    this.label = '',
  });

  /// Clock direction 1–12 (12 = up).
  final int clockDirection;
  final double offset;
  final String label;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasStadia &&
          clockDirection == other.clockDirection &&
          offset == other.offset &&
          label == other.label;

  @override
  int get hashCode => Object.hash(clockDirection, offset, label);
}

/// Named reticle value.
final class AtlasReticle {
  const AtlasReticle({
    required this.id,
    required this.unit,
    this.stadia = const [],
    this.clickValue,
  });

  final AtlasId id;
  final AtlasAngularUnit unit;
  final List<AtlasStadia> stadia;

  /// Click value in [unit] (explicit caller data, never ballistics).
  final double? clickValue;

  int get stadiaCount => stadia.length;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasReticle &&
          id == other.id &&
          unit == other.unit &&
          stadiaCount == other.stadiaCount &&
          clickValue == other.clickValue;

  @override
  int get hashCode => Object.hash(id, unit, stadiaCount, clickValue);
}
