// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.PacketDeliveryStage
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import com.sovereignatlas.atlas.tactical.mesh.TransmissionOutcome
import com.sovereignatlas.atlas.tactical.mesh.TransmissionPermit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum as ProtoPortNum
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Routing
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class AndroidBleRadioClientTest {

    @Test
    fun timedOutGattWriteQuarantinesFutureWrites() {
        val fence = GattWriteFence()
        assertTrue(fence.canWrite())
        fence.quarantine()
        assertFalse(fence.canWrite())
        fence.quarantine()
        assertFalse(fence.canWrite())
    }

    @Test
    fun silentPeerDeliveryExpiresWithoutFurtherRadioTraffic() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val now = AtomicLong(1_000L)
            val transport = FakeBleGattTransport(onWantConfigWritten = { fake, nonce ->
                fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(my_info = MyNodeInfo(my_node_num = 10))))
                fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(config_complete_id = nonce)))
            })
            val client = AndroidBleRadioClient(null, scope, { transport }, clockMs = { now.get() }, handshakeTimeoutMs = 3_000L)
            assertTrue(client.connect())
            assertTrue(client.transmitPacket(MeshPacket(from = 10, to = 20, portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP, payload = byteArrayOf(1), id = 77, wantAck = true)))
            now.addAndGet(120_000L)
            withTimeout(3_000L) {
                client.deliveryStates.first { it[77L]?.stage == PacketDeliveryStage.UNCONFIRMED }
            }
            assertEquals("Delivery acknowledgment timed out", client.deliveryStates.value[77L]?.errorMessage)
            client.disconnect()
        } finally {
            scope.cancel()
        }
    }

    private class FakeBleGattTransport(
        private val negotiatedMtu: Int = 512,
        private val onWantConfigWritten: (FakeBleGattTransport, Int) -> Unit = { _, _ -> },
        private val packetOutcome: TransmissionOutcome? = null,
    ) : BleGattTransport {
        val events = mutableListOf<String>()
        val writtenToRadio = mutableListOf<ByteArray>()
        val readQueue = ConcurrentLinkedQueue<ByteArray>()
        var callbacks: BleGattCallbacks? = null
        var closed: Boolean = false
        val readSignal = Channel<Unit>(Channel.UNLIMITED)

        override suspend fun connect(callbacks: BleGattCallbacks): Boolean {
            this.callbacks = callbacks
            events += "connect"
            return true
        }

        override suspend fun requestMtu(mtu: Int): Int {
            events += "requestMtu:$mtu"
            return negotiatedMtu
        }

        override suspend fun discoverMeshtasticCharacteristics(): Boolean {
            events += "discover"
            return true
        }

        override suspend fun enableFromNumNotifications(): Boolean {
            events += "enableFromNum"
            return true
        }

        override suspend fun writeToRadio(bytes: ByteArray): Boolean {
            events += "writeToRadio:${bytes.size}"
            writtenToRadio += bytes
            val decoded = runCatching { ToRadio.ADAPTER.decode(bytes) }.getOrNull()
            decoded?.want_config_id?.let { nonce ->
                onWantConfigWritten(this, nonce)
            }
            return true
        }

        override suspend fun writeToRadioOutcome(bytes: ByteArray, permit: TransmissionPermit?): TransmissionOutcome =
            packetOutcome ?: if (writeToRadio(bytes)) TransmissionOutcome.SUBMITTED else TransmissionOutcome.UNCONFIRMED

        override suspend fun readFromRadio(): ByteArray {
            events += "readFromRadio"
            val item = readQueue.poll() ?: ByteArray(0)
            readSignal.trySend(Unit)
            return item
        }

        override fun close() {
            closed = true
            events += "close"
        }

        fun notifyFromNum(count: Int = 1) {
            val b = byteArrayOf(count.toByte(), 0, 0, 0)
            callbacks?.onCharacteristicNotified(MeshtasticBleUuids.FROM_NUM_UUID, b)
        }
    }

    @Test
    fun submittedWriteWithMissingCompletionRemainsUnconfirmed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val transport = FakeBleGattTransport(packetOutcome = TransmissionOutcome.UNCONFIRMED, onWantConfigWritten = { fake, nonce ->
                fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(my_info = MyNodeInfo(my_node_num = 10))))
                fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(config_complete_id = nonce)))
            })
            val client = AndroidBleRadioClient(null, scope, { transport }, handshakeTimeoutMs = 3_000L)
            assertTrue(client.connect())
            val permit = object : TransmissionPermit {
                override fun <T : Any> submit(action: () -> T): T? = action()
            }
            val packet = MeshPacket(from = 10, to = 20, portNum = PortNum.PORTNUM_TEXT_MESSAGE_APP, payload = byteArrayOf(1), id = 78, wantAck = true)
            assertEquals(TransmissionOutcome.UNCONFIRMED, client.transmitPacketOutcome(packet, permit))
            assertEquals(PacketDeliveryStage.UNCONFIRMED, client.deliveryStates.value[78L]?.stage)
            client.disconnect()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun staleDisconnectCannotCloseReplacementConnection() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            fun transport() = FakeBleGattTransport(onWantConfigWritten = { fake, nonce ->
                fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(id = 1, my_info = MyNodeInfo(my_node_num = 10))))
                fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(id = 2, config_complete_id = nonce)))
            })
            val first = transport()
            val second = transport()
            val transports = ArrayDeque(listOf(first, second))
            val client = AndroidBleRadioClient(null, scope, { transports.removeFirst() }, handshakeTimeoutMs = 3_000L)
            assertTrue(client.connect())
            client.disconnect()
            assertTrue(client.connect())
            val existing = scope.coroutineContext[Job]!!.children.toSet()
            first.callbacks!!.onDisconnected(true, "late")
            scope.coroutineContext[Job]!!.children.filter { it !in existing }.toList().forEach { it.join() }
            assertTrue(client.isReady.value)
            assertFalse(second.closed)
            client.disconnect()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun officialBleUuidsMatchMeshtasticFirmwareSpec() {
        assertEquals(UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd"), MeshtasticBleUuids.SERVICE_UUID)
        assertEquals(UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7"), MeshtasticBleUuids.TO_RADIO_UUID)
        assertEquals(UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002"), MeshtasticBleUuids.FROM_RADIO_UUID)
        assertEquals(UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453"), MeshtasticBleUuids.FROM_NUM_UUID)
        assertEquals(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), MeshtasticBleUuids.CCCD_UUID)
    }

    @Test
    fun handshakeSubscribesFromNumDrainsUntilEmptyAndTransmitsEnvelopesWithoutSerialFraming() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val transport = FakeBleGattTransport(
                negotiatedMtu = 256,
                onWantConfigWritten = { fake, nonce ->
                    fake.readQueue.add(
                        FromRadio.ADAPTER.encode(FromRadio(id = 1, my_info = MyNodeInfo(my_node_num = 0x11112222)))
                    )
                    fake.readQueue.add(
                        FromRadio.ADAPTER.encode(
                            FromRadio(
                                id = 2,
                                metadata = DeviceMetadata(firmware_version = "2.8.2", hw_model = HardwareModel.RAK4631),
                            )
                        )
                    )
                    fake.readQueue.add(
                        FromRadio.ADAPTER.encode(
                            FromRadio(id = 3, config = Config(lora = Config.LoRaConfig(region = Config.LoRaConfig.RegionCode.US)))
                        )
                    )
                    fake.readQueue.add(
                        FromRadio.ADAPTER.encode(FromRadio(id = 4, config_complete_id = nonce))
                    )
                },
            )
            val client = AndroidBleRadioClient(
                context = null,
                scope = scope,
                transportFactory = { transport },
                handshakeTimeoutMs = 3_000L,
            )

            assertTrue(client.connect())
            assertTrue(client.isReady.value)
            assertEquals(RadioConnectionState.CONNECTED, client.connectionState.value)

            assertEquals(
                listOf("connect", "requestMtu:512", "discover", "enableFromNum"),
                transport.events.take(4),
            )
            val wantConfigWire = transport.writtenToRadio.first()
            assertFalse(
                wantConfigWire.size >= 2 && wantConfigWire[0] == 0x94.toByte() && wantConfigWire[1] == 0xC3.toByte()
            )
            assertEquals("915 MHz US", client.activeRadioInfo.value?.frequencyBand)
            assertEquals("2.8.2", client.activeRadioInfo.value?.firmwareVersion)
            assertEquals("RAK4631", client.activeRadioInfo.value?.radioModel)
            assertFalse(client.activeRadioInfo.value!!.isUsb)

            val payload = "ATAK-COT".encodeToByteArray()
            val txOk = client.transmitPacket(
                MeshPacket(
                    from = 0x11112222L,
                    to = 0x99887766L,
                    portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                    payload = payload,
                    id = 4444L,
                    wantAck = true,
                )
            )
            assertTrue(txOk)
            assertEquals(PacketDeliveryStage.QUEUED, client.deliveryStates.value[4444L]?.stage)

            val txEnvelope = ToRadio.ADAPTER.decode(transport.writtenToRadio.last())
            assertEquals(PortNum.PORTNUM_ATAK_PLUGIN, txEnvelope.packet!!.decoded!!.portnum.value)
            assertEquals(0x99887766L, txEnvelope.packet!!.to.toLong() and 0xFFFFFFFFL)
            assertArrayEquals(payload, txEnvelope.packet!!.decoded!!.payload.toByteArray())

            transport.readQueue.add(
                FromRadio.ADAPTER.encode(
                    FromRadio(
                        id = 5,
                        node_info = NodeInfo(num = 0x99887766L.toInt(), user = User(short_name = "R2", long_name = "Rover 2")),
                    )
                )
            )
            transport.readQueue.add(
                FromRadio.ADAPTER.encode(
                    FromRadio(id = 6, queueStatus = QueueStatus(res = 0, free = 10, maxlen = 16, mesh_packet_id = 4444))
                )
            )
            val ackBytes = Routing.ADAPTER.encode(Routing(error_reason = Routing.Error.NONE))
            transport.readQueue.add(
                FromRadio.ADAPTER.encode(
                    FromRadio(
                        id = 7,
                        packet = ProtoMeshPacket(
                            from = 0x99887766L.toInt(),
                            to = 0x11112222,
                            id = 888,
                            decoded = Data(
                                portnum = ProtoPortNum.ROUTING_APP,
                                payload = ByteString.of(*ackBytes),
                                request_id = 4444,
                            ),
                        ),
                    )
                )
            )
            transport.notifyFromNum()

            withTimeout(2_000L) {
                client.deliveryStates.first { it[4444L]?.stage == PacketDeliveryStage.PEER_DELIVERED }
            }
            assertEquals(1, client.discoveredNodes.value.size)
            assertEquals("R2", client.discoveredNodes.value.single().callsign)

            client.disconnect()
            assertFalse(client.isReady.value)
            assertEquals(RadioConnectionState.DISCONNECTED, client.connectionState.value)
            assertNull(client.activeRadioInfo.value)
            assertTrue(transport.closed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun mtuNegotiationRejectsOversizedToRadioPacketAndHandshakeTimeoutFailsClosed() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val smallMtuTransport = FakeBleGattTransport(
                negotiatedMtu = 40,
                onWantConfigWritten = { fake, nonce ->
                    fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(id = 1, my_info = MyNodeInfo(my_node_num = 10))))
                    fake.readQueue.add(FromRadio.ADAPTER.encode(FromRadio(id = 2, config_complete_id = nonce)))
                },
            )
            val mtuClient = AndroidBleRadioClient(
                context = null,
                scope = scope,
                transportFactory = { smallMtuTransport },
                handshakeTimeoutMs = 2_000L,
            )
            assertTrue(mtuClient.connect())
            val writeCountBefore = smallMtuTransport.writtenToRadio.size
            val oversizedSent = mtuClient.transmitPacket(
                MeshPacket(
                    from = 10L,
                    to = 0xFFFFFFFFL,
                    portNum = PortNum.PORTNUM_PRIVATE_APP,
                    payload = ByteArray(45),
                    id = 500L,
                )
            )
            assertFalse(oversizedSent)
            assertEquals(writeCountBefore, smallMtuTransport.writtenToRadio.size)
            assertEquals(PacketDeliveryStage.FAILED, mtuClient.deliveryStates.value[500L]?.stage)
            mtuClient.disconnect()

            val timeoutTransport = FakeBleGattTransport(negotiatedMtu = 512)
            val timeoutClient = AndroidBleRadioClient(
                context = null,
                scope = scope,
                transportFactory = { timeoutTransport },
                handshakeTimeoutMs = 150L,
            )
            assertFalse(timeoutClient.connect())
            assertFalse(timeoutClient.isReady.value)
            assertEquals(RadioConnectionState.ERROR, timeoutClient.connectionState.value)
            assertTrue(timeoutTransport.closed)
        } finally {
            scope.cancel()
        }
    }
}
