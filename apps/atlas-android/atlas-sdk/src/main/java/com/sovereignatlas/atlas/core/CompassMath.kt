// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

object CompassMath {
    /**
     * Normalizes any degree (positive or negative) to a strict 0-359 range.
     */
    fun normalize(degree: Int): Int {
        val d = degree % 360
        return if (d < 0) d + 360 else d
    }

    /**
     * Returns the cardinal/ordinal label for major compass points.
     */
    fun getLabel(degree: Int): String {
        return when (normalize(degree)) {
            0 -> "N"
            45 -> "NE"
            90 -> "E"
            135 -> "SE"
            180 -> "S"
            225 -> "SW"
            270 -> "W"
            315 -> "NW"
            else -> normalize(degree).toString()
        }
    }
}
