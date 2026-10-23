// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.elevation

import kotlin.math.round

object MapboxDemDecoder {

    fun decodeRgbToElevation(r: Int, g: Int, b: Int): Double {
        val raw = ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
        return -10000.0 + (raw * 0.1)
    }

    fun encodeElevationToRgb(elevationMeters: Double): Triple<Int, Int, Int> {
        val clampedElevation = elevationMeters.coerceIn(-10000.0, 10000.0)
        val raw = round((clampedElevation + 10000.0) * 10.0).toInt().coerceIn(0, 0xFFFFFF)
        val r = (raw shr 16) and 0xFF
        val g = (raw shr 8) and 0xFF
        val b = raw and 0xFF
        return Triple(r, g, b)
    }

    fun decodePixel(pixelRgb: Int): Double {
        val r = (pixelRgb shr 16) and 0xFF
        val g = (pixelRgb shr 8) and 0xFF
        val b = pixelRgb and 0xFF
        return decodeRgbToElevation(r, g, b)
    }
}
