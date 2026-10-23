// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

object PortNum {
    const val PORTNUM_TEXT_MESSAGE_APP: Int = 1
    const val PORTNUM_POSITION_APP: Int = 3
    const val PORTNUM_NODEINFO_APP: Int = 4
    const val PORTNUM_ROUTING_APP: Int = 32
    const val PORTNUM_ATAK_PLUGIN: Int = 67
    const val PORTNUM_TELEMETRY_APP: Int = 69
    const val PORTNUM_PRIVATE_APP: Int = 256
}

enum class RadioConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STREAMING,
    ERROR,
}

data class RadioHardwareInfo(
    val radioModel: String,
    val frequencyBand: String,
    val firmwareVersion: String,
    val nodeNum: Long,
    val isUsb: Boolean,
)

data class MeshNodeInfo(
    val nodeNum: Long,
    val user: String,
    val callsign: String,
    val snr: Float,
    val rssi: Int,
    val batteryLevel: Int,
    val lastHeardMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
) {
    val nodeIdHex: String get() = "!%08x".format(nodeNum and 0xFFFFFFFFL)
}

data class MeshPacket(
    val from: Long,
    val to: Long = 0xFFFFFFFFL,
    val portNum: Int,
    val payload: ByteArray,
    val id: Long = 0L,
    val rxTime: Long = 0L,
    val rxSnr: Float = 0f,
    val rxRssi: Int = 0,
    val hopLimit: Int = 3,
    val wantAck: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshPacket) return false
        return from == other.from &&
            to == other.to &&
            portNum == other.portNum &&
            id == other.id &&
            rxTime == other.rxTime &&
            hopLimit == other.hopLimit &&
            wantAck == other.wantAck &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + portNum
        result = 31 * result + id.hashCode()
        result = 31 * result + rxTime.hashCode()
        result = 31 * result + hopLimit
        result = 31 * result + wantAck.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
