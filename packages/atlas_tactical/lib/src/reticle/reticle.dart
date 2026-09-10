// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';

enum AtlasAngularUnit { mils, moa }

final class AtlasStadia {
  const AtlasStadia({
    required this.clockDirection,
    required this.offset,
    this.label = '',
  });

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
