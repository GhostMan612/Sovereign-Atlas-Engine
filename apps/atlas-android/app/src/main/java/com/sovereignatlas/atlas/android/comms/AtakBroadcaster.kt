// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.android.settings.SettingsRepository
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.CotProtobufGenerator
import com.sovereignatlas.atlas.geo.cot.PliBroadcastScheduler
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AtakBroadcaster(
    private val listener: AtakMulticastListener,
    private val localUid: String,
    private val settingsRepository: SettingsRepository,
    private val syncProvider: SyncProvider? = null,
    private val scheduler: PliBroadcastScheduler = PliBroadcastScheduler(),
) {
    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun broadcastPli(
        lat: Double,
        lon: Double,
        hae: Double?,
        ce: Double?,
        fixTimeMillis: Long,
    ) {
        val profile = settingsRepository.networkProfile.value
        if (profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.RADIO_SILENCE ||
            profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.CLOUD_ONLY) {
            return
        }
        if (lat == 0.0 && lon == 0.0) return
        if (!scheduler.shouldBroadcast(lat, lon)) return

        val callsign = settingsRepository.callsign.value
        val teamColor = settingsRepository.teamColor.value
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
            teamColor = teamColor,
            geoPoint = geoPoint,
            ceFallback = ce,
        )
        listener.sendMulticast(payload)
    }

    suspend fun sendMarker(type: String, callsign: String, lat: Double, lon: Double) {
        val profile = settingsRepository.networkProfile.value
        if (profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.RADIO_SILENCE ||
            profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.CLOUD_ONLY) {
            throw IllegalStateException("Markers require an active mesh profile")
        }
        val geoPoint = com.sovereignatlas.atlas.geo.GeoPoint(lat, lon, null, null, null, System.currentTimeMillis())
        val payload = CotProtobufGenerator.generateMarkerProto(
            localUid = localUid,
            type = type,
            callsign = callsign,
            geoPoint = geoPoint
        )
        listener.sendMulticast(payload)
    }

    suspend fun sendChatMessage(
        text: String,
        currentGeoPoint: com.sovereignatlas.atlas.geo.GeoPoint?,
        targetUid: String? = null,
    ): ChatMessage {
        val profile = settingsRepository.networkProfile.value
        if (profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.RADIO_SILENCE) {
            throw IllegalStateException("Radio Silence (EMCON) is Active")
        }
        if (currentGeoPoint == null) throw IllegalStateException("No GPS fix")

        val callsign = settingsRepository.callsign.value
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

        if (profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.MESH_ONLY ||
            profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.HYBRID_BRIDGE) {
            listener.sendMulticast(payload)
        }

        val sent = ChatMessage(
            messageId = messageId,
            senderUid = localUid,
            senderCallsign = callsign,
            chatroom = chatroom,
            remarksTo = remarksTo,
            text = text,
            timestampMillis = System.currentTimeMillis(),
            isSelf = true,
        )
        if (profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.CLOUD_ONLY ||
            profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.HYBRID_BRIDGE) {
            pushScope.launch {
                syncProvider?.pushChatMessage(sent)
            }
        }
        return sent
    }
}
