// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.core.AtlasId
import com.sovereignatlas.atlas.core.AtlasIds
import com.sovereignatlas.atlas.core.AtlasRejection
import com.sovereignatlas.atlas.core.AtlasValidation
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class AtlasCoordinate(
    val latitude: Double,
    val longitude: Double,
    val crs: String = WGS84,
) {
    override fun toString() = "AtlasCoordinate($latitude, $longitude, $crs)"

    companion object {
        const val WGS84 = "WGS84"
    }
}

object AtlasCoordinates {
    fun validate(
        latitude: Double,
        longitude: Double,
        crs: String = AtlasCoordinate.WGS84,
    ): AtlasValidation {
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "NON_FINITE",
                    "Coordinates must be finite numbers.",
                ),
            )
        }
        if (latitude < -90.0 || latitude > 90.0) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "OUT_OF_RANGE",
                    "Latitude must be within [-90, 90].",
                ),
            )
        }
        if (longitude < -180.0 || longitude > 180.0) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "OUT_OF_RANGE",
                    "Longitude must be within [-180, 180] (normalization undecided, DEC-004).",
                ),
            )
        }
        if (crs != AtlasCoordinate.WGS84) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "UNSUPPORTED_CRS",
                    "Only WGS84 coordinates are accepted; unknown CRS is never assumed.",
                ),
            )
        }
        return AtlasValidation.valid()
    }

    fun checked(
        latitude: Double,
        longitude: Double,
        crs: String = AtlasCoordinate.WGS84,
    ): AtlasCoordinate {
        val validation = validate(latitude, longitude, crs)
        if (!validation.isValid) {
            throw AtlasContractException(validation.rejection!!)
        }
        return AtlasCoordinate(latitude, longitude, crs)
    }
}

object AtlasAngles {
    fun normalizeBearingDeg(degrees: Double): Double {
        requireFinite(degrees, "bearing")
        return ((degrees % 360.0) + 360.0) % 360.0
    }

    fun normalizeSignedDeg(degrees: Double): Double {
        requireFinite(degrees, "signed angle")
        val wrapped = normalizeBearingDeg(degrees)
        return if (wrapped > 180.0) wrapped - 360.0 else wrapped
    }

    fun normalizeLongitudeDeg(longitude: Double): Double =
        normalizeSignedDeg(longitude)

    private fun requireFinite(value: Double, what: String) {
        if (!value.isFinite()) {
            throw AtlasContractException(
                AtlasRejection("NON_FINITE", "$what must be finite to normalize."),
            )
        }
    }
}

object AtlasGeoMath {
    const val REFERENCE_RADIUS_KM = 6371.0088
    const val SPHERE_RADIUS_M = 6371000.0

