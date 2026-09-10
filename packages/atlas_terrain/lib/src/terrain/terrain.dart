// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_geo/atlas_geo.dart';
import '../grid/elevation_grid.dart';

abstract final class AtlasTerrain {

  static double? slopeDeg(AtlasElevationGrid grid, int row, int col) {
    final window = _window(grid, row, col);
    if (window == null) return null;
    final dzdx = ((window[0][2] + 2 * window[1][2] + window[2][2]) -
            (window[0][0] + 2 * window[1][0] + window[2][0])) /
        (8 * grid.cellSizeMeters);
    final dzdy = ((window[2][0] + 2 * window[2][1] + window[2][2]) -
            (window[0][0] + 2 * window[0][1] + window[0][2])) /
        (8 * grid.cellSizeMeters);
    return math.atan(math.sqrt(dzdx * dzdx + dzdy * dzdy)) * 180.0 / math.pi;
  }

  static double? aspectDeg(AtlasElevationGrid grid, int row, int col) {
    final window = _window(grid, row, col);
    if (window == null) return null;
    final dzdx = ((window[0][2] + 2 * window[1][2] + window[2][2]) -
            (window[0][0] + 2 * window[1][0] + window[2][0])) /
        (8 * grid.cellSizeMeters);
    final dzdy = ((window[2][0] + 2 * window[2][1] + window[2][2]) -
            (window[0][0] + 2 * window[0][1] + window[0][2])) /
        (8 * grid.cellSizeMeters);
    if (dzdx == 0 && dzdy == 0) return null;
    var aspect = math.atan2(dzdx, -dzdy) * 180.0 / math.pi;
    if (aspect < 0) aspect += 360.0;
    return aspect;
  }

  static double? hillshade(
    AtlasElevationGrid grid,
    int row,
    int col,
    double azimuthDeg,
    double altitudeDeg,
  ) {
    final slope = slopeDeg(grid, row, col);
    final aspect = aspectDeg(grid, row, col);
    if (slope == null) return null;
    final slopeRad = slope * math.pi / 180.0;
    final aspectRad = (aspect ?? 0.0) * math.pi / 180.0;
    final azimuthRad = azimuthDeg * math.pi / 180.0;
    final altitudeRad = altitudeDeg * math.pi / 180.0;
    final shade = math.sin(altitudeRad) * math.cos(slopeRad) +
        math.cos(altitudeRad) *
            math.sin(slopeRad) *
            math.cos(azimuthRad - aspectRad);
    return (shade.clamp(0.0, 1.0) * 255.0);
  }

  static List<double?> profile(
    AtlasElevationGrid grid,
    List<AtlasCoordinate> path,
  ) =>
      [for (final point in path) _sample(grid, point)];

  static List<List<double>>? _window(
    AtlasElevationGrid grid,
    int row,
    int col,
  ) {
    final window = <List<double>>[];
    for (var r = row - 1; r <= row + 1; r++) {
      final line = <double>[];
      for (var c = col - 1; c <= col + 1; c++) {
        final height = grid.at(r, c);
        if (height == null) return null;
        line.add(height);
      }
      window.add(line);
    }
    return window;
  }

  static double? _sample(AtlasElevationGrid grid, AtlasCoordinate point) {
    final metersPerDegree = 1.0 / AtlasLengthUnits.degreesPerMeter();
    final latCos = math.cos(grid.origin.latitude * math.pi / 180.0).abs().clamp(
          0.2,
          1.0,
        );
    final eastMeters =
        (point.longitude - grid.origin.longitude) * metersPerDegree * latCos;
    final northMeters =
        (point.latitude - grid.origin.latitude) * metersPerDegree;
    final colF = eastMeters / grid.cellSizeMeters;
    final rowF = northMeters / grid.cellSizeMeters;
    final col0 = colF.floor();
    final row0 = rowF.floor();
    final a = grid.at(row0, col0);
    final b = grid.at(row0, col0 + 1);
    final c = grid.at(row0 + 1, col0);
    final d = grid.at(row0 + 1, col0 + 1);
    if (a == null || b == null || c == null || d == null) return null;
    final fx = colF - col0;
    final fy = rowF - row0;
    return a * (1 - fx) * (1 - fy) +
        b * fx * (1 - fy) +
        c * (1 - fx) * fy +
        d * fx * fy;
  }
}
