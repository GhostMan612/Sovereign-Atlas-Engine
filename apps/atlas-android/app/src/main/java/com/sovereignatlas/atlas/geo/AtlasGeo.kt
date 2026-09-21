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

    fun formatBearing(degrees: Double): String {
        return "BRG ${degrees.round().toString().padStart(3, '0')}°"
    }

    private fun Double.round(): Long = kotlin.math.round(this).toLong()

    private fun radians(degrees: Double): Double = degrees * Math.PI / 180.0
}

fun AtlasId.checkId(): AtlasValidation = AtlasIds.check(value)
