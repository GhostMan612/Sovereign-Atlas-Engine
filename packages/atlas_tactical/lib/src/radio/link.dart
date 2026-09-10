// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';

abstract final class AtlasRadioLink {

  static double freeSpaceLossDb(double distanceMeters, double frequencyMHz) {
    if (distanceMeters <= 0 || frequencyMHz <= 0) {
      throw ArgumentError('Distance and frequency must be positive.');
    }
    return 20 * _log10(distanceMeters) + 20 * _log10(frequencyMHz) - 27.55;
  }

  static double linkMarginDb({
    required double distanceMeters,
    required double frequencyMHz,
    required double txPowerDbm,
    required double rxSensitivityDbm,
    double antennaGainDbi = 0.0,
    double extraLossDb = 0.0,
  }) =>
      txPowerDbm +
      antennaGainDbi -
      extraLossDb -
      freeSpaceLossDb(distanceMeters, frequencyMHz) -
      rxSensitivityDbm;

  static double rangeMeters(AtlasCoordinate a, AtlasCoordinate b) =>
      AtlasGeoMath.haversineKm(a, b) * 1000.0;

  static double _log10(double value) => math.log(value) / math.ln10;
}
