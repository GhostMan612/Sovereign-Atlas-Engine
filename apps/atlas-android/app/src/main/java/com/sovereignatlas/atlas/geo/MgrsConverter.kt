// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo

import java.util.Locale

object MgrsConverter {
    fun toMgrs(latitude: Double, longitude: Double): String {
        val ref = Mgrs.gridRef(latitude, longitude)
        if (ref.offGrid) return Mgrs.OFF_GRID
        val easting = ref.easting % 100_000
        val northing = ref.northing % 100_000
        return "${ref.zone}${ref.band}${ref.square}" +
            String.format(Locale.US, "%05d%05d", easting, northing)
    }

    fun spaced(canonical: String): String {
        val match = Regex("""^(\d{1,2}[A-Z])(\w{2})(\d{5})(\d{5})$""").matchEntire(canonical)
        if (match == null) return canonical
        val (zoneBand, square, easting, northing) = match.destructured
        return "$zoneBand $square $easting $northing"
    }
}
