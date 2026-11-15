// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

object PortNum {
    const val PORTNUM_UNKNOWN_APP: Int = 0
    const val PORTNUM_TEXT_MESSAGE_APP: Int = 1
    const val PORTNUM_POSITION_APP: Int = 3
    const val PORTNUM_NODEINFO_APP: Int = 4
    const val PORTNUM_ROUTING_APP: Int = 5
    const val PORTNUM_ADMIN_APP: Int = 6
    const val PORTNUM_REPLY_APP: Int = 32
    const val PORTNUM_TELEMETRY_APP: Int = 67
    const val PORTNUM_SIMULATOR_APP: Int = 69
    const val PORTNUM_TRACEROUTE_APP: Int = 70
    const val PORTNUM_ATAK_PLUGIN: Int = 72
    const val PORTNUM_ATAK_PLUGIN_V2: Int = 78
    const val PORTNUM_PRIVATE_APP: Int = 256
    const val PORTNUM_ATAK_FORWARDER: Int = 257
}

enum class RadioConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    CONNECTED,
    STREAMING,
    ERROR,
}

enum class PacketDeliveryStage {
    QUEUED,
    RADIO_ACCEPTED,
    PEER_DELIVERED,
    FAILED,
}

data class PacketDeliveryUpdate(
    val packetId: Long,
    val stage: PacketDeliveryStage,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val timestampMs: Long = 0L,
)

data class RadioHardwareInfo(
    val radioModel: String?,
    val frequencyBand: String?,
    val firmwareVersion: String?,
    val nodeNum: Long,
    val isUsb: Boolean,
    val hardwareModelCode: Int? = null,
    val regionCode: Int? = null,
)

data class MeshNodeInfo(
    val nodeNum: Long,
    val user: String,
    val callsign: String,
    val snr: Float? = null,
    val rssi: Int? = null,
    val batteryLevel: Int? = null,
    val lastHeardTimestampMs: Long = 0L,
    val hopsAway: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Int? = null,
) {
    val shortName: String get() = callsign
    val longName: String get() = user
    val nodeIdHex: String get() = "!%08x".format(nodeNum and 0xFFFFFFFFL)
}

data class MeshPacket(
    val from: Long,
    val to: Long,
    val portNum: Int,
    val payload: ByteArray,
    val id: Long = 0L,
    val wantAck: Boolean = false,
    val hopLimit: Int = 3,
    val channel: Int = 0,
    val wantResponse: Boolean = false,
    val requestId: Long = 0L,
    val replyId: Long = 0L,
    val routingError: Int? = null,
    val rxSnr: Float? = null,
    val rxRssi: Int? = null,
    val hopStart: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshPacket) return false
        return from == other.from &&
            to == other.to &&
            portNum == other.portNum &&
            payload.contentEquals(other.payload) &&
            id == other.id &&
            wantAck == other.wantAck &&
            hopLimit == other.hopLimit &&
            channel == other.channel &&
            wantResponse == other.wantResponse &&
            requestId == other.requestId &&
            replyId == other.replyId &&
            routingError == other.routingError &&
            rxSnr == other.rxSnr &&
            rxRssi == other.rxRssi &&
            hopStart == other.hopStart
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + portNum
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + wantAck.hashCode()
        result = 31 * result + hopLimit
        result = 31 * result + channel
        result = 31 * result + wantResponse.hashCode()
        result = 31 * result + requestId.hashCode()
        result = 31 * result + replyId.hashCode()
        result = 31 * result + (routingError ?: 0)
        result = 31 * result + (rxSnr?.hashCode() ?: 0)
        result = 31 * result + (rxRssi ?: 0)
        result = 31 * result + (hopStart ?: 0)
        return result
    }
}
