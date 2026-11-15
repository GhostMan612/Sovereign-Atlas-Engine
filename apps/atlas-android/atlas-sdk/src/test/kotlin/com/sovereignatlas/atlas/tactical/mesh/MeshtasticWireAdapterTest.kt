// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.tactical.mesh

import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User

class MeshtasticWireAdapterTest {

    private val adapter = MeshtasticWireAdapter()

    @Test
    fun portNumConstantsMatchPinnedMeshtasticV282Proto() {
        assertEquals(0, PortNum.PORTNUM_UNKNOWN_APP)
        assertEquals(ProtoPortNum.TEXT_MESSAGE_APP.value, PortNum.PORTNUM_TEXT_MESSAGE_APP)
        assertEquals(1, PortNum.PORTNUM_TEXT_MESSAGE_APP)
        assertEquals(ProtoPortNum.POSITION_APP.value, PortNum.PORTNUM_POSITION_APP)
        assertEquals(3, PortNum.PORTNUM_POSITION_APP)
        assertEquals(ProtoPortNum.NODEINFO_APP.value, PortNum.PORTNUM_NODEINFO_APP)
        assertEquals(4, PortNum.PORTNUM_NODEINFO_APP)
        assertEquals(ProtoPortNum.ROUTING_APP.value, PortNum.PORTNUM_ROUTING_APP)
        assertEquals(5, PortNum.PORTNUM_ROUTING_APP)
        assertEquals(ProtoPortNum.REPLY_APP.value, PortNum.PORTNUM_REPLY_APP)
        assertEquals(32, PortNum.PORTNUM_REPLY_APP)
        assertEquals(ProtoPortNum.TELEMETRY_APP.value, PortNum.PORTNUM_TELEMETRY_APP)
        assertEquals(67, PortNum.PORTNUM_TELEMETRY_APP)
        assertEquals(ProtoPortNum.SIMULATOR_APP.value, PortNum.PORTNUM_SIMULATOR_APP)
        assertEquals(69, PortNum.PORTNUM_SIMULATOR_APP)
        assertEquals(ProtoPortNum.TRACEROUTE_APP.value, PortNum.PORTNUM_TRACEROUTE_APP)
        assertEquals(70, PortNum.PORTNUM_TRACEROUTE_APP)
        assertEquals(ProtoPortNum.ATAK_PLUGIN.value, PortNum.PORTNUM_ATAK_PLUGIN)
        assertEquals(72, PortNum.PORTNUM_ATAK_PLUGIN)
        assertEquals(ProtoPortNum.ATAK_PLUGIN_V2.value, PortNum.PORTNUM_ATAK_PLUGIN_V2)
        assertEquals(78, PortNum.PORTNUM_ATAK_PLUGIN_V2)
        assertEquals(ProtoPortNum.PRIVATE_APP.value, PortNum.PORTNUM_PRIVATE_APP)
        assertEquals(256, PortNum.PORTNUM_PRIVATE_APP)
        assertEquals(ProtoPortNum.ATAK_FORWARDER.value, PortNum.PORTNUM_ATAK_FORWARDER)
        assertEquals(257, PortNum.PORTNUM_ATAK_FORWARDER)
    }

