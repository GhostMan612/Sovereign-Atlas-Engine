// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical

import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.geo.AtlasGeoMath
import kotlin.math.ln

object AtlasRadioLink {
    fun freeSpaceLossDb(distanceMeters: Double, frequencyMHz: Double): Double {
        if (distanceMeters <= 0 || frequencyMHz <= 0) {
            throw IllegalArgumentException("Distance and frequency must be positive.")
        }
        return 20 * log10(distanceMeters) + 20 * log10(frequencyMHz) - 27.55
    }

    fun linkMarginDb(
        distanceMeters: Double,
        frequencyMHz: Double,
        txPowerDbm: Double,
        rxSensitivityDbm: Double,
        antennaGainDbi: Double = 0.0,
        extraLossDb: Double = 0.0,
    ): Double {
        return txPowerDbm +
            antennaGainDbi -
            extraLossDb -
            freeSpaceLossDb(distanceMeters, frequencyMHz) -
            rxSensitivityDbm
    }

    fun rangeMeters(a: AtlasCoordinate, b: AtlasCoordinate): Double {
        return AtlasGeoMath.haversineKm(a, b) * 1000.0
    }

    private fun log10(value: Double): Double = kotlin.math.ln(value) / ln(10.0)
}
