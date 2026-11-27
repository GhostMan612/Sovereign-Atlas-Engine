// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

internal class MeshtasticLegacyCodec(private val adapter: MeshtasticWireAdapter) : MeshtasticCodec {
    override fun encodeWantConfig(configId: Long): ByteArray = adapter.encodeWantConfig(configId)
    override fun encodeDisconnect(): ByteArray = adapter.encodeDisconnect()
    override fun encodePacket(packet: MeshPacket, maxEnvelopeBytes: Int): ByteArray = adapter.encodePacket(packet, maxEnvelopeBytes)
    override fun decodeFromRadio(bytes: ByteArray): DecodedFromRadio? = adapter.decodeFromRadio(bytes)
    override fun decodeUserPayload(payload: ByteArray): MeshtasticDecodedUser? = adapter.decodeUserPayload(payload)
    override fun decodePositionPayload(payload: ByteArray): MeshtasticDecodedPosition? = adapter.decodePositionPayload(payload)
    override fun decodeTelemetryPayload(payload: ByteArray): MeshtasticDecodedTelemetry? = adapter.decodeTelemetryPayload(payload)
}
