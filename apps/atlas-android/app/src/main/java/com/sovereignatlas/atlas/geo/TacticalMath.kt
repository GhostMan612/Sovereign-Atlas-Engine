// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import kotlin.math.roundToInt

object TacticalMath {
    /**
     * Converts standard degrees (0-360) to NATO tactical mil-radians (0-6400).
     */
    fun degreesToMils(degrees: Float): Int {
        val normalized = ((degrees % 360) + 360) % 360
        val mils = (normalized * (6400.0f / 360.0f)).roundToInt()
        return if (mils >= 6400) 0 else mils
    }

    /**
     * Formats a bearing to a strict 3-digit string (e.g., 45f -> "045").
     */
    fun formatBearing(degrees: Float): String {
        val normalized = ((degrees.roundToInt() % 360) + 360) % 360
        return normalized.toString().padStart(3, '0')
    }
}
