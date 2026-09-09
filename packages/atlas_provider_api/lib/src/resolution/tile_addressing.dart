// Sovereign Atlas Engine — atlas_provider_api
// AtlasTileAddressing: deterministic geographic → tile-coordinate resolution.
//
// Contract: 1.5-C (inventory: ATLAS-NORMATIVE definitional standard).
// Standard web-mercator slippy grid shared by the observed z/x/y schemes:
//   x = floor((lon + 180) / 360 * 2^z)
//   y = floor(clamp01((1 − ln(tanφ + secφ) / π) / 2) * 2^z)
// Documented edge handling (no silent invention):
// - lon ≡ ±180 is one meridian: exactly ±180.0 addresses as −180.0 (math
//   identity, explicit here; validation policy still rejects |lon| > 180 and
//   DEC-004 stays open).
// - |lat| > mercatorMaxLatitude (±85.05112878, grid definitional bound):
//   throws OUT_OF_RANGE — no silent polar clamp (1.5-C polar evaluation).
// - The [0,1] clamp on the mercator fraction is float hygiene (prevents dust
//   like maxlat-z5 → y=−1, verified in inventory), not policy: inputs are
//   already range-validated when it applies.
// - Fractional zoom floors to tile-zoom (PROVISIONAL web-map standard;
//   overzoom rendering is downstream business, not addressing).
// Pure math on doubles (no geo dependency — see request docs). Deterministic.
// Phase 1.5 slice. Depends on atlas_core + dart:math only.

import 'dart:math' as math;

import '../../../../atlas_core/lib/atlas_core.dart';
import '../requests/tile_coordinate.dart';

/// Geographic → tile-grid addressing. No I/O, no providers, no state.
abstract final class AtlasTileAddressing {
  /// Web-mercator latitude bound (grid definitional standard, degrees).
  static const double mercatorMaxLatitude = 85.05112878;

  /// Addresses ([latitude], [longitude]) at integer tile-zoom [z].
  /// Throws [AtlasRejectionException] (NON_FINITE / OUT_OF_RANGE /
  /// INVALID_TILE) instead of coercing.
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
    // Meridian identity: +180 ≡ −180 for addressing (explicit, documented).
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

  /// Integer tile-zoom for a semantic [zoom]: floor (PROVISIONAL standard).
  /// Throws INVALID_REQUEST for non-finite or negative zoom.
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
