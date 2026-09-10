// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';

final class AtlasSegmentHit {
  const AtlasSegmentHit({required this.at, required this.t, required this.u});

  final AtlasCoordinate at;

  final double t;
  final double u;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasSegmentHit &&
          at == other.at &&
          t == other.t &&
          u == other.u;

  @override
  int get hashCode => Object.hash(at, t, u);
}

abstract final class AtlasSegments {
  static const double _eps = 1e-12;

  static double _orient(
          AtlasCoordinate a, AtlasCoordinate b, AtlasCoordinate c) =>
      (b.longitude - a.longitude) * (c.latitude - a.latitude) -
      (b.latitude - a.latitude) * (c.longitude - a.longitude);

  static bool _between(
    AtlasCoordinate a,
    AtlasCoordinate b,
    AtlasCoordinate c,
  ) {
    final lonMin = math.min(a.longitude, b.longitude) - _eps;
    final lonMax = math.max(a.longitude, b.longitude) + _eps;
    final latMin = math.min(a.latitude, b.latitude) - _eps;
    final latMax = math.max(a.latitude, b.latitude) + _eps;
    return c.longitude >= lonMin &&
        c.longitude <= lonMax &&
        c.latitude >= latMin &&
        c.latitude <= latMax;
  }

  static AtlasSegmentHit? intersect(
    AtlasCoordinate a,
    AtlasCoordinate b,
    AtlasCoordinate c,
    AtlasCoordinate d,
  ) {
    final o1 = _orient(a, b, c);
    final o2 = _orient(a, b, d);
    final o3 = _orient(c, d, a);
    final o4 = _orient(c, d, b);
    final proper = ((o1 > _eps && o2 < -_eps) || (o1 < -_eps && o2 > _eps)) &&
        ((o3 > _eps && o4 < -_eps) || (o3 < -_eps && o4 > _eps));
    AtlasCoordinate point(double t) => AtlasCoordinate(
          latitude: a.latitude + t * (b.latitude - a.latitude),
          longitude: a.longitude + t * (b.longitude - a.longitude),
        );
    if (proper) {
      final dx = b.longitude - a.longitude;
      final dy = b.latitude - a.latitude;
      final denom =
          dx * (d.latitude - c.latitude) - dy * (d.longitude - c.longitude);
      if (denom.abs() < _eps) return null;
      final t = ((c.longitude - a.longitude) * (d.latitude - c.latitude) -
              (c.latitude - a.latitude) * (d.longitude - c.longitude)) /
          denom;
      final u =
          ((c.latitude - a.latitude) * dx - (c.longitude - a.longitude) * dy) /
              -denom;
      return AtlasSegmentHit(at: point(t), t: t, u: u);
    }

    if (o1.abs() <= _eps && _between(a, b, c)) {
      return AtlasSegmentHit(at: c, t: _fraction(a, b, c), u: 0.0);
    }
    if (o2.abs() <= _eps && _between(a, b, d)) {
      return AtlasSegmentHit(at: d, t: _fraction(a, b, d), u: 1.0);
    }
    if (o3.abs() <= _eps && _between(c, d, a)) {
      return AtlasSegmentHit(at: a, t: 0.0, u: _fraction(c, d, a));
    }
    if (o4.abs() <= _eps && _between(c, d, b)) {
      return AtlasSegmentHit(at: b, t: 1.0, u: _fraction(c, d, b));
    }
    return null;
  }

  static double _fraction(
      AtlasCoordinate a, AtlasCoordinate b, AtlasCoordinate p) {
    final dx = b.longitude - a.longitude;
    final dy = b.latitude - a.latitude;
    final denom = dx * dx + dy * dy;
    if (denom == 0) return 0.0;
    return ((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) /
        denom;
  }
}
