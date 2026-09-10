// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

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

  static const double earthMeanRadiusMeters = 6371008.8;

  static double degreesPerMeter() =>
      360.0 / (2.0 * math.pi * earthMeanRadiusMeters);
}

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
