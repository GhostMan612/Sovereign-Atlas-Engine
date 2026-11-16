// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshtasticCodecBoundaryTest {
    private class FakeCodec : MeshtasticCodec {
        var nonce: Long? = null
        var next: DecodedFromRadio? = null
        var encodedPacket: MeshPacket? = null
        var maximum: Int? = null
        var rejectPacket = false
        var userPayload: ByteArray? = null

        override fun encodeWantConfig(configId: Long): ByteArray {
            nonce = configId
            return byteArrayOf(11)
        }

        override fun encodeDisconnect(): ByteArray = byteArrayOf(12)

        override fun encodePacket(packet: MeshPacket, maxEnvelopeBytes: Int): ByteArray {
            if (rejectPacket) throw IllegalArgumentException("rejected")
            encodedPacket = packet
            maximum = maxEnvelopeBytes
            return byteArrayOf(13)
        }

        override fun decodeFromRadio(bytes: ByteArray): DecodedFromRadio? = next

        override fun decodeUserPayload(payload: ByteArray): MeshtasticDecodedUser {
            userPayload = payload
            return object : MeshtasticDecodedUser {
                override val shortName = "SYN"
                override val longName = "Synthetic"
            }
        }

        override fun decodePositionPayload(payload: ByteArray): MeshtasticDecodedPosition? = null
        override fun decodeTelemetryPayload(payload: ByteArray): MeshtasticDecodedTelemetry? = null
    }

    @Test
    fun injectedCodecDrivesHandshakeOutgoingAndDisconnect() {
        val codec = FakeCodec()
        val session = MeshtasticProtocolSession(wireAdapter = codec, clockMs = { 50L })
        session.beginConnecting()
        assertArrayEquals(byteArrayOf(11), session.beginHandshake(isUsb = false, configNonce = 9L))
        assertEquals(9L, codec.nonce ?: -1L)
        codec.next = DecodedFromRadio.MyInfo(42L, 0L, 0, null)
        assertTrue(session.onFromRadioBytes(byteArrayOf(1)))
        assertFalse(session.isReady.value)
        codec.next = DecodedFromRadio.ConfigComplete(9L)
        assertTrue(session.onFromRadioBytes(byteArrayOf(2)))
        assertTrue(session.isReady.value)

        val packet = MeshPacket(from = 0L, to = 43L, portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP, payload = byteArrayOf(4), id = 5L)
        val prepared = session.prepareOutgoingPacket(packet, 100)
        assertNotNull(prepared)
        assertArrayEquals(byteArrayOf(13), prepared!!.toRadioBytes)
        assertEquals(42L, codec.encodedPacket!!.from)
        assertEquals(100, codec.maximum ?: -1)
        assertEquals(PacketDeliveryStage.QUEUED, session.deliveryStates.value[5L]!!.stage)
        assertArrayEquals(byteArrayOf(12), session.encodeDisconnectCommand())
    }

    @Test
    fun injectedCodecErrorsAndMalformedFramesKeepSessionFailClosed() {
        val codec = FakeCodec()
        val session = MeshtasticProtocolSession(codec)
        assertFalse(session.onFromRadioBytes(byteArrayOf(1)))
        assertEquals(1L, session.malformedFrameCount.value)
        session.beginHandshake(isUsb = true, configNonce = 9L)
        codec.next = DecodedFromRadio.MyInfo(42L, 0L, 0, null)
        session.onFromRadioBytes(byteArrayOf(1))
        codec.next = DecodedFromRadio.ConfigComplete(9L)
        session.onFromRadioBytes(byteArrayOf(1))
        codec.rejectPacket = true
        val packet = MeshPacket(from = 0L, to = 43L, portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP, payload = byteArrayOf(4), id = 7L)
        assertNull(session.prepareOutgoingPacket(packet))
        assertEquals(PacketDeliveryStage.FAILED, session.deliveryStates.value[7L]!!.stage)
    }

    @Test
    fun injectedCodecDecodesPayloadForNodeEnrichment() {
        val codec = FakeCodec()
        val session = MeshtasticProtocolSession(codec, clockMs = { 50L })
        val payload = byteArrayOf(9, 8)
        codec.next = DecodedFromRadio.InboundPacket(
            MeshPacket(from = 43L, to = 42L, portNum = PortNum.PORTNUM_NODEINFO_APP, payload = payload)
        )
        assertTrue(session.onFromRadioBytes(byteArrayOf(1)))
        assertArrayEquals(payload, codec.userPayload)
        assertEquals("Synthetic", session.discoveredNodes.value.single().user)
        assertEquals("SYN", session.discoveredNodes.value.single().callsign)
    }
}
