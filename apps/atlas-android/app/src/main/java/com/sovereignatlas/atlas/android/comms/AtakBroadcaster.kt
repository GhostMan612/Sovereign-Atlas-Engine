// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.geo.cot.CotGenerator
import com.sovereignatlas.atlas.geo.cot.PliBroadcastScheduler
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AtakBroadcaster(
    private val listener: AtakMulticastListener,
    private val localUid: String,
    private val callsign: String,
    private val scheduler: PliBroadcastScheduler = PliBroadcastScheduler(),
) {
    suspend fun broadcastPli(
        lat: Double,
        lon: Double,
        hae: Double?,
        ce: Double,
        fixTimeMillis: Long = System.currentTimeMillis(),
    ) {
        if (lat == 0.0 && lon == 0.0) return
        if (!scheduler.shouldBroadcast(lat, lon)) return

        val xml = CotGenerator.generatePliXml(
            uid = localUid,
            callsign = callsign,
            latitude = lat,
            longitude = lon,
            altitude = hae ?: 0.0,
            accuracyMeters = ce,
            observedAt = Instant.ofEpochMilli(fixTimeMillis),
        )

        val header = byteArrayOf(0xBF.toByte(), 0x00.toByte(), 0xBF.toByte())
        val payload = header + xml.toByteArray(Charsets.UTF_8)

        withContext(Dispatchers.IO) {
            listener.sendMulticast(payload)
        }
    }
}
