// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:flutter/foundation.dart';

enum MeasurePointSource { fix, mapCenter }

enum MeasureUnit { meters, kilometers, miles, feet, nauticalMiles }

final class MeasureState extends ChangeNotifier {
  AtlasCoordinate? _pointA;
  MeasurePointSource? _pointASource;
  AtlasCoordinate? _pointB;
  MeasureUnit _unit = MeasureUnit.meters;

  AtlasCoordinate? get pointA => _pointA;
  MeasurePointSource? get pointASource => _pointASource;
  AtlasCoordinate? get pointB => _pointB;
  MeasureUnit get unit => _unit;

  bool get isActive => _pointA != null;
  bool get isComplete => _pointA != null && _pointB != null;

  double? get distanceKm {
    final a = _pointA;
    final b = _pointB;
    if (a == null || b == null) return null;
    return AtlasGeoMath.haversineKm(a, b);
  }

  double? get bearingDeg {
    final a = _pointA;
    final b = _pointB;
    if (a == null || b == null) return null;
    try {
      return AtlasGeoMath.initialBearingDeg(a, b);
    } on AtlasRejectionException {
      return null;
    }
  }

  double? get displayDistance {
    final km = distanceKm;
    if (km == null) return null;
    final meters = km * 1000.0;
    return switch (_unit) {
      MeasureUnit.meters => meters,
      MeasureUnit.kilometers => km,
      MeasureUnit.miles => AtlasLengthUnits.toMiles(meters),
      MeasureUnit.feet => AtlasLengthUnits.toFeet(meters),
      MeasureUnit.nauticalMiles => AtlasLengthUnits.toNauticalMiles(meters),
    };
  }

  String get unitLabel => labelOf(_unit);

  static String labelOf(MeasureUnit unit) => switch (unit) {
        MeasureUnit.meters => 'm',
        MeasureUnit.kilometers => 'km',
        MeasureUnit.miles => 'mi',
        MeasureUnit.feet => 'ft',
        MeasureUnit.nauticalMiles => 'nmi',
      };

  void begin({AtlasCoordinate? fixA, required AtlasCoordinate center}) {
    if (fixA != null) {
      _pointA = fixA;
      _pointASource = MeasurePointSource.fix;
    } else {
      _pointA = center;
      _pointASource = MeasurePointSource.mapCenter;
    }
    _pointB = null;
    notifyListeners();
  }

  void setB(AtlasCoordinate point) {
    _pointB = point;
    notifyListeners();
  }

  void setUnit(MeasureUnit unit) {
    _unit = unit;
    notifyListeners();
  }

  void clear() {
    _pointA = null;
    _pointASource = null;
    _pointB = null;
    notifyListeners();
  }
}
