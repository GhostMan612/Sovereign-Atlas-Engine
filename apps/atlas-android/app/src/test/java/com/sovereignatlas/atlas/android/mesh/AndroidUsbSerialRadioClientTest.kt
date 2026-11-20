// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.MeshtasticSerialFramer
import com.sovereignatlas.atlas.tactical.mesh.PacketDeliveryStage
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.TransmissionOutcome
import com.sovereignatlas.atlas.tactical.mesh.TransmissionPermit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.MeshPacket as ProtoMeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.PortNum as ProtoPortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio

class AndroidUsbSerialRadioClientTest {

    @Test
    fun injectedCodecHandlesUsbHandshakeAndIncomingMessages() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val codec = RecordingLegacyCodec()
            val transport = FakeUsbSerialTransport(onWantConfigWritten = { fake, nonce ->
                val info = MeshtasticSerialFramer.frame(FromRadio.ADAPTER.encode(FromRadio(my_info = MyNodeInfo(my_node_num = 10))))
                val complete = MeshtasticSerialFramer.frame(FromRadio.ADAPTER.encode(FromRadio(config_complete_id = nonce)))
                fake.reads.trySend(UsbReadResult.Data(info + complete))
            })
            val client = AndroidUsbSerialRadioClient(null, scope, { transport }, handshakeTimeoutMs = 3_000L, codec = codec)
            assertTrue(client.connect())
            assertEquals(1, codec.handshakeCalls.get())
            assertTrue(codec.decodeCalls.get() >= 2)
            client.disconnect()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun partialWriteFollowedByPolicyRevocationIsUnconfirmed() {
        var submissions = 0
        val permit = object : TransmissionPermit {
            override fun <T : Any> submit(action: () -> T): T? = if (submissions++ == 0) action() else null
        }
        val chunks = mutableListOf<ByteArray>()
        val result = writeSerialFrame(byteArrayOf(1, 2, 3, 4), permit) { bytes ->
            chunks.add(bytes.copyOf())
            2
        }
        assertEquals(TransmissionOutcome.UNCONFIRMED, result)
        assertEquals(1, chunks.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), chunks.single())
        val denied = object : TransmissionPermit {
            override fun <T : Any> submit(action: () -> T): T? = null
        }
        assertEquals(TransmissionOutcome.NOT_SUBMITTED, writeSerialFrame(byteArrayOf(1), denied) { error("must not write") })
        assertEquals(TransmissionOutcome.UNCONFIRMED, writeSerialFrame(byteArrayOf(1), null) { -1 })
    }

    @Test
    fun ftdiIsRefusedEvenWhenItAdvertisesCdc() {
        assertFalse(AndroidUsbSerialTransport.isSupportedUsbDevice(0x0403, false))
        assertFalse(AndroidUsbSerialTransport.isSupportedUsbDevice(0x0403, true))
        assertTrue(AndroidUsbSerialTransport.isSupportedUsbDevice(0x303A, false))
        assertTrue(AndroidUsbSerialTransport.isSupportedUsbDevice(0x7777, true))
        assertFalse(AndroidUsbSerialTransport.isSupportedUsbDevice(0x7777, false))
    }

    private class FakeUsbSerialTransport(
        private val openSucceeds: Boolean = true,
        private val onWantConfigWritten: (FakeUsbSerialTransport, Int) -> Unit = { _, _ -> },
    ) : UsbSerialTransport {
        val writes = mutableListOf<ByteArray>()
        val reads = Channel<UsbReadResult>(Channel.UNLIMITED)
        private val framer = MeshtasticSerialFramer()
        @Volatile
        var closed: Boolean = false

        override fun open(): Boolean = openSucceeds

        override suspend fun write(bytes: ByteArray, timeoutMs: Int): Boolean {
            writes += bytes
            for (frame in framer.feed(bytes)) {
                val decoded = runCatching { ToRadio.ADAPTER.decode(frame) }.getOrNull()
                decoded?.want_config_id?.let { nonce ->
                    onWantConfigWritten(this, nonce)
                }
            }
            return true
        }

        override suspend fun read(maxBytes: Int, timeoutMs: Int): UsbReadResult {
            return reads.receive()
        }

        override fun close() {
            closed = true
            reads.trySend(UsbReadResult.Closed("closed", isError = false))
        }
    }

    @Test
    fun fragmentedSerialReadsReassembleHandshakeAndRoundtripPacketsIdenticallyToBle() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val transport = FakeUsbSerialTransport(
                onWantConfigWritten = { fake, nonce ->
                    val f1 = MeshtasticSerialFramer.frame(
                        FromRadio.ADAPTER.encode(FromRadio(id = 1, my_info = MyNodeInfo(my_node_num = 0x44556677)))
                    )
                    val f2 = MeshtasticSerialFramer.frame(
                        FromRadio.ADAPTER.encode(
                            FromRadio(
                                id = 2,
                                metadata = DeviceMetadata(firmware_version = "2.8.2", hw_model = HardwareModel.HELTEC_V3),
                            )
                        )
                    )
                    val f3 = MeshtasticSerialFramer.frame(
                        FromRadio.ADAPTER.encode(
                            FromRadio(id = 3, config = Config(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.EU_868)))
                        )
                    )
                    val f4 = MeshtasticSerialFramer.frame(
                        FromRadio.ADAPTER.encode(FromRadio(id = 4, config_complete_id = nonce))
                    )
                    val combined = f1 + byteArrayOf(0xFF.toByte(), 0x00) + f2 + f3 + f4
                    val splitAt = f1.size / 2
                    fake.reads.trySend(UsbReadResult.Data(combined.copyOfRange(0, splitAt)))
                    fake.reads.trySend(UsbReadResult.Timeout)
                    fake.reads.trySend(UsbReadResult.Data(combined.copyOfRange(splitAt, combined.size)))
                },
            )
            val client = AndroidUsbSerialRadioClient(
                context = null,
                scope = scope,
                transportFactory = { transport },
                handshakeTimeoutMs = 3_000L,
            )

            assertTrue(client.connect())
            assertTrue(client.isReady.value)
            assertEquals(RadioConnectionState.CONNECTED, client.connectionState.value)

            val hw = client.activeRadioInfo.value!!
            assertTrue(hw.isUsb)
            assertEquals(0x44556677L, hw.nodeNum)
            assertEquals("868 MHz EU", hw.frequencyBand)
            assertEquals("2.8.2", hw.firmwareVersion)
            assertEquals("HELTEC V3", hw.radioModel)

            assertTrue(transport.writes.first().copyOfRange(0, 32).contentEquals(MeshtasticSerialFramer.WAKE_PREAMBLE))

            val inboundDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000L) { client.inboundPackets.first() }
            }

            val inboundPayload = byteArrayOf(0x0A, 0x02, 0x10, 0x01)
            val inboundBytes = MeshtasticSerialFramer.frame(
                FromRadio.ADAPTER.encode(
                    FromRadio(
                        id = 10,
                        packet = ProtoMeshPacket(
                            from = 0x77665544,
                            to = 0xFFFFFFFFL.toInt(),
                            id = 9001,
                            channel = 1,
                            want_ack = true,
                            decoded = Data(
                                portnum = ProtoPortNum.ATAK_PLUGIN,
                                payload = ByteString.of(*inboundPayload),
                                want_response = true,
                            ),
                        ),
                    )
                )
            )
            for (b in inboundBytes) {
                transport.reads.trySend(UsbReadResult.Data(byteArrayOf(b)))
            }

            val received = inboundDeferred.await()
            assertEquals(0x77665544L, received.from)
            assertEquals(0xFFFFFFFFL, received.to)
            assertEquals(PortNum.PORTNUM_ATAK_PLUGIN, received.portNum)
            assertEquals(9001L, received.id)
            assertEquals(1, received.channel)
            assertTrue(received.wantAck)
            assertTrue(received.wantResponse)
            assertArrayEquals(inboundPayload, received.payload)

            assertTrue(
                client.transmitPacket(
                    MeshPacket(
                        from = 0x44556677L,
                        to = 0x77665544L,
                        portNum = PortNum.PORTNUM_PRIVATE_APP,
                        payload = "USB-TX".encodeToByteArray(),
                        id = 333L,
                        wantAck = true,
                    )
                )
            )
            assertEquals(PacketDeliveryStage.QUEUED, client.deliveryStates.value[333L]?.stage)
            val framedTx = transport.writes.last()
            assertEquals(0x94.toByte(), framedTx[0])
            assertEquals(0xC3.toByte(), framedTx[1])
            val decodedTx = ToRadio.ADAPTER.decode(framedTx.copyOfRange(4, framedTx.size)).packet!!
            assertEquals(ProtoPortNum.PRIVATE_APP, decodedTx.decoded!!.portnum)

            transport.reads.trySend(
                UsbReadResult.Data(
                    MeshtasticSerialFramer.frame(
                        FromRadio.ADAPTER.encode(
                            FromRadio(id = 11, queueStatus = QueueStatus(res = 0, free = 10, maxlen = 16, mesh_packet_id = 333))
                        )
                    )
                )
            )
            withTimeout(2_000L) {
                client.deliveryStates.first { it[333L]?.stage == PacketDeliveryStage.RADIO_ACCEPTED }
            }

            transport.reads.trySend(UsbReadResult.Closed("USB detached", isError = true))
            withTimeout(2_000L) {
                client.connectionState.first { it == RadioConnectionState.ERROR }
            }
            assertFalse(client.isReady.value)
            assertNull(client.activeRadioInfo.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun handshakeTimeoutAndOpenFailureReturnFalseSafely() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val closedClient = AndroidUsbSerialRadioClient(
                context = null,
                scope = scope,
                transportFactory = { FakeUsbSerialTransport(openSucceeds = false) },
                handshakeTimeoutMs = 200L,
            )
            assertFalse(closedClient.connect())
            assertEquals(RadioConnectionState.ERROR, closedClient.connectionState.value)

            val stalledTransport = FakeUsbSerialTransport(openSucceeds = true)
            val stalledClient = AndroidUsbSerialRadioClient(
                context = null,
                scope = scope,
                transportFactory = { stalledTransport },
                handshakeTimeoutMs = 150L,
            )
            assertFalse(stalledClient.connect())
            assertFalse(stalledClient.isReady.value)
            assertEquals(RadioConnectionState.ERROR, stalledClient.connectionState.value)
            assertTrue(stalledTransport.closed)
        } finally {
            scope.cancel()
        }
    }
}
