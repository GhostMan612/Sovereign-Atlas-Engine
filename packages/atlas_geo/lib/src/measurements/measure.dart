// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import '../coordinates/coordinate.dart';
import '../coordinates/distance.dart';
import '../coordinates/units.dart';
import '../geometry/polygon_types.dart';

abstract final class AtlasMeasure {

  static double ringAreaSqM(List<AtlasCoordinate> ring) {
    const earth = AtlasLengthUnits.earthMeanRadiusMeters;
    var total = 0.0;
    for (var i = 0; i < ring.length - 1; i++) {
      final p1 = ring[i];
      final p2 = ring[i + 1];
      total += _radians(p2.longitude - p1.longitude) *
          (math.sin(_radians(p1.latitude)) + math.sin(_radians(p2.latitude)));
    }
    return (total * earth * earth / 2.0).abs();
  }

  static double polygonAreaSqM(AtlasPolygon polygon) {
    var area = ringAreaSqM(polygon.exterior);
    for (final hole in polygon.holes) {
      area -= ringAreaSqM(hole);
    }
    return area;
  }

  static AtlasCoordinate centroid(AtlasPolygon polygon) {
    var sumLat = 0.0;
    var sumLon = 0.0;
    var weight = 0.0;
    void accumulate(List<AtlasCoordinate> ring, double sign) {
      for (var i = 0; i < ring.length - 1; i++) {
        final segment = (ring[i].longitude * ring[i + 1].latitude -
                ring[i + 1].longitude * ring[i].latitude) *
            sign;
        weight += segment;
        sumLon += (ring[i].longitude + ring[i + 1].longitude) * segment;
        sumLat += (ring[i].latitude + ring[i + 1].latitude) * segment;
      }
    }

    accumulate(polygon.exterior, 1.0);
    for (final hole in polygon.holes) {
      accumulate(hole, -1.0);
    }
    if (weight.abs() < 1e-12) {
      final first = polygon.exterior.first;
      return AtlasCoordinate(
        latitude: first.latitude,
        longitude: first.longitude,
      );
    }
    return AtlasCoordinate(
      latitude: sumLat / (3.0 * weight),
      longitude: sumLon / (3.0 * weight),
    );
  }

  static bool containsPoint(AtlasPolygon polygon, AtlasCoordinate point) {
    var inside = _ringContains(polygon.exterior, point);
    for (final hole in polygon.holes) {
      if (_ringContains(hole, point)) inside = false;
    }
    return inside;
  }

  static bool _ringContains(List<AtlasCoordinate> ring, AtlasCoordinate p) {
    var inside = false;
    for (var i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      final a = ring[i];
      final b = ring[j];
      if (_onSegment(a, b, p)) return true;
      if ((a.latitude > p.latitude) != (b.latitude > p.latitude)) {
        final atLon = (b.longitude - a.longitude) *
                (p.latitude - a.latitude) /
                (b.latitude - a.latitude) +
            a.longitude;
        if (p.longitude < atLon) inside = !inside;
      }
    }
    return inside;
  }

  static bool _onSegment(
      AtlasCoordinate a, AtlasCoordinate b, AtlasCoordinate p,) {
    const eps = 1e-9;
    final dx = b.longitude - a.longitude;
    final dy = b.latitude - a.latitude;
    final squared = dx * dx + dy * dy;
    if (squared < eps) {

      final dLon = p.longitude - a.longitude;
      final dLat = p.latitude - a.latitude;
      return dLon * dLon + dLat * dLat <= eps;
    }
    final cross =
        dx * (p.latitude - a.latitude) - dy * (p.longitude - a.longitude);
    if (cross.abs() > eps) return false;
    final dot =
        (p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy;
    if (dot < 0) return false;
    return dot <= squared + eps;
  }

  static double perimeterM(List<AtlasCoordinate> ring) {
    var totalKm = 0.0;
    for (var i = 0; i < ring.length - 1; i++) {
      totalKm += AtlasGeoMath.haversineKm(ring[i], ring[i + 1]);
    }
    return AtlasLengthUnits.fromKilometers(totalKm);
  }

  static AtlasCoordinate nearestOnSegment(
    AtlasCoordinate a,
    AtlasCoordinate b,
    AtlasCoordinate p,
  ) {
    final latRef = _radians(p.latitude);
    double x(AtlasCoordinate c) => _radians(c.longitude) * math.cos(latRef);
    double y(AtlasCoordinate c) => _radians(c.latitude);
    final dx = x(b) - x(a);
    final dy = y(b) - y(a);
    final lengthSq = dx * dx + dy * dy;
    if (lengthSq == 0) return a;
    var t = ((x(p) - x(a)) * dx + (y(p) - y(a)) * dy) / lengthSq;
    t = t.clamp(0.0, 1.0);
    final lon = a.longitude + t * (b.longitude - a.longitude);
    final lat = a.latitude + t * (b.latitude - a.latitude);
    return AtlasCoordinate(latitude: lat, longitude: lon);
  }

  static double _radians(double degrees) => degrees * math.pi / 180.0;
}
