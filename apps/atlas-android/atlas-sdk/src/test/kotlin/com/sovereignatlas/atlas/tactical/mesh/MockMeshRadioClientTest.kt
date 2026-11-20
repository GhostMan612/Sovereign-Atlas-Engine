// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockMeshRadioClientTest {
    @Test
    fun routingRequiresExplicitCorrelatedOutstandingUnicastAcknowledgment() = runBlocking {
        var now = 1_000L
        val radio = MockMeshRadioClient(RadioConnectionState.CONNECTED, clockMs = { now }, acknowledgementTimeoutMs = 100L)
        val local = radio.activeRadioInfo.value!!.nodeNum
        fun ack(id: Long, from: Long = 2, to: Long = local, channel: Int = 0, error: Int? = 0) =
            MeshPacket(from = from, to = to, channel = channel, portNum = PortNum.PORTNUM_ROUTING_APP, payload = byteArrayOf(), requestId = id, routingError = error)
        fun packet(id: Long, to: Long = 2, wantAck: Boolean = true) =
            MeshPacket(from = 0, to = to, portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP, payload = byteArrayOf(1), id = id, wantAck = wantAck)

        radio.simulateInboundPacket(ack(10))
        assertNull(radio.deliveryStates.value[10])
        assertTrue(radio.transmitPacket(packet(10)))
        radio.simulateInboundPacket(ack(10, from = 3))
        radio.simulateInboundPacket(ack(10, to = 3))
        radio.simulateInboundPacket(ack(10, channel = 1))
        radio.simulateInboundPacket(ack(10, error = null))
        assertEquals(PacketDeliveryStage.RADIO_ACCEPTED, radio.deliveryStates.value[10]?.stage)
        radio.simulateInboundPacket(ack(10))
        assertEquals(PacketDeliveryStage.PEER_DELIVERED, radio.deliveryStates.value[10]?.stage)
        radio.simulateInboundPacket(ack(10, error = 1))
        assertEquals(PacketDeliveryStage.PEER_DELIVERED, radio.deliveryStates.value[10]?.stage)
        assertFalse(radio.transmitPacket(packet(10)))

        assertTrue(radio.transmitPacket(packet(11, to = 0xFFFFFFFFL)))
        assertTrue(radio.transmitPacket(packet(12, wantAck = false)))
        radio.simulateInboundPacket(ack(11))
        radio.simulateInboundPacket(ack(12))
        assertEquals(PacketDeliveryStage.RADIO_ACCEPTED, radio.deliveryStates.value[11]?.stage)
        assertEquals(PacketDeliveryStage.RADIO_ACCEPTED, radio.deliveryStates.value[12]?.stage)
        assertTrue(radio.transmitPacket(packet(13)))
        now += 100
        radio.simulateInboundPacket(ack(13))
        assertEquals(PacketDeliveryStage.UNCONFIRMED, radio.deliveryStates.value[13]?.stage)
        assertTrue(radio.transmitPacket(packet(14)))
        radio.simulateInboundPacket(ack(14, error = 1))
        assertEquals(PacketDeliveryStage.FAILED, radio.deliveryStates.value[14]?.stage)
    }
}
