// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import com.sovereignatlas.atlas.geo.AtlasGeoMath

class PliBroadcastScheduler(
    private val baseIntervalMillis: Long = 60_000L,
    private val movementThresholdMeters: Double = 50.0,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastTime: Long = 0L

    fun shouldBroadcast(lat: Double, lon: Double): Boolean {
        val now = timeProvider()
        if (now - lastTime >= baseIntervalMillis) {
            update(lat, lon, now)
            return true
        }
        val previousLat = lastLat ?: return true
        val previousLon = lastLon ?: return true
        val dist = AtlasGeoMath.haversine(previousLat, previousLon, lat, lon)

        if (dist >= movementThresholdMeters) {
            update(lat, lon, now)
            return true
        }
        return false
    }

    private fun update(lat: Double, lon: Double, time: Long) {
        lastLat = lat
        lastLon = lon
        lastTime = time
    }
}
