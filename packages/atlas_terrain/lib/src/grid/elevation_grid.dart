// Sovereign Atlas Engine — atlas_terrain
// Elevation grid: explicit sampled heights (never fetched, never rendered).
//
// Contract: blueprint Phase 8 (terrain engine side). A grid is rows×cols of
// meters-above-datum over an explicit cell size; edges and voids are
// EXPLICIT (void = null, never interpolated silently — interpolation is a
// downstream choice with its own contract). Sampling outside bounds returns
// null (no clamping invention).
// Phase 8 slice. Depends on atlas_core + atlas_geo (coordinates only).

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

/// Explicit elevation grid value (row-major heights, meters).
final class AtlasElevationGrid {
  const AtlasElevationGrid({
    required this.rows,
    required this.cols,
    required this.cellSizeMeters,
    required this.origin,
    required this.heights,
  });

  final int rows;
  final int cols;

  /// Square cell size in meters (explicit resolution contract).
  final double cellSizeMeters;

  /// Coordinate of the south-west corner (row 0, col 0).
  final AtlasCoordinate origin;

  /// Row-major heights; null = void (unsurveyed, never zero-filled).
  final List<double?> heights;

  AtlasValidation validate() {
    if (rows <= 0 || cols <= 0 || cellSizeMeters <= 0) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_GRID',
          'Grid dimensions and cell size must be positive.',
        ),
      );
    }
    if (heights.length != rows * cols) {
      return const AtlasValidation.invalid(
        AtlasRejection(
          'INVALID_GRID',
          'Height count must equal rows × cols.',
        ),
      );
    }
    return const AtlasValidation.valid();
  }

  double? at(int row, int col) {
    if (row < 0 || row >= rows || col < 0 || col >= cols) return null;
    return heights[row * cols + col];
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AtlasElevationGrid &&
          rows == other.rows &&
          cols == other.cols &&
          cellSizeMeters == other.cellSizeMeters &&
          origin == other.origin;

  @override
  int get hashCode => Object.hash(rows, cols, cellSizeMeters, origin);
}
