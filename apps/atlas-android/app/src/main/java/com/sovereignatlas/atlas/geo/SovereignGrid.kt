// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

object SovereignGrid {
    const val SCHEME_ID = "sovereign-grid-v1"

    const val RESOLUTION_STANDARD = 6

    const val RESOLUTION_EMERGENCY = 10

    fun geoToCell(latDeg: Double, lngDeg: Double, resolution: Int): Long {
        val res = resolution.coerceIn(0, 15)
        val gridBits = (15 + res).coerceAtMost(30)
        val cells = 1L shl gridBits

        val latNorm = ((latDeg + 90.0) / 180.0).coerceIn(0.0, 1.0)
        val lngNorm = ((lngDeg + 180.0) / 360.0).coerceIn(0.0, 1.0)
        val latIdx = (latNorm * (cells - 1)).toLong().coerceIn(0L, cells - 1)
        val lngIdx = (lngNorm * (cells - 1)).toLong().coerceIn(0L, cells - 1)

        return (res.toLong() and 0xFL shl 60) or
            (latIdx and MASK_30 shl 30) or
            (lngIdx and MASK_30)
    }

    fun resolutionOf(cell: Long): Int = (cell ushr 60 and 0xFL).toInt()

    fun cellToLatLng(cell: Long): Pair<Double, Double> {
        val cells = gridCells(cell)
        val latIdx = cell ushr 30 and MASK_30
        val lngIdx = cell and MASK_30
        val lat = latIdx.toDouble() / (cells - 1) * 180.0 - 90.0
        val lng = lngIdx.toDouble() / (cells - 1) * 360.0 - 180.0
        return lat to lng
    }

    fun cellPitchDegrees(cell: Long): Pair<Double, Double> {
        val cells = gridCells(cell)
        return 180.0 / cells to 360.0 / cells
    }

    fun cellBoundary(cell: Long, minRadiusDeg: Double = 0.0): List<Pair<Double, Double>> {
        val (lat, lng) = cellToLatLng(cell)
        val (pitchLat, pitchLng) = cellPitchDegrees(cell)
        val rLat = maxOf(pitchLat * 0.58, minRadiusDeg)
        val rLng = maxOf(pitchLng * 0.58, minRadiusDeg)
        return (0..6).map { i ->
            val angle = Math.toRadians(60.0 * i - 30.0)
            val vLat = (lat + rLat * Math.sin(angle)).coerceIn(-90.0, 90.0)
            val vLng = (lng + rLng * Math.cos(angle)).coerceIn(-180.0, 180.0)
            vLat to vLng
        }
    }

    private fun gridCells(cell: Long): Long {
        val gridBits = (15 + resolutionOf(cell)).coerceAtMost(30)
        return 1L shl gridBits
    }

    private const val MASK_30 = 0x3FFFFFFFL
}
