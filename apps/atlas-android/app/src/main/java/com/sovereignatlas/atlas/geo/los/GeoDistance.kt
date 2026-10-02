// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.los

import com.sovereignatlas.atlas.geo.AtlasGeoMath
import com.sovereignatlas.atlas.geo.GeoPoint

/**
 * Geodesic helpers for the line-of-sight engine.
 *
 * Named rather than reached for directly so there is exactly one definition of
 * "how far apart are these two points" in the LOS path. The previous engines
 * exposed their own internal copies as `internal fun`, which is how two engines
 * ended up nominally sharing a curvature constant while differing in everything
 * else.
 */
internal object GeoDistance {

    /** Great-circle ground distance in metres. */
    fun groundDistance(from: GeoPoint, to: GeoPoint): Double = AtlasGeoMath.haversine(
        from.latitude,
        from.longitude,
        to.latitude,
        to.longitude,
    )

    /** Great-circle interpolation to a point [distanceMeters] along the ray. */
    fun interpolate(
        from: GeoPoint,
        to: GeoPoint,
        distanceMeters: Double,
        totalMeters: Double,
    ): Pair<Double, Double> = AtlasGeoMath.interpolateGreatCircle(
        from.latitude,
        from.longitude,
        to.latitude,
        to.longitude,
        distanceMeters / totalMeters,
        totalMeters,
    )
}