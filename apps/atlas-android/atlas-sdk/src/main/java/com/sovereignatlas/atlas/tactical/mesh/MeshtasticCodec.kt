// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

interface MeshtasticCodec {
    fun encodeWantConfig(configId: Long): ByteArray
    fun encodeDisconnect(): ByteArray
    fun encodePacket(packet: MeshPacket, maxEnvelopeBytes: Int = MAX_TO_FROM_RADIO_SIZE): ByteArray
    fun decodeFromRadio(bytes: ByteArray): DecodedFromRadio?
    fun decodeUserPayload(payload: ByteArray): MeshtasticDecodedUser?
    fun decodePositionPayload(payload: ByteArray): MeshtasticDecodedPosition?
    fun decodeTelemetryPayload(payload: ByteArray): MeshtasticDecodedTelemetry?

    companion object {
        const val MAX_TO_FROM_RADIO_SIZE: Int = 512
    }
}

interface MeshtasticDecodedUser {
    val shortName: String?
    val longName: String?
}

interface MeshtasticDecodedPosition {
    val latitude: Double
    val longitude: Double
    val altitudeMeters: Int?
}

interface MeshtasticDecodedTelemetry {
    val batteryLevel: Int
}

sealed class DecodedFromRadio {
    data class MyInfo(
        val myNodeNum: Long,
        val rebootCount: Long,
        val nodedbCount: Int,
        val pioEnv: String?,
    ) : DecodedFromRadio()

    data class Metadata(
        val firmwareVersion: String?,
        val hardwareModelCode: Int?,
        val hardwareModelName: String?,
    ) : DecodedFromRadio()

    data class LoraRegion(
        val regionCode: Int,
        val frequencyBand: String?,
    ) : DecodedFromRadio()

    data class DiscoveredNode(val node: MeshNodeInfo) : DecodedFromRadio()
    data class InboundPacket(val packet: MeshPacket) : DecodedFromRadio()
    data class QueueAck(val meshPacketId: Long, val result: Int, val free: Int, val maxlen: Int) : DecodedFromRadio()
    data class ConfigComplete(val configCompleteId: Long) : DecodedFromRadio()
    data object Rebooted : DecodedFromRadio()
    data object Ignored : DecodedFromRadio()
}
