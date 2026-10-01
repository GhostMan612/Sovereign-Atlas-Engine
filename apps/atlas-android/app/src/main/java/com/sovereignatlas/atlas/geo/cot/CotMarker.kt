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
    val timestampMillis: Long,
    /**
     * Epoch millis at which the producer declared this report stale, from the CoT
     * `stale` attribute. Null when the message carried no readable `stale`, in
     * which case [MarkerStore] falls back to `timestampMillis + TTL_MILLIS`.
     */
    val expiresAtMillis: Long? = null,
)
