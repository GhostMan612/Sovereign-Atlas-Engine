// Sovereign Atlas Engine — atlas_tactical
// Radio-link estimation: free-space path loss + link margin (pure math).
//
// Contract: blueprint Phase 10 + ADR-001 (radio-link estimation MODELS —
// mesh transport itself stays in integrations/). FSPL is the closed-form
// baseline (frequency + distance in, dB out); link margin subtracts caller-
// supplied losses/gains (terrain/vegetation/hardware stay caller-side data,
// never invented constants). No hardware, no spectrum claims.
// Phase 10 slice. Depends on atlas_core + atlas_geo (distance) only.

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';

/// Free-space link estimation services.
abstract final class AtlasRadioLink {
  /// Free-space path loss in dB for [frequencyMHz] over [distanceMeters].
  /// Closed form: 20·log10(d) + 20·log10(f) − 27.55 (d in meters, f in MHz).
  static double freeSpaceLossDb(double distanceMeters, double frequencyMHz) {
    if (distanceMeters <= 0 || frequencyMHz <= 0) {
      throw ArgumentError('Distance and frequency must be positive.');
    }
    return 20 * _log10(distanceMeters) + 20 * _log10(frequencyMHz) - 27.55;
  }

  /// Link margin in dB: txPower + gains − losses − FSPL − sensitivity.
  /// All terms are explicit caller data (no invented radio constants).
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

  /// Great-circle range check between two positions at explicit distance.
  static double rangeMeters(AtlasCoordinate a, AtlasCoordinate b) =>
      AtlasGeoMath.haversineKm(a, b) * 1000.0;

  static double _log10(double value) => math.log(value) / math.ln10;
}
