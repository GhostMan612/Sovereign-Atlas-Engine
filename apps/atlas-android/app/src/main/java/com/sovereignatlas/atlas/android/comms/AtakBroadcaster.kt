// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.comms

import com.sovereignatlas.atlas.android.mesh.MeshTransportSupervisor
import com.sovereignatlas.atlas.android.settings.SettingsRepository
import com.sovereignatlas.atlas.android.settings.CloudPolicySnapshot
import com.sovereignatlas.atlas.geo.cot.ChatMessage
import com.sovereignatlas.atlas.geo.cot.CotProtobufGenerator
import com.sovereignatlas.atlas.geo.cot.PliBroadcastScheduler
import com.sovereignatlas.atlas.geo.cot.SyncProvider
import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.TransmissionOutcome
import com.sovereignatlas.atlas.tactical.mesh.PortNum

class AtakBroadcaster(
    private val listener: AtakMulticastListener,
    private val localUid: String,
    private val settingsRepository: SettingsRepository,
    private val syncProvider: () -> SyncProvider = { OfflineSyncProvider },
    private val scheduler: PliBroadcastScheduler = PliBroadcastScheduler(),
    private val meshSupervisor: MeshTransportSupervisor? = null,
) {
    suspend fun broadcastPli(
        lat: Double,
        lon: Double,
        hae: Double?,
        ce: Double?,
        fixTimeMillis: Long,
    ) {
        val snapshot = settingsRepository.cloudPolicy.snapshot()
        val profile = snapshot.profile
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
        settingsRepository.cloudPolicy.meshPermit(snapshot).submit { listener.sendMulticast(payload) }
        val supervisor = meshSupervisor
        if (supervisor != null && supervisor.canTransmit()) {
            val radio = supervisor.radioClient
            if (radio.isReady.value) {
                val myNode = supervisor.activeRadioInfo.value?.nodeNum ?: 0L
                supervisor.sendPacket(
                    MeshPacket(
                        from = myNode,
                        to = 0xFFFFFFFFL,
                        portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                        payload = payload,
                    ), snapshot
                )
            }
        }
    }

    suspend fun sendMarker(type: String, callsign: String, lat: Double, lon: Double, snapshot: CloudPolicySnapshot = settingsRepository.cloudPolicy.snapshot()): MarkerSendResult {
        val profile = snapshot.profile
        if (profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.RADIO_SILENCE ||
            profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.CLOUD_ONLY) {
            throw IllegalStateException("Markers require an active mesh profile")
        }
        val geoPoint = com.sovereignatlas.atlas.geo.GeoPoint(lat, lon, null, null, null, System.currentTimeMillis())
        val markerUid = CotProtobufGenerator.buildMarkerUid(localUid)
        val payload = CotProtobufGenerator.generateMarkerProto(
            localUid = localUid,
            type = type,
            callsign = callsign,
            geoPoint = geoPoint,
            markerUid = markerUid
        )
        val sentMulticast = settingsRepository.cloudPolicy.meshPermit(snapshot).submit { listener.sendMulticast(payload) } ?: false
        val supervisor = meshSupervisor
        val meshOutcome = if (supervisor != null && supervisor.canTransmit()) {
            runCatching {
                supervisor.sendCotMarkerOutcome(type, callsign, lat, lon, snapshot)
            }.getOrDefault(TransmissionOutcome.UNCONFIRMED)
        } else {
            TransmissionOutcome.NOT_SUBMITTED
        }
        return MarkerSendResult(markerUid, sentMulticast || meshOutcome == TransmissionOutcome.SUBMITTED, meshOutcome)
    }

    suspend fun sendChatMessage(
        text: String,
        currentGeoPoint: com.sovereignatlas.atlas.geo.GeoPoint?,
        snapshot: CloudPolicySnapshot,
        targetUid: String? = null,
        messageId: String = java.util.UUID.randomUUID().toString(),
    ): ChatSendResult {
        val profile = snapshot.profile
        if (profile == com.sovereignatlas.atlas.android.settings.NetworkProfile.RADIO_SILENCE) {
            throw IllegalStateException("Radio Silence (EMCON) is Active")
        }
        if (currentGeoPoint == null) throw IllegalStateException("No GPS fix")

        val callsign = settingsRepository.callsign.value
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
        return deliverChat(profile, sent, settingsRepository.cloudPolicy, syncProvider, snapshot.authorization, settingsRepository.chatOutbox) {
            settingsRepository.cloudPolicy.meshPermit(snapshot).submit { listener.sendMulticast(payload) } ?: false
        }
    }

    suspend fun retryCloudMessage(message: ChatMessage, snapshot: CloudPolicySnapshot): ChatSendResult {
        require(message.senderUid == localUid)
        require(settingsRepository.chatOutbox.pending.value.contains(message))
        return deliverChat(snapshot.profile, message, settingsRepository.cloudPolicy, syncProvider, snapshot.authorization, settingsRepository.chatOutbox) { false }
    }
}

/** Outcome of a marker drop: the wire uid plus whether the radio actually sent. */
data class MarkerSendResult(val uid: String, val transmitted: Boolean, val meshOutcome: TransmissionOutcome = TransmissionOutcome.NOT_SUBMITTED)