    @Test
    fun encodeWantConfigMatchesOfficialWireBytesAndRejectsZero() {
        val wireBytes = adapter.encodeWantConfig(42L)
        assertArrayEquals(byteArrayOf(0x18, 0x2A), wireBytes)

        val decoded = ToRadio.ADAPTER.decode(wireBytes)
        assertEquals(42, decoded.want_config_id)

        val thrown = runCatching { adapter.encodeWantConfig(0L) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun encodePacketProducesGenuineToRadioProtobufPreservingUint32AndFlags() {
        val payload = byteArrayOf(0x0A, 0x05, 0x41, 0x54, 0x4C, 0x41, 0x53)
        val packet = MeshPacket(
            from = 0xAABBCCDDL,
            to = 0xFFFFFFFFL,
            portNum = PortNum.PORTNUM_ATAK_PLUGIN,
            payload = payload,
            id = 0x89ABCDEFL,
            wantAck = true,
            hopLimit = 5,
            channel = 2,
            wantResponse = true,
            requestId = 0x11223344L,
            replyId = 0x55667788L,
        )

        val encoded = adapter.encodePacket(packet)
        val toRadio = ToRadio.ADAPTER.decode(encoded)
        val wirePacket = toRadio.packet!!
        assertEquals(0xAABBCCDDL, wirePacket.from.toLong() and 0xFFFFFFFFL)
        assertEquals(0xFFFFFFFFL, wirePacket.to.toLong() and 0xFFFFFFFFL)
        assertEquals(2, wirePacket.channel)
        assertEquals(0x89ABCDEFL, wirePacket.id.toLong() and 0xFFFFFFFFL)
        assertTrue(wirePacket.want_ack)
        assertEquals(5, wirePacket.hop_limit)
        assertEquals(ProtoMeshPacket.Priority.RELIABLE, wirePacket.priority)

        val decodedData = wirePacket.decoded!!
        assertEquals(ProtoPortNum.ATAK_PLUGIN, decodedData.portnum)
        assertArrayEquals(payload, decodedData.payload.toByteArray())
        assertTrue(decodedData.want_response)
        assertEquals(0x11223344L, decodedData.request_id.toLong() and 0xFFFFFFFFL)
        assertEquals(0x55667788L, decodedData.reply_id.toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun decodeRawOfficialWireVectorsHandlesMyInfoMetadataConfigNodeInfoAndAck() {
        val rawMyInfoVector = hexToBytes("08011A0708DDCC8B194064")
        val decodedMyInfo = adapter.decodeFromRadio(rawMyInfoVector) as DecodedFromRadio.MyInfo
        assertEquals(0x0322E65DL, decodedMyInfo.myNodeNum)
        assertEquals(100L, decodedMyInfo.rebootCount)

        val metadataBytes = FromRadio.ADAPTER.encode(
            FromRadio(
                id = 2,
                metadata = DeviceMetadata(
                    firmware_version = "2.6.0.abc123",
                    hw_model = HardwareModel.TBEAM,
                ),
            )
        )
        val decodedMeta = adapter.decodeFromRadio(metadataBytes) as DecodedFromRadio.Metadata
        assertEquals("2.6.0.abc123", decodedMeta.firmwareVersion)
        assertEquals(HardwareModel.TBEAM.value, decodedMeta.hardwareModelCode)
        assertEquals("TBEAM", decodedMeta.hardwareModelName)

        val unsetLora = FromRadio.ADAPTER.encode(
            FromRadio(id = 3, config = Config(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.UNSET)))
        )
        val decodedUnset = adapter.decodeFromRadio(unsetLora) as DecodedFromRadio.LoraRegion
        assertEquals(0, decodedUnset.regionCode)
        assertNull(decodedUnset.frequencyBand)

        val euLora = FromRadio.ADAPTER.encode(
            FromRadio(id = 4, config = Config(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.EU_868)))
        )
        val decodedEu = adapter.decodeFromRadio(euLora) as DecodedFromRadio.LoraRegion
        assertEquals(Config.LoRaConfig.RegionCode.EU_868.value, decodedEu.regionCode)
        assertEquals("868 MHz EU", decodedEu.frequencyBand)

        val nodeBytes = FromRadio.ADAPTER.encode(
            FromRadio(
                id = 5,
                node_info = NodeInfo(
                    num = 0xDEADBEEFL.toInt(),
                    user = User(id = "!deadbeef", long_name = "Bravo Two", short_name = "B2"),
                    position = Position(latitude_i = -338688200, longitude_i = 1512092900, altitude = 42),
                    snr = -3.5f,
                    last_heard = 1700000000,
                    device_metrics = DeviceMetrics(battery_level = 84),
                    hops_away = 2,
                ),
            )
        )
        val decodedNode = adapter.decodeFromRadio(nodeBytes) as DecodedFromRadio.DiscoveredNode
        assertEquals(0xDEADBEEFL, decodedNode.node.nodeNum)
        assertEquals("B2", decodedNode.node.callsign)
        assertEquals("Bravo Two", decodedNode.node.user)
        assertEquals("!deadbeef", decodedNode.node.nodeIdHex)
        assertEquals(-33.86882, decodedNode.node.latitude!!, 1e-6)
        assertEquals(151.20929, decodedNode.node.longitude!!, 1e-6)
        assertEquals(42, decodedNode.node.altitudeMeters)
        assertEquals(-3.5f, decodedNode.node.snr!!, 0.001f)
        assertNull(decodedNode.node.rssi)
        assertEquals(84, decodedNode.node.batteryLevel)
        assertEquals(2, decodedNode.node.hopsAway)

        val queueBytes = FromRadio.ADAPTER.encode(
            FromRadio(id = 6, queueStatus = QueueStatus(res = 0, free = 15, maxlen = 16, mesh_packet_id = 0x12345678))
        )
        val decodedQueue = adapter.decodeFromRadio(queueBytes) as DecodedFromRadio.QueueAck
        assertEquals(0x12345678L, decodedQueue.meshPacketId)
        assertEquals(0, decodedQueue.result)

        val ackPayload = Routing.ADAPTER.encode(Routing(error_reason = Routing.Error.NONE))
        val routingAckBytes = FromRadio.ADAPTER.encode(
            FromRadio(
                id = 7,
                packet = ProtoMeshPacket(
                    from = 0xDEADBEEFL.toInt(),
                    to = 0x0322E65D,
                    id = 999,
                    rx_snr = 6.25f,
                    rx_rssi = -78,
                    hop_start = 3,
                    hop_limit = 2,
                    decoded = Data(
                        portnum = ProtoPortNum.ROUTING_APP,
                        payload = ByteString.of(*ackPayload),
                        request_id = 0x12345678,
                    ),
                ),
            )
        )
        val decodedAck = adapter.decodeFromRadio(routingAckBytes) as DecodedFromRadio.InboundPacket
        assertEquals(PortNum.PORTNUM_ROUTING_APP, decodedAck.packet.portNum)
        assertEquals(0x12345678L, decodedAck.packet.requestId)
        assertEquals(0, decodedAck.packet.routingError)
        assertEquals(-78, decodedAck.packet.rxRssi)
        assertEquals(6.25f, decodedAck.packet.rxSnr!!, 0.001f)
        assertEquals(3, decodedAck.packet.hopStart)
    }

    @Test
    fun malformedOversizedOrUnsupportedPacketsFailClosed() {
        assertNull(adapter.decodeFromRadio(ByteArray(0)))
        assertNull(adapter.decodeFromRadio(ByteArray(513)))
        assertNull(adapter.decodeFromRadio(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())))

        val encryptedOnly = FromRadio.ADAPTER.encode(
            FromRadio(
                id = 10,
                packet = ProtoMeshPacket(
                    from = 10,
                    to = 20,
                    encrypted = ByteString.of(1, 2, 3, 4),
                ),
            )
        )
        assertEquals(DecodedFromRadio.Ignored, adapter.decodeFromRadio(encryptedOnly))

        val oversized = MeshPacket(
            from = 1L,
            to = 2L,
            portNum = PortNum.PORTNUM_ATAK_PLUGIN,
            payload = ByteArray(500),
            id = 55L,
        )
        val sizeFailure = runCatching { adapter.encodePacket(oversized, maxEnvelopeBytes = 64) }.exceptionOrNull()
        assertTrue(sizeFailure is IllegalArgumentException)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) { idx ->
            clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
    }
}
