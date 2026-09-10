// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';

final class AtlasRadialZone {
  const AtlasRadialZone({required this.center, required this.radiusMeters});

  final AtlasCoordinate center;
  final double radiusMeters;

  bool contains(AtlasCoordinate point) =>
      AtlasGeoMath.haversineKm(center, point) * 1000.0 <= radiusMeters;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasRadialZone &&
          center == other.center &&
          radiusMeters == other.radiusMeters;

  @override
  int get hashCode => Object.hash(center, radiusMeters);
}

abstract final class AtlasSpatial {

  static List<AtlasCoordinate> clipToBox(
    List<AtlasCoordinate> ring,
    AtlasBoundingBox box,
  ) {
    var output = ring;
    output = _clipEdge(output, true, box.west, true);
    output = _clipEdge(output, true, box.east, false);
    output = _clipEdge(output, false, box.south, true);
    output = _clipEdge(output, false, box.north, false);
    return output;
  }

  static List<AtlasCoordinate> _clipEdge(
    List<AtlasCoordinate> ring,
    bool vertical,
    double bound,
    bool keepGreater,
  ) {
    double value(AtlasCoordinate c) => vertical ? c.longitude : c.latitude;
    AtlasCoordinate cross(AtlasCoordinate a, AtlasCoordinate b) {
      final t = (bound - value(a)) / (value(b) - value(a));
      return AtlasCoordinate(
        latitude: a.latitude + t * (b.latitude - a.latitude),
        longitude: a.longitude + t * (b.longitude - a.longitude),
      );
    }

    final output = <AtlasCoordinate>[];
    for (var i = 0; i < ring.length; i++) {
      final current = ring[i];
      final previous = ring[(i + ring.length - 1) % ring.length];
      final currentIn =
          keepGreater ? value(current) >= bound : value(current) <= bound;
      final previousIn =
          keepGreater ? value(previous) >= bound : value(previous) <= bound;
      if (currentIn) {
        if (!previousIn) output.add(cross(previous, current));
        output.add(current);
      } else if (previousIn) {
        output.add(cross(previous, current));
      }
    }
    return output;
  }

  static List<AtlasCoordinate> densify(
    List<AtlasCoordinate> line,
    double stepMeters,
  ) {
    if (line.length < 2) return line;
    final output = <AtlasCoordinate>[line.first];
    for (var i = 0; i < line.length - 1; i++) {
      final legM = AtlasGeoMath.haversineKm(line[i], line[i + 1]) * 1000.0;
      final steps = (legM / stepMeters).floor();
      for (var s = 1; s <= steps; s++) {
        output.add(_slerp(line[i], line[i + 1], (s * stepMeters) / legM));
      }
      output.add(line[i + 1]);
    }
    return output;
  }

  static AtlasCoordinate _slerp(
    AtlasCoordinate a,
    AtlasCoordinate b,
    double fraction,
  ) {
    double lat(AtlasCoordinate c) => c.latitude * math.pi / 180.0;
    double lon(AtlasCoordinate c) => c.longitude * math.pi / 180.0;
    final ax = math.cos(lat(a)) * math.cos(lon(a));
    final ay = math.cos(lat(a)) * math.sin(lon(a));
    final az = math.sin(lat(a));
    final bx = math.cos(lat(b)) * math.cos(lon(b));
    final by = math.cos(lat(b)) * math.sin(lon(b));
    final bz = math.sin(lat(b));
    var dot = (ax * bx + ay * by + az * bz).clamp(-1.0, 1.0);
    final omega = math.acos(dot);
    if (omega < 1e-12) return a;
    final so = math.sin(omega);
    final ka = math.sin((1 - fraction) * omega) / so;
    final kb = math.sin(fraction * omega) / so;
    final x = ka * ax + kb * bx;
    final y = ka * ay + kb * by;
    final z = ka * az + kb * bz;
    return AtlasCoordinate(
      latitude: math.asin(z.clamp(-1.0, 1.0)) * 180.0 / math.pi,
      longitude: math.atan2(y, x) * 180.0 / math.pi,
    );
  }

  static List<AtlasCoordinate> simplify(
    List<AtlasCoordinate> line,
    double toleranceMeters,
  ) {
    if (line.length <= 2) return line;
    final keep = List<bool>.filled(line.length, false);
    keep.first = true;
    keep.last = true;
    _simplifyInto(line, 0, line.length - 1, toleranceMeters, keep);
    return [
      for (var i = 0; i < line.length; i++)
        if (keep[i]) line[i],
    ];
  }

  static void _simplifyInto(
    List<AtlasCoordinate> line,
    int first,
    int last,
    double toleranceMeters,
    List<bool> keep,
  ) {
    var maxMeters = 0.0;
    var index = first;
    for (var i = first + 1; i < last; i++) {
      final distance = _planarMeters(line[i], line[first], line[last]);
      if (distance > maxMeters) {
        maxMeters = distance;
        index = i;
      }
    }
    if (maxMeters > toleranceMeters) {
      keep[index] = true;
      _simplifyInto(line, first, index, toleranceMeters, keep);
      _simplifyInto(line, index, last, toleranceMeters, keep);
    }
  }

  static double _planarMeters(
    AtlasCoordinate p,
    AtlasCoordinate a,
    AtlasCoordinate b,
  ) {
    final latRef = p.latitude * math.pi / 180.0;
    double x(AtlasCoordinate c) =>
        c.longitude * math.pi / 180.0 * math.cos(latRef);
    double y(AtlasCoordinate c) => c.latitude * math.pi / 180.0;
    const earth = AtlasLengthUnits.earthMeanRadiusMeters;
    final dx = x(b) - x(a);
    final dy = y(b) - y(a);
    final denom = math.sqrt(dx * dx + dy * dy);
    if (denom == 0) {
      return math.sqrt(
            (x(p) - x(a)) * (x(p) - x(a)) + (y(p) - y(a)) * (y(p) - y(a)),
          ) *
          earth;
    }
    return ((x(p) - x(a)) * dy - (y(p) - y(a)) * dx).abs() / denom * earth;
  }
}
