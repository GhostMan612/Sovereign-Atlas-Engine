// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object Mgrs {

    const val OFF_GRID = "—— OFF-GRID (POLAR) ——"

    private const val A = 6_378_137.0
    private const val F = 1.0 / 298.257223563
    private const val K0 = 0.9996
    private val E2 = F * (2 - F)
    private val EP2 = E2 / (1 - E2)

    private const val LAT_BANDS = "CDEFGHJKLMNPQRSTUVWX"

    private const val SET_ORIGIN_COLUMN = "AJSAJS"
    private const val SET_ORIGIN_ROW = "AFAFAF"

    data class GridRef(
        val zone: Int,
        val band: Char,
        val square: String,
        val easting: Int,
        val northing: Int,
        val offGrid: Boolean = false,
    )

    fun format(latDeg: Double, lngDeg: Double, digits: Int = 5): String {
        val ref = gridRef(latDeg, lngDeg)
        if (ref.offGrid) return OFF_GRID
        val d = digits.coerceIn(1, 5)
        val scale = intPow10(5 - d)
        val e = (ref.easting % 100_000) / scale
        val n = (ref.northing % 100_000) / scale
        val width = d
        return "%d%c %s %0${width}d %0${width}d".format(ref.zone, ref.band, ref.square, e, n)
    }

    fun gridRef(latDeg: Double, lngDeg: Double): GridRef {
        if (latDeg < -80.0 || latDeg > 84.0) {
            return GridRef(0, 'Z', "", 0, 0, offGrid = true)
        }
        val lng = ((lngDeg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        val zone = zoneNumber(latDeg, lng)
        val band = LAT_BANDS[floor((latDeg + 80.0) / 8.0).toInt().coerceIn(0, 19)]

        val (easting, northing) = utm(latDeg, lng, zone)
        val square = squareId(easting, northing, zone)
        return GridRef(zone, band, square, easting.toInt(), northing.toInt())
    }

    private fun utm(latDeg: Double, lngDeg: Double, zone: Int): Pair<Double, Double> {
        val lat = Math.toRadians(latDeg)
        val lng = Math.toRadians(lngDeg)
        val lng0 = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val tanLat = tan(lat)

        val n = A / sqrt(1 - E2 * sinLat * sinLat)
        val t = tanLat * tanLat
        val c = EP2 * cosLat * cosLat
        val a1 = cosLat * (lng - lng0)

        val m = A * ((1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256) * lat
            - (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2 * E2 * E2 / 1024) * sin(2 * lat)
            + (15 * E2 * E2 / 256 + 45 * E2 * E2 * E2 / 1024) * sin(4 * lat)
            - (35 * E2 * E2 * E2 / 3072) * sin(6 * lat))

        val easting = K0 * n * (a1 + (1 - t + c) * a1 * a1 * a1 / 6
            + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * a1 * a1 * a1 * a1 * a1 / 120) + 500_000.0

        var northing = K0 * (m + n * tanLat * (a1 * a1 / 2
            + (5 - t + 9 * c + 4 * c * c) * a1 * a1 * a1 * a1 / 24
            + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * a1 * a1 * a1 * a1 * a1 * a1 / 720))
        if (latDeg < 0) northing += 10_000_000.0

        return easting to northing
    }

    private fun zoneNumber(latDeg: Double, lngDeg: Double): Int {
        var zone = (floor((lngDeg + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
        if (latDeg in 56.0..64.0 && lngDeg in 3.0..12.0) zone = 32
        if (latDeg in 72.0..84.0) {
            zone = when {
                lngDeg in 0.0..9.0 -> 31
                lngDeg in 9.0..21.0 -> 33
                lngDeg in 21.0..33.0 -> 35
                lngDeg in 33.0..42.0 -> 37
                else -> zone
            }
        }
        return zone
    }

    private const val A_CODE = 'A'.code
    private const val I_CODE = 'I'.code
    private const val O_CODE = 'O'.code
    private const val V_CODE = 'V'.code
    private const val Z_CODE = 'Z'.code

    private fun squareId(easting: Double, northing: Double, zone: Int): String {
        val setIndex = (zone - 1) % 6
        val column = floor(easting / 100_000.0).toInt()
        val row = floor(northing / 100_000.0).toInt() % 20
        val colOrigin = SET_ORIGIN_COLUMN[setIndex].code
        val rowOrigin = SET_ORIGIN_ROW[setIndex].code

        var colInt = colOrigin + column - 1
        var rollover = false
        if (colInt > Z_CODE) {
            colInt = colInt - Z_CODE + A_CODE - 1
            rollover = true
        }
        if (colInt == I_CODE || (colOrigin < I_CODE && colInt > I_CODE) ||
            ((colInt > I_CODE || colOrigin < I_CODE) && rollover)
        ) {
            colInt++
        }
        if (colInt == O_CODE || (colOrigin < O_CODE && colInt > O_CODE) ||
            ((colInt > O_CODE || colOrigin < O_CODE) && rollover)
        ) {
            colInt++
            if (colInt == I_CODE) colInt++
        }
        if (colInt > Z_CODE) colInt = colInt - Z_CODE + A_CODE - 1

        var rowInt = rowOrigin + row
        rollover = false
        if (rowInt > V_CODE) {
            rowInt = rowInt - V_CODE + A_CODE - 1
            rollover = true
        }
        if (rowInt == I_CODE || (rowOrigin < I_CODE && rowInt > I_CODE) ||
            ((rowInt > I_CODE || rowOrigin < I_CODE) && rollover)
        ) {
            rowInt++
        }
        if (rowInt == O_CODE || (rowOrigin < O_CODE && rowInt > O_CODE) ||
            ((rowInt > O_CODE || rowOrigin < O_CODE) && rollover)
        ) {
            rowInt++
            if (rowInt == I_CODE) rowInt++
        }
        if (rowInt > V_CODE) rowInt = rowInt - V_CODE + A_CODE - 1

        return "" + colInt.toChar() + rowInt.toChar()
    }

    private fun intPow10(exp: Int): Int {
        var r = 1
        repeat(exp) { r *= 10 }
        return r
    }
}