    fun haversineKm(
        from: AtlasCoordinate,
        to: AtlasCoordinate,
        radiusKm: Double = REFERENCE_RADIUS_KM,
    ): Double {
        val lat1 = radians(from.latitude)
        val lat2 = radians(to.latitude)
        val dLat = radians(to.latitude - from.latitude)
        val dLon = radians(to.longitude - from.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * radiusKm * asin(sqrt(h))
    }

    fun initialBearingDeg(from: AtlasCoordinate, to: AtlasCoordinate): Double {
        if (from.latitude == to.latitude && from.longitude == to.longitude) {
            throw AtlasContractException(
                AtlasRejection(
                    "COINCIDENT_POINTS",
                    "Bearing is undefined for identical points (provisional; BRG-004 open).",
                ),
            )
        }
        val lat1 = radians(from.latitude)
        val lat2 = radians(to.latitude)
        val dLon = radians(to.longitude - from.longitude)
        val x = sin(dLon) * cos(lat2)
        val y = cos(lat1) * sin(lat2) -
            sin(lat1) * cos(lat2) * cos(dLon)
        return AtlasAngles.normalizeBearingDeg(atan2(x, y) * 180.0 / Math.PI)
    }

    fun destinationPoint(
        origin: AtlasCoordinate,
        distanceKm: Double,
        bearingDeg: Double,
        radiusKm: Double = REFERENCE_RADIUS_KM,
    ): AtlasCoordinate {
        val angular = distanceKm / radiusKm
        val bearing =
            AtlasAngles.normalizeBearingDeg(bearingDeg) * Math.PI / 180.0
        val lat1 = radians(origin.latitude)
        val lon1 = radians(origin.longitude)
        val lat2 = asin(
            sin(lat1) * cos(angular) +
                cos(lat1) * sin(angular) * cos(bearing),
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return AtlasCoordinate(
            latitude = lat2 * 180.0 / Math.PI,
            longitude = AtlasAngles.normalizeSignedDeg(lon2 * 180.0 / Math.PI),
        )
    }

    fun formatDistance(km: Double): String {
        if (km < 1.0) {
            return "${(km * 1000).round()} M"
        }
        return "${"%.2f".format(java.util.Locale.US, km)} KM"
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * SPHERE_RADIUS_M * asin(sqrt(h))
    }

    /**
     * Great-circle path interpolation.
     * Do NOT use for MGRS grid line densification — MGRS requires UTM-space linear interpolation.
     */
    fun interpolateGreatCircle(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        fraction: Double,
        totalMeters: Double,
    ): Pair<Double, Double> {
        if (totalMeters == 0.0) return lat1 to lon1
        val p1 = Math.toRadians(lat1)
        val l1 = Math.toRadians(lon1)
        val p2 = Math.toRadians(lat2)
        val l2 = Math.toRadians(lon2)
        val delta = totalMeters / SPHERE_RADIUS_M
        val a = sin((1 - fraction) * delta) / sin(delta)
        val b = sin(fraction * delta) / sin(delta)
        val x = a * cos(p1) * cos(l1) + b * cos(p2) * cos(l2)
        val y = a * cos(p1) * sin(l1) + b * cos(p2) * sin(l2)
        val z = a * sin(p1) + b * sin(p2)
        return Math.toDegrees(atan2(z, sqrt(x * x + y * y))) to
            Math.toDegrees(atan2(y, x))
    }

    /**
     * A point at [fraction] along the great-circle segment [from]-[to].
     *
     * Distinct from [interpolateGreatCircle], which takes an ABSOLUTE distance and
     * a precomputed total. This one measures the segment itself, which is what a
     * nearest-point-on-edge or distance-field query actually wants — the caller has
     * the two endpoints and not the length.
     */
    fun interpolateAlongSegment(
        from: AtlasCoordinate,
        to: AtlasCoordinate,
        fraction: Double,
    ): AtlasCoordinate {
        val totalKm = haversineKm(from, to)
        if (totalKm <= 1e-12) return from
        val clamped = fraction.coerceIn(0.0, 1.0)
        return destinationPoint(from, totalKm * clamped, initialBearingDeg(from, to), totalKm.let { _ ->
            REFERENCE_RADIUS_KM
        })
    }

    /**
     * Total great-circle length of a polyline, in km.
     *
     * A polyline is open by definition, so vertices are summed pairwise with no
     * closing segment. Adding one would inflate the length by the gap between the
     * last and first vertex.
     */
    fun polylineLengthKm(vertices: List<AtlasCoordinate>): Double {
        if (vertices.size < 2) return 0.0
        var total = 0.0
        for (index in 0 until vertices.size - 1) {
            total += haversineKm(vertices[index], vertices[index + 1])
        }
        return total
    }

    /**
     * Rhumb-line (loxodromic) distance in km: constant bearing, not constant
     * great-circle heading.
     *
     * Added because [haversineKm] alone makes "distance" ambiguous on a
     * rhumb-line navigation problem, and the two diverge by up to ~0.5% at high
     * latitude over long legs. Which one an operator wants depends on whether they
     * are plotting a route to steer or measuring ground covered, so both are
     * offered rather than one being silently substituted for the other.
     */
    fun rhumbDistanceKm(
        from: AtlasCoordinate,
        to: AtlasCoordinate,
        radiusKm: Double = REFERENCE_RADIUS_KM,
    ): Double {
        val lat1 = radians(from.latitude)
        val lat2 = radians(to.latitude)
        val dLat = lat2 - lat1
        var dLon = Math.toRadians(to.longitude - from.longitude)

        // Crossing the antimeridian the short way is the intended reading.
        if (dLon > Math.PI) dLon -= 2 * Math.PI
        if (dLon < -Math.PI) dLon += 2 * Math.PI

        val dPsi = kotlin.math.ln(
            kotlin.math.tan(lat2 / 2 + Math.PI / 4) / kotlin.math.tan(lat1 / 2 + Math.PI / 4),
        )
        val q = if (dLat == 0.0 && dPsi == 0.0) {
            0.0
        } else {
            dPsi / dLat
        }
        val distance = kotlin.math.sqrt(dLat * dLat + q * q * dLon * dLon)
        return distance * radiusKm
    }

    fun formatBearing(degrees: Double): String {
        return "BRG ${degrees.round().toString().padStart(3, '0')}°"
    }

    private fun Double.round(): Long = kotlin.math.round(this).toLong()

    private fun radians(degrees: Double): Double = degrees * Math.PI / 180.0
}

fun AtlasId.checkId(): AtlasValidation = AtlasIds.check(value)
