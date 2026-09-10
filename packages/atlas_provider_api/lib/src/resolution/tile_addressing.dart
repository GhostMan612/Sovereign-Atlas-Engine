// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'dart:math' as math;

import 'package:atlas_core/atlas_core.dart';
import '../requests/tile_coordinate.dart';

abstract final class AtlasTileAddressing {

  static const double mercatorMaxLatitude = 85.05112878;

  static AtlasTileCoordinate address({
    required double latitude,
    required double longitude,
    required int z,
  }) {
    if (!latitude.isFinite || !longitude.isFinite) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'NON_FINITE',
          'Tile addressing requires finite coordinates.',
        ),
      );
    }
    if (latitude < -90.0 ||
        latitude > 90.0 ||
        longitude < -180.0 ||
        longitude > 180.0) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'OUT_OF_RANGE',
          'Tile addressing requires ATLAS-COORD-001 bounds.',
        ),
      );
    }
    if (z < 0 || z > AtlasTileCoordinate.maxZoom) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'INVALID_TILE',
          'Tile zoom must be within [0, 32] (PROPOSED practical bound).',
        ),
      );
    }
    if (latitude.abs() > mercatorMaxLatitude) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'OUT_OF_RANGE',
          'Latitude is outside web-mercator tile-grid bounds '
              '(±85.05112878); no polar clamp is performed.',
        ),
      );
    }

    final lon = longitude == 180.0 ? -180.0 : longitude;
    final n = 1 << z;
    final x = (((lon + 180.0) / 360.0) * n).floor();
    final latRad = latitude * math.pi / 180.0;
    final fraction =
        (1.0 - math.log(math.tan(latRad) + 1.0 / math.cos(latRad)) / math.pi) /
            2.0;
    final clamped = fraction < 0.0 ? 0.0 : (fraction > 1.0 ? 1.0 : fraction);
    final y = (clamped * n).floor();
    return AtlasTileCoordinate(z: z, x: x, y: y);
  }

  static int tileZoomFor(double zoom) {
    if (!zoom.isFinite || zoom < 0) {
      throw const AtlasRejectionException(
        AtlasRejection(
          'INVALID_REQUEST',
          'Tile-zoom derivation requires finite non-negative zoom.',
        ),
      );
    }
    return zoom.floor();
  }
}
