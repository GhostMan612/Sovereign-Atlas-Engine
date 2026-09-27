// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.CotProtobufGenerator
import com.sovereignatlas.atlas.geo.cot.PliBroadcastScheduler

class AtakBroadcaster(
    private val listener: AtakMulticastListener,
    private val localUid: String,
    private val callsignProvider: () -> String,
    private val scheduler: PliBroadcastScheduler = PliBroadcastScheduler(),
) {
    suspend fun broadcastPli(
        lat: Double,
        lon: Double,
        hae: Double?,
        ce: Double?,
        fixTimeMillis: Long,
    ) {
        if (lat == 0.0 && lon == 0.0) return
        if (!scheduler.shouldBroadcast(lat, lon)) return
        val callsign = callsignProvider()
        val geoPoint = com.sovereignatlas.atlas.geo.GeoPoint(
            lat,
            lon,
            hae,
            null,
            null,
            fixTimeMillis,
        )

        val payload = CotProtobufGenerator.generatePliProto(
            localUid = localUid,
            callsign = callsign,
            geoPoint = geoPoint,
            ceFallback = ce,
        )
        listener.sendMulticast(payload)
    }

    suspend fun sendChatMessage(
        text: String,
        currentGeoPoint: com.sovereignatlas.atlas.geo.GeoPoint?,
        targetUid: String? = null,
    ): ChatMessage {
        if (currentGeoPoint == null) throw IllegalStateException("No GPS fix")

        val callsign = callsignProvider()
        val messageId = java.util.UUID.randomUUID().toString()
        val chatroom = "All Chat Rooms"
        val remarksTo = targetUid ?: chatroom

        val payload = CotProtobufGenerator.generateChatProto(
            localUid = localUid,
            callsign = callsign,
            geoPoint = currentGeoPoint,
            text = text,
            messageId = messageId,
            chatroom = chatroom,
            targetUid = targetUid,
        )

        listener.sendMulticast(payload)

        return ChatMessage(
            messageId = messageId,
            senderUid = localUid,
            senderCallsign = callsign,
            chatroom = chatroom,
            remarksTo = remarksTo,
            text = text,
            timestampMillis = System.currentTimeMillis(),
            isSelf = true,
        )
    }
}
