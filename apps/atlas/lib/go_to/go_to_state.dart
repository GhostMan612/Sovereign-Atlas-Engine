// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';
import 'package:flutter/foundation.dart';

final class GoToTarget {
  const GoToTarget({
    required this.id,
    required this.latitude,
    required this.longitude,
    required this.label,
  });

  final String id;
  final double latitude;
  final double longitude;
  final String label;
}

class GoToState extends ChangeNotifier {
  GoToTarget? _target;

  GoToTarget? get target => _target;
  bool get isActive => _target != null;

  void activate({
    required String id,
    required double latitude,
    required double longitude,
    required String label,
  }) {
    final point = AtlasCoordinates.checked(latitude, longitude);
    _target = GoToTarget(
      id: id,
      latitude: point.latitude,
      longitude: point.longitude,
      label: label,
    );
    notifyListeners();
  }

  void clear() {
    if (_target == null) return;
    _target = null;
    notifyListeners();
  }

  double? distanceKmTo(AtlasCoordinate? fix) {
    final target = _target;
    if (target == null || fix == null) return null;
    return AtlasGeoMath.haversineKm(
      fix,
      AtlasCoordinate(latitude: target.latitude, longitude: target.longitude),
    );
  }

  double? bearingDegTo(AtlasCoordinate? fix) {
    final target = _target;
    if (target == null || fix == null) return null;
    try {
      return AtlasGeoMath.initialBearingDeg(
        fix,
        AtlasCoordinate(
          latitude: target.latitude,
          longitude: target.longitude,
        ),
      );
    } on AtlasRejectionException {
      return null;
    }
  }
}
