// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasCoordinates
import com.sovereignatlas.atlas.geo.AtlasGeoMath

data class RadialFence(
    val center: AtlasCoordinate,
    val radiusMeters: Double,
    val armed: Boolean = true,
) {
    fun isValid(): Boolean {
        if (radiusMeters <= 0.0) return false
        return AtlasCoordinates.validate(
            center.latitude,
            center.longitude,
        ).isValid
    }

    fun breachedBy(position: AtlasCoordinate): Boolean {
        if (!armed) return false
        return AtlasGeoMath.haversineKm(center, position) * 1000.0 <= radiusMeters
    }
}

fun fencePolygon(
    center: AtlasCoordinate,
    radiusMeters: Double,
    vertices: Int = 64,
): List<AtlasCoordinate> {
    val points = ArrayList<AtlasCoordinate>(vertices + 1)
    for (index in 0..vertices) {
        points.add(
            AtlasGeoMath.destinationPoint(
                origin = center,
                distanceKm = radiusMeters / 1000.0,
                bearingDeg = index * 360.0 / vertices,
            ),
        )
    }
    return points
}
