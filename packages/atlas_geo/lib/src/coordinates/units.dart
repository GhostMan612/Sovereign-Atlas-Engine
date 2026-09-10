// Sovereign Atlas Engine — atlas_geo
// Unit conversions + projected/datum holders.
//
// Contract: blueprint 4.1 (coordinate systems). Conversion factors are exact
// definitions; projected coordinates and datum metadata are explicit holders
// (UTM/MGRS full conversion stays hooks — blocked precedent MGRS-001;
// zone/band helpers live in atlas_tactical/mgrs).
// Phase 4 slice. Depends on atlas_core only (dart:math for derived values).

import 'dart:math' as math;

/// Exact-by-definition length conversions (SI + imperial + nautical).
abstract final class AtlasLengthUnits {
  static const double metersPerKilometer = 1000.0;
  static const double metersPerMile = 1609.344;
  static const double metersPerFoot = 0.3048;
  static const double metersPerNauticalMile = 1852.0;

  static double toKilometers(double meters) => meters / metersPerKilometer;
  static double toMiles(double meters) => meters / metersPerMile;
  static double toFeet(double meters) => meters / metersPerFoot;
  static double toNauticalMiles(double meters) =>
      meters / metersPerNauticalMile;
  static double fromKilometers(double v) => v * metersPerKilometer;
  static double fromMiles(double v) => v * metersPerMile;
  static double fromFeet(double v) => v * metersPerFoot;
  static double fromNauticalMiles(double v) => v * metersPerNauticalMile;

  /// Mean earth radius in meters (6371.0088 km — the SOURCE-VERIFIED F-02
  /// reference radius shared with haversine legs, so area and perimeter
  /// agree on one earth).
  static const double earthMeanRadiusMeters = 6371008.8;

  /// Degrees latitude per meter at the equator (spherical approximation).
  static double degreesPerMeter() =>
      360.0 / (2.0 * math.pi * earthMeanRadiusMeters);
}

/// Projected (planar) coordinate holder: easting/northing in [unit] under a
/// NAMED projection (projection math itself is downstream of this holder).
final class AtlasProjectedCoordinate {
  const AtlasProjectedCoordinate({
    required this.easting,
    required this.northing,
    required this.projection,
    this.unit = 'm',
  });

  final double easting;
  final double northing;
  final String projection;
  final String unit;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasProjectedCoordinate &&
          easting == other.easting &&
          northing == other.northing &&
          projection == other.projection &&
          unit == other.unit;

  @override
  int get hashCode => Object.hash(easting, northing, projection, unit);
}

/// Datum metadata holder (identity + ellipsoid hook; no transform math here).
final class AtlasDatum {
  const AtlasDatum({required this.name, this.ellipsoid = 'WGS84'});

  static const wgs84 = AtlasDatum(name: 'WGS84');

  final String name;
  final String ellipsoid;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasDatum && name == other.name && ellipsoid == other.ellipsoid;

  @override
  int get hashCode => Object.hash(name, ellipsoid);
}
