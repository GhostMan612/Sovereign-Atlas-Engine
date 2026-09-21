// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.camera

import com.sovereignatlas.atlas.core.AtlasContractException
import com.sovereignatlas.atlas.core.AtlasRejection
import com.sovereignatlas.atlas.core.AtlasValidation
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasCoordinates

data class AtlasCameraState(
    val center: AtlasCoordinate,
    val zoom: Double,
    val bearing: Double,
    val pitch: Double,
) {
    fun validate(): AtlasValidation {
        val centerCheck = AtlasCoordinates.validate(
            center.latitude,
            center.longitude,
        )
        if (!centerCheck.isValid) return centerCheck
        if (!zoom.isFinite() || zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "INVALID_ZOOM",
                    "Zoom must be within [0, 24] (SOURCE-VERIFIED guard).",
                ),
            )
        }
        if (!bearing.isFinite()) {
            return AtlasValidation.invalid(
                AtlasRejection("NON_FINITE", "Bearing must be finite."),
            )
        }
        if (!pitch.isFinite() || pitch < 0.0 || pitch > MAX_PITCH) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "OUT_OF_RANGE",
                    "Pitch must be within [0, 85] (SOURCE-VERIFIED guard).",
                ),
            )
        }
        return AtlasValidation.valid()
    }

    fun serialize(): String =
        "${center.latitude}|${center.longitude}|$zoom|$bearing|$pitch"

    fun copyWith(
        center: AtlasCoordinate? = null,
        zoom: Double? = null,
        bearing: Double? = null,
        pitch: Double? = null,
    ): AtlasCameraState = AtlasCameraState(
        center = center ?: this.center,
        zoom = zoom ?: this.zoom,
        bearing = bearing ?: this.bearing,
        pitch = pitch ?: this.pitch,
    )

    override fun toString() = "AtlasCameraState(${serialize()})"

    companion object {
        const val MAX_ZOOM = 24.0
        const val MIN_ZOOM = 0.0
        const val MAX_PITCH = 85.0

        fun home() = AtlasCameraState(
            center = AtlasCoordinate(latitude = 39.83, longitude = -98.58),
            zoom = 3.0,
            bearing = 0.0,
            pitch = 0.0,
        )

        fun parse(serialized: String): AtlasCameraState {
            val parts = serialized.split('|')
            if (parts.size != 5) {
                throw AtlasContractException(
                    AtlasRejection(
                        "MALFORMED",
                        "Camera state requires 5 pipe-separated fields.",
                    ),
                )
            }
            fun part(text: String, field: String): Double {
                return text.toDoubleOrNull() ?: throw AtlasContractException(
                    AtlasRejection(
                        "MALFORMED",
                        "Camera field \"$field\" is not a number: \"$text\".",
                    ),
                )
            }
            val candidate = AtlasCameraState(
                center = AtlasCoordinate(
                    latitude = part(parts[0], "latitude"),
                    longitude = part(parts[1], "longitude"),
                ),
                zoom = part(parts[2], "zoom"),
                bearing = part(parts[3], "bearing"),
                pitch = part(parts[4], "pitch"),
            )
            val validation = candidate.validate()
            if (!validation.isValid) {
                throw AtlasContractException(validation.rejection!!)
            }
            return candidate
        }
    }
}
