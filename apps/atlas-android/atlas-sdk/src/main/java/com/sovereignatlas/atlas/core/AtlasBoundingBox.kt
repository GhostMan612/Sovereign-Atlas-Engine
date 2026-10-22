// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

data class AtlasBoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    /** True when the box crosses the 180° meridian (west > east). */
    val crossesAntimeridian: Boolean get() = west > east

    /**
     * Splits into two non-crossing boxes: [west, 180] and [-180, east].
     * Returns a single-element list when no split is needed.
     */
    fun unwrap(): List<AtlasBoundingBox> {
        if (!crossesAntimeridian) return listOf(this)
        if (west == 180.0) return listOf(AtlasBoundingBox(south, -180.0, north, east))
        if (east == -180.0) return listOf(AtlasBoundingBox(south, west, north, 180.0))

        return listOf(
            AtlasBoundingBox(south, west, north, 180.0),
            AtlasBoundingBox(south, -180.0, north, east),
        )
    }

    fun validate(): AtlasValidation {
        for (edge in listOf(south, west, north, east)) {
            if (!edge.isFinite()) {
                return AtlasValidation.invalid(
                    AtlasRejection("NON_FINITE", "Bounds edges must be finite."),
                )
            }
        }
        if (south < -90.0 || south > 90.0 || north < -90.0 || north > 90.0) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "OUT_OF_RANGE",
                    "Bounds latitudes must be within [-90, 90].",
                ),
            )
        }
        if (west < -180.0 || west > 180.0 || east < -180.0 || east > 180.0) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "OUT_OF_RANGE",
                    "Bounds longitudes must be within [-180, 180] (DEC-004).",
                ),
            )
        }
        if (south > north) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "INVALID_GEOMETRY",
                    "Bounds require south <= north.",
                ),
            )
        }
        return AtlasValidation.valid()
    }
}
