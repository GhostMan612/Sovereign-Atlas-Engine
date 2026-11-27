// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket as ProtoMeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum as ProtoPortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Routing
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User

class MeshtasticProtocolSessionTest {

    @Test
    fun handshakeGatesTransmitReadinessAndDoesNotInventHardwareFields() {
        val session = MeshtasticProtocolSession()
        assertEquals(RadioConnectionState.DISCONNECTED, session.connectionState.value)
        assertFalse(session.isReady.value)

        session.beginConnecting()
        assertEquals(RadioConnectionState.CONNECTING, session.connectionState.value)

        val wantConfigBytes = session.beginHandshake(isUsb = true, configNonce = 777L)
        assertEquals(777, ToRadio.ADAPTER.decode(wantConfigBytes).want_config_id)
        assertEquals(RadioConnectionState.HANDSHAKING, session.connectionState.value)
        assertFalse(session.isReady.value)

        val blockedTx = MeshPacket(
            from = 0x1234L,
            to = 0xFFFFFFFFL,
            portNum = PortNum.PORTNUM_ATAK_PLUGIN,
            payload = byteArrayOf(1, 2, 3),
        )
        assertNull(session.prepareOutgoingPacket(blockedTx))

        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(FromRadio(id = 1, config_complete_id = 777))
            )
        )
        assertFalse(session.isReady.value)

        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(FromRadio(id = 2, my_info = MyNodeInfo(my_node_num = 0x10203040)))
            )
        )
        assertFalse(session.isReady.value)

        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(FromRadio(id = 3, config_complete_id = 999))
            )
        )
        assertFalse(session.isReady.value)

        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(
                    FromRadio(
                        id = 4,
                        node_info = NodeInfo(num = 0x10203040, user = User(short_name = "SELF", long_name = "Local Radio")),
                    )
                )
            )
        )
        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(
                    FromRadio(
                        id = 5,
                        node_info = NodeInfo(num = 0x20304050, user = User(short_name = "A1", long_name = "Alpha One")),
                    )
                )
            )
        )
        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(
                    FromRadio(
                        id = 6,
                        config = Config(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.UNSET)),
                    )
                )
            )
        )
        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(
                    FromRadio(
                        id = 7,
                        metadata = DeviceMetadata(firmware_version = "", hw_model = HardwareModel.UNSET),
                    )
                )
            )
        )
        assertTrue(
            session.onFromRadioBytes(
                FromRadio.ADAPTER.encode(FromRadio(id = 8, config_complete_id = 777))
            )
        )

        assertTrue(session.isReady.value)
        assertEquals(RadioConnectionState.CONNECTED, session.connectionState.value)

        val hw = session.activeRadioInfo.value!!
        assertEquals(0x10203040L, hw.nodeNum)
        assertTrue(hw.isUsb)
        assertNull(hw.radioModel)
        assertNull(hw.frequencyBand)
        assertNull(hw.firmwareVersion)

        val peers = session.discoveredNodes.value
        assertEquals(1, peers.size)
        assertEquals(0x20304050L, peers.single().nodeNum)
        assertNull(peers.single().snr)
        assertNull(peers.single().rssi)
        assertNull(peers.single().batteryLevel)
        assertNull(peers.single().hopsAway)
        assertNull(peers.single().latitude)
    }

    @Test
    fun deliveryLifecycleTracksQueuedRadioAcceptedPeerDeliveredAndFailedStages() {
        val session = MeshtasticProtocolSession()
        session.beginConnecting()
        session.beginHandshake(isUsb = false, configNonce = 55L)
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(FromRadio(id = 1, my_info = MyNodeInfo(my_node_num = 0x01020304)))
        )
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(FromRadio(id = 2, config_complete_id = 55))
        )
        assertTrue(session.isReady.value)

        val preparedAcked = session.prepareOutgoingPacket(
            MeshPacket(
                from = 0L,
                to = 0x55667788L,
                portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                payload = byteArrayOf(0x01, 0x02),
                id = 1001L,
                wantAck = true,
            )
        )!!
        assertEquals(PacketDeliveryStage.QUEUED, session.deliveryStates.value[1001L]?.stage)
        assertEquals(0x01020304L, preparedAcked.packet.from)

        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(
                FromRadio(id = 3, queueStatus = QueueStatus(res = 0, free = 14, maxlen = 16, mesh_packet_id = 1001))
            )
        )
        assertEquals(PacketDeliveryStage.RADIO_ACCEPTED, session.deliveryStates.value[1001L]?.stage)

        val ackPayload = Routing.ADAPTER.encode(Routing(error_reason = Routing.Error.NONE))
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(
                FromRadio(
                    id = 4,
                    packet = ProtoMeshPacket(
                        from = 0x55667788,
                        to = 0x01020304,
                        id = 2002,
                        decoded = Data(
                            portnum = ProtoPortNum.ROUTING_APP,
                            payload = ByteString.of(*ackPayload),
                            request_id = 1001,
                        ),
                    ),
                )
            )
        )
        assertEquals(PacketDeliveryStage.PEER_DELIVERED, session.deliveryStates.value[1001L]?.stage)

        session.prepareOutgoingPacket(
            MeshPacket(
                from = 0L,
                to = 0x55667788L,
                portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP,
                payload = "FAIL".encodeToByteArray(),
                id = 1002L,
                wantAck = true,
            )
        )!!
        val nakPayload = Routing.ADAPTER.encode(Routing(error_reason = Routing.Error.NO_ROUTE))
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(
                FromRadio(
                    id = 5,
                    packet = ProtoMeshPacket(
                        from = 0x55667788,
                        to = 0x01020304,
                        id = 2003,
                        decoded = Data(
                            portnum = ProtoPortNum.ROUTING_APP,
                            payload = ByteString.of(*nakPayload),
                            request_id = 1002,
                        ),
                    ),
                )
            )
        )
        val failed = session.deliveryStates.value[1002L]!!
        assertEquals(PacketDeliveryStage.FAILED, failed.stage)
        assertEquals(Routing.Error.NO_ROUTE.value, failed.errorCode)

        session.prepareOutgoingPacket(
            MeshPacket(
                from = 0L,
                to = 0xFFFFFFFFL,
                portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                payload = byteArrayOf(9),
                id = 1003L,
            )
        )!!
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(
                FromRadio(id = 6, queueStatus = QueueStatus(res = 7, free = 0, maxlen = 16, mesh_packet_id = 1003))
            )
        )
        assertEquals(PacketDeliveryStage.FAILED, session.deliveryStates.value[1003L]?.stage)
        assertEquals(7, session.deliveryStates.value[1003L]?.errorCode)
    }

    @Test
    fun routingAcknowledgementRequiresOutstandingUnicastAndExpectedPeer() {
        var now = 1_000L
        val session = MeshtasticProtocolSession(clockMs = { now }, acknowledgementTimeoutMs = 100L)
        session.beginConnecting()
        session.beginHandshake(isUsb = false, configNonce = 88L)
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(id = 1, my_info = MyNodeInfo(my_node_num = 1))))
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(id = 2, config_complete_id = 88)))
        fun send(id: Long, to: Long = 2L, wantAck: Boolean = true) = session.prepareOutgoingPacket(
            MeshPacket(from = 0, to = to, portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP, payload = byteArrayOf(1), id = id, wantAck = wantAck),
        )
        fun routing(id: Int, from: Int = 2, to: Int = 1, channel: Int = 0, payload: ByteArray = Routing.ADAPTER.encode(Routing(error_reason = Routing.Error.NONE))) {
            session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(packet = ProtoMeshPacket(
                from = from, to = to, channel = channel,
                decoded = Data(portnum = ProtoPortNum.ROUTING_APP, request_id = id, payload = ByteString.of(*payload)),
            ))))
        }
        routing(50)
        assertNull(session.deliveryStates.value[50L])
        assertNotNull(send(51))
        routing(51, from = 3)
        routing(51, to = 4)
        routing(51, channel = 1)
        routing(51, payload = byteArrayOf())
        routing(51, payload = Routing.ADAPTER.encode(Routing(route_request = org.meshtastic.proto.RouteDiscovery())))
        routing(51, payload = Routing.ADAPTER.encode(Routing(route_reply = org.meshtastic.proto.RouteDiscovery())))
        assertEquals(PacketDeliveryStage.QUEUED, session.deliveryStates.value[51L]?.stage)
        session.markTransportWriteUnconfirmed(51, "write completion missing")
        assertEquals(PacketDeliveryStage.UNCONFIRMED, session.deliveryStates.value[51L]?.stage)
        routing(51)
        assertEquals(PacketDeliveryStage.PEER_DELIVERED, session.deliveryStates.value[51L]?.stage)
        routing(51, payload = Routing.ADAPTER.encode(Routing(error_reason = Routing.Error.NO_ROUTE)))
        assertEquals(PacketDeliveryStage.PEER_DELIVERED, session.deliveryStates.value[51L]?.stage)
        assertNull(send(51))
        assertNotNull(send(52, to = 0xFFFFFFFFL))
        routing(52)
        assertEquals(PacketDeliveryStage.QUEUED, session.deliveryStates.value[52L]?.stage)
        assertNotNull(send(53, wantAck = false))
        routing(53)
        assertEquals(PacketDeliveryStage.QUEUED, session.deliveryStates.value[53L]?.stage)
        assertNotNull(send(54))
        now += 100
        routing(54)
        assertEquals(PacketDeliveryStage.UNCONFIRMED, session.deliveryStates.value[54L]?.stage)
        assertEquals("Delivery acknowledgment timed out", session.deliveryStates.value[54L]?.errorMessage)
        assertNull(send(54))
    }

    @Test
    fun staleQueueAndRoutingRepliesCannotOverwriteTerminalFailure() {
        val session = MeshtasticProtocolSession()
        session.beginConnecting()
        session.beginHandshake(isUsb = false, configNonce = 44L)
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(my_info = MyNodeInfo(my_node_num = 1))))
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(config_complete_id = 44)))
        val packet = MeshPacket(from = 0, to = 2, portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP, payload = byteArrayOf(1), id = 71, wantAck = true)
        assertNotNull(session.prepareOutgoingPacket(packet))
        session.markTransportWriteFailed(71, "failed")
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(queueStatus = QueueStatus(mesh_packet_id = 71, res = 0))))
        val payload = Routing.ADAPTER.encode(Routing(error_reason = Routing.Error.NONE))
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(packet = ProtoMeshPacket(from = 2, to = 1,
            decoded = Data(portnum = ProtoPortNum.ROUTING_APP, request_id = 71, payload = ByteString.of(*payload))))))
        assertEquals(PacketDeliveryStage.FAILED, session.deliveryStates.value[71L]?.stage)
    }

    @Test
    fun livePacketsEnrichDiscoveredNodesAndMalformedOrRebootResetsCleanly() {
        var fakeClock = 5_000L
        val session = MeshtasticProtocolSession(clockMs = { fakeClock })
        session.beginConnecting()
        session.beginHandshake(isUsb = false, configNonce = 88L)
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(id = 1, my_info = MyNodeInfo(my_node_num = 1))))
        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(id = 2, config_complete_id = 88)))

        val userPayload = User.ADAPTER.encode(User(id = "!00000009", long_name = "Relay Nine", short_name = "R9"))
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(
                FromRadio(
                    id = 3,
                    packet = ProtoMeshPacket(
                        from = 9,
                        to = 0xFFFFFFFFL.toInt(),
                        id = 101,
                        rx_snr = 4.5f,
                        rx_rssi = -65,
                        hop_start = 3,
                        hop_limit = 1,
                        decoded = Data(portnum = ProtoPortNum.NODEINFO_APP, payload = ByteString.of(*userPayload)),
                    ),
                )
            )
        )

        val posPayload = Position.ADAPTER.encode(Position(latitude_i = 401234567, longitude_i = -1052345678, altitude = 1650))
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(
                FromRadio(
                    id = 4,
                    packet = ProtoMeshPacket(
                        from = 9,
                        to = 0xFFFFFFFFL.toInt(),
                        id = 102,
                        decoded = Data(portnum = ProtoPortNum.POSITION_APP, payload = ByteString.of(*posPayload)),
                    ),
                )
            )
        )

        val telPayload = Telemetry.ADAPTER.encode(Telemetry(device_metrics = DeviceMetrics(battery_level = 71)))
        session.onFromRadioBytes(
            FromRadio.ADAPTER.encode(
                FromRadio(
                    id = 5,
                    packet = ProtoMeshPacket(
                        from = 9,
                        to = 0xFFFFFFFFL.toInt(),
                        id = 103,
                        decoded = Data(portnum = ProtoPortNum.TELEMETRY_APP, payload = ByteString.of(*telPayload)),
                    ),
                )
            )
        )

        val node = session.discoveredNodes.value.single()
        assertEquals(9L, node.nodeNum)
        assertEquals("R9", node.callsign)
        assertEquals("Relay Nine", node.user)
        assertEquals(4.5f, node.snr!!, 0.001f)
        assertEquals(-65, node.rssi)
        assertEquals(2, node.hopsAway)
        assertEquals(40.1234567, node.latitude!!, 1e-6)
        assertEquals(-105.2345678, node.longitude!!, 1e-6)
        assertEquals(71, node.batteryLevel)

        assertFalse(session.onFromRadioBytes(byteArrayOf(0x00, 0x7F, 0xFF.toByte())))
        assertEquals(1L, session.malformedFrameCount.value)
        assertEquals(1, session.discoveredNodes.value.size)

        session.onFromRadioBytes(FromRadio.ADAPTER.encode(FromRadio(id = 6, rebooted = true)))
        assertFalse(session.isReady.value)
        assertEquals(RadioConnectionState.HANDSHAKING, session.connectionState.value)
        assertNull(session.activeRadioInfo.value)
    }
}
