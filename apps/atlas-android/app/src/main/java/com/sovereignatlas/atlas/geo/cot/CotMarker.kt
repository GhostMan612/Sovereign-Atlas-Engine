// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

data class CotMarker(
    val uid: String,
    val type: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val timestampMillis: Long
)
