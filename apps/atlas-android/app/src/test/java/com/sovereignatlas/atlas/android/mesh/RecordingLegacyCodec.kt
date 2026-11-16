// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.MeshtasticCodec
import com.sovereignatlas.atlas.tactical.mesh.MeshtasticWireAdapter
import java.util.concurrent.atomic.AtomicInteger

internal class RecordingLegacyCodec : MeshtasticCodec {
    private val adapter = MeshtasticWireAdapter()
    val handshakeCalls = AtomicInteger()
    val decodeCalls = AtomicInteger()

    override fun encodeWantConfig(configId: Long): ByteArray {
        handshakeCalls.incrementAndGet()
        return adapter.encodeWantConfig(configId)
    }

    override fun encodeDisconnect() = adapter.encodeDisconnect()
    override fun encodePacket(packet: MeshPacket, maxEnvelopeBytes: Int) = adapter.encodePacket(packet, maxEnvelopeBytes)
    override fun decodeFromRadio(bytes: ByteArray) = adapter.decodeFromRadio(bytes).also { decodeCalls.incrementAndGet() }
    override fun decodeUserPayload(payload: ByteArray) = adapter.decodeUserPayload(payload)
    override fun decodePositionPayload(payload: ByteArray) = adapter.decodePositionPayload(payload)
    override fun decodeTelemetryPayload(payload: ByteArray) = adapter.decodeTelemetryPayload(payload)
}
