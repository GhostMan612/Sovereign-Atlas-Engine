// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

import 'package:atlas_core/atlas_core.dart';
import 'package:atlas_geo/atlas_geo.dart';

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

  final double cellSizeMeters;

  final AtlasCoordinate origin;

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
