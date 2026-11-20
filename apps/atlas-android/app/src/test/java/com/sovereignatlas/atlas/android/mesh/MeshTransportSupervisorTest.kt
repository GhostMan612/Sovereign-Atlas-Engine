// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import com.sovereignatlas.atlas.android.settings.NetworkProfile
import com.sovereignatlas.atlas.android.settings.CloudPolicyCoordinator
import com.sovereignatlas.atlas.geo.cot.CotMessageRouter
import com.sovereignatlas.atlas.geo.cot.MarkerStore
import com.sovereignatlas.atlas.geo.cot.MessageStore
import com.sovereignatlas.atlas.geo.cot.PliStore
import com.sovereignatlas.atlas.tactical.dtn.FragmentStore
import com.sovereignatlas.atlas.tactical.dtn.FragmentTier
import com.sovereignatlas.atlas.tactical.mesh.ChunkPacketCodec
import com.sovereignatlas.atlas.tactical.mesh.FragmentAckCodec
import com.sovereignatlas.atlas.tactical.mesh.FragmentAckReport
import com.sovereignatlas.atlas.tactical.mesh.MeshPacket
import com.sovereignatlas.atlas.tactical.mesh.MockMeshRadioClient
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeshTransportSupervisorTest {

    private class TestHarness(
        scope: kotlinx.coroutines.CoroutineScope,
        initialProfile: NetworkProfile = NetworkProfile.MESH_ONLY,
        clock: () -> Long = System::currentTimeMillis,
    ) : java.io.Closeable {
        private val policy = CloudPolicyCoordinator(initialProfile)
        var activeProfile: NetworkProfile = initialProfile
            set(value) {
                policy.transition(value) { field = value }
            }
        val mockRadio = MockMeshRadioClient(initialState = RadioConnectionState.CONNECTED)
        val pliStore = PliStore(localDeviceUid = "test-node")
        val markerStore = MarkerStore(scope)
        val messageStore = MessageStore(localDeviceUid = "test-node")
        val fragmentStore = FragmentStore(clock = clock)

        val cotRouter = CotMessageRouter(
            pliStore = pliStore,
            markerStore = markerStore,
            messageStore = messageStore,
            legacyReader = { null },
        )

        val supervisor = MeshTransportSupervisor(
            radioClient = mockRadio,
            networkProfileProvider = { activeProfile },
            cotRouter = cotRouter,
            fragmentStore = fragmentStore,
            localUid = "test-node",
            scope = scope,
            transmissionPolicy = policy,
        )

        override fun close() {
            supervisor.stopSupervisor()
            markerStore.shutdown()
        }
    }

    @Test
    fun strictEmconBlocksTransmissions() = runTest {
        val harness = TestHarness(scope = backgroundScope, initialProfile = NetworkProfile.RADIO_SILENCE)
        try {
            assertFalse(harness.supervisor.canTransmit())

            var threwMarker = false
            try {
                harness.supervisor.sendCotMarker("a-h-G", "HOSTILE-1", 37.77, -122.41)
            } catch (e: IllegalStateException) {
                threwMarker = true
            }
            assertTrue(threwMarker)

            var threwText = false
            try {
                harness.supervisor.sendTextMessage("Test")
            } catch (e: IllegalStateException) {
                threwText = true
            }
            assertTrue(threwText)
            assertTrue(harness.mockRadio.transmittedPackets.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun cloudOnlyProfileBlocksTransmissions() = runTest {
        val harness = TestHarness(scope = backgroundScope, initialProfile = NetworkProfile.CLOUD_ONLY)
        try {
            assertFalse(harness.supervisor.canTransmit())

            var threw = false
            try {
                harness.supervisor.sendCotMarker("a-h-G", "HOSTILE-2", 37.77, -122.41)
            } catch (e: IllegalStateException) {
                threw = true
            }
            assertTrue(threw)
            assertTrue(harness.mockRadio.transmittedPackets.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun meshOnlyProfileAllowsTransmissions() = runTest {
        val harness = TestHarness(scope = backgroundScope, initialProfile = NetworkProfile.MESH_ONLY)
        try {
            assertTrue(harness.supervisor.canTransmit())

            val sent = harness.supervisor.sendCotMarker("a-h-G", "RED-LEADER", 37.7749, -122.4194)
            assertTrue(sent)
            assertEquals(1, harness.mockRadio.transmittedPackets.size)
            val packet = harness.mockRadio.transmittedPackets.first()
            assertEquals(PortNum.PORTNUM_ATAK_PLUGIN, packet.portNum)
            assertTrue(packet.payload.isNotEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun textMessageAtPayloadLimitTransmits() = runTest {
        val harness = TestHarness(scope = backgroundScope)
        try {
            val text = "A".repeat(MeshTransportSupervisor.LORA_MTU_BYTES)
            assertTrue(harness.supervisor.sendTextMessage(text))
            assertEquals(1, harness.mockRadio.transmittedPackets.size)
            assertEquals(
                MeshTransportSupervisor.LORA_MTU_BYTES,
                harness.mockRadio.transmittedPackets.single().payload.size,
            )
            assertEquals(PortNum.PORTNUM_TEXT_MESSAGE_APP, harness.mockRadio.transmittedPackets.single().portNum)
        } finally {
            harness.close()
        }
    }

    @Test
    fun oversizedUtf8TextMessageIsRejectedWithoutRadioTransmission() = runTest {
        val harness = TestHarness(scope = backgroundScope)
        try {
            assertFalse(harness.supervisor.sendTextMessage("A".repeat(MeshTransportSupervisor.LORA_MTU_BYTES + 1)))
            assertFalse(harness.supervisor.sendTextMessage("é".repeat(120)))
            assertTrue(harness.mockRadio.transmittedPackets.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun inboundHostileXmlMarkerRoutesToMarkerStore() = runTest {
        val harness = TestHarness(scope = backgroundScope)
        try {
            testScheduler.runCurrent()

            val xmlCot = ("<event version=\"2.0\" uid=\"hostile-alpha\" type=\"a-h-G\" how=\"m-g\" " +
                "time=\"2026-10-23T12:00:00Z\" start=\"2026-10-23T12:00:00Z\" stale=\"2026-10-23T13:00:00Z\">" +
                "<point lat=\"38.8951\" lon=\"-77.0364\" hae=\"10.0\" ce=\"5.0\" le=\"5.0\"/>" +
                "<detail><contact callsign=\"HOSTILE-ALPHA\"/></detail></event>").toByteArray(Charsets.UTF_8)

            val packet = com.sovereignatlas.atlas.tactical.mesh.MeshPacket(
                from = 0x9999L,
                to = 0xFFFFFFFFL,
                portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                payload = xmlCot,
            )

            harness.mockRadio.simulateInboundPacket(packet)
            testScheduler.runCurrent()

            val markers = harness.markerStore.markerStream.value
            assertEquals(1, markers.size)
            val marker = markers["hostile-alpha"]
            org.junit.Assert.assertNotNull(marker)
            assertEquals("a-h-G", marker!!.type)
            assertEquals(38.8951, marker.latitude, 0.0001)
            assertEquals(-77.0364, marker.longitude, 0.0001)
        } finally {
            harness.close()
        }
    }
    @Test
    fun missedMiddleChunkIsRepairedWithOriginalChk1WireFrame() = runTest {
        val sender = TestHarness(scope = backgroundScope)
        val receiver = TestHarness(scope = backgroundScope)
        try {
            testScheduler.runCurrent()
            assertTrue(sender.supervisor.sendCotMarker("a-h-G", "LONG-".repeat(300), 37.77, -122.41))
            val original = sender.mockRadio.transmittedPackets.toList()
            assertTrue("marker should fragment", original.size > 2)
            assertTrue(original.all { it.payload.size <= MeshTransportSupervisor.LORA_MTU_BYTES })
            val decoded = original.map { ChunkPacketCodec.decode(it.payload)!! }
            val id = decoded.first().transferId
            assertTrue(decoded.all { it.transferId == id && it.totalChunks == original.size })
            val missingIndex = original.size / 2
            for (i in original.indices) {
                if (i == missingIndex) continue
                receiver.mockRadio.simulateInboundPacket(original[i])
                testScheduler.runCurrent()
            }
            assertFalse(receiver.fragmentStore.isInboundComplete(id))
            val nack = receiver.fragmentStore.generateAckReport(id, round = 1)!!
            assertEquals(1, nack.missing.count { it })
            val nackPayload = FragmentAckCodec.encode(listOf(nack))
            assertTrue(nackPayload.size <= MeshTransportSupervisor.LORA_MTU_BYTES)
            sender.mockRadio.simulateInboundPacket(
                MeshPacket(
                    from = 0xBEEFL,
                    to = 0xFFFFFFFFL,
                    portNum = PortNum.PORTNUM_ATAK_PLUGIN,
                    payload = nackPayload,
                )
            )
            testScheduler.runCurrent()
            val repairs = sender.mockRadio.transmittedPackets.drop(original.size)
            assertEquals(1, repairs.size)
            assertEquals(0xBEEFL, repairs.single().to)
            assertArrayEquals(original[missingIndex].payload, repairs.single().payload)
            assertTrue(repairs.single().payload.size <= MeshTransportSupervisor.LORA_MTU_BYTES)
            receiver.mockRadio.simulateInboundPacket(repairs.single())
            testScheduler.runCurrent()
            assertTrue(receiver.fragmentStore.isInboundComplete(id))
            val expected = decoded.fold(ByteArray(0)) { bytes, part -> bytes + part.data }
            assertArrayEquals(expected, receiver.fragmentStore.assembleInboundPayload(id))
        } finally {
            sender.close()
            receiver.close()
        }
    }

    @Test
    fun existingInboundTransferWithoutPermitCannotAcquireFreshAuthorization() = runTest {
        val epoch = System.currentTimeMillis()
        val receiver = TestHarness(scope = backgroundScope, clock = { epoch + testScheduler.currentTime })
        try {
            testScheduler.runCurrent()
            receiver.fragmentStore.registerInbound("old-transfer", 0, 3, FragmentTier.PUBLIC, "peer", epoch + 600_000L, byteArrayOf(1))
            receiver.activeProfile = NetworkProfile.RADIO_SILENCE
            receiver.activeProfile = NetworkProfile.MESH_ONLY
            val chunk = ChunkPacketCodec.encode("old-transfer", 1, 3, byteArrayOf(2))!!
            receiver.mockRadio.simulateInboundPacket(MeshPacket(from = 2, to = 1, portNum = PortNum.PORTNUM_ATAK_PLUGIN, payload = chunk))
            val fresh = ChunkPacketCodec.encode("fresh-transfer", 0, 3, byteArrayOf(3))!!
            receiver.mockRadio.simulateInboundPacket(MeshPacket(from = 2, to = 1, portNum = PortNum.PORTNUM_ATAK_PLUGIN, payload = fresh))
            testScheduler.runCurrent()
            testScheduler.advanceTimeBy(5_001L)
            testScheduler.runCurrent()
            val sent = receiver.mockRadio.transmittedPackets.toList()
            assertEquals(1, sent.size)
            assertEquals("fresh-transfer", FragmentAckCodec.decode(sent.single().payload)!!.single().transferId)
        } finally {
            receiver.close()
        }
    }

    @Test
    fun repairDoesNotReuseAuthorizationAfterRadioSilence() = runTest {
        val sender = TestHarness(scope = backgroundScope)
        try {
            testScheduler.runCurrent()
            assertTrue(sender.supervisor.sendCotMarker("a-h-G", "LONG-".repeat(300), 37.77, -122.41))
            val original = sender.mockRadio.transmittedPackets.toList()
            val first = ChunkPacketCodec.decode(original.first().payload)!!
            val report = FragmentAckReport(first.transferId, 1, first.totalChunks, 0, 0, booleanArrayOf(true))
            sender.activeProfile = NetworkProfile.RADIO_SILENCE
            sender.activeProfile = NetworkProfile.MESH_ONLY
            sender.mockRadio.simulateInboundPacket(MeshPacket(from = 2, to = 1, portNum = PortNum.PORTNUM_ATAK_PLUGIN, payload = FragmentAckCodec.encode(listOf(report))))
            testScheduler.runCurrent()
            assertEquals(original.size, sender.mockRadio.transmittedPackets.size)
        } finally {
            sender.close()
        }
    }

    @Test
    fun excessiveChunkCountFailsBeforeAnyRadioPacket() = runTest {
        val harness = TestHarness(scope = backgroundScope)
        try {
            assertFalse(harness.supervisor.sendCotMarker("a-h-G", "X".repeat(60_000), 37.77, -122.41))
            assertTrue(harness.mockRadio.transmittedPackets.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun handshakingBlocksTransmissionsAndStreamingMaintainsReadiness() = runTest {
        val harness = TestHarness(scope = backgroundScope)
        try {
            harness.mockRadio.setConnectionState(RadioConnectionState.HANDSHAKING)
            assertFalse(harness.supervisor.isReady.value)
            assertFalse(harness.supervisor.sendCotMarker("a-f-G", "BRAVO", 37.77, -122.41))
            assertFalse(harness.supervisor.sendTextMessage("HELLO"))
            assertFalse(harness.supervisor.pingNode(0xBEEFL))
            assertTrue(harness.mockRadio.transmittedPackets.isEmpty())

            harness.mockRadio.setConnectionState(RadioConnectionState.STREAMING)
            assertTrue(harness.supervisor.isReady.value)
            assertTrue(harness.supervisor.sendCotMarker("a-f-G", "BRAVO", 37.77, -122.41))
            assertEquals(1, harness.mockRadio.transmittedPackets.size)

            harness.activeProfile = NetworkProfile.RADIO_SILENCE
            var blocked = false
            try {
                harness.supervisor.sendCotMarker("a-f-G", "EMCON", 37.77, -122.41)
            } catch (_: IllegalStateException) {
                blocked = true
            }
            assertTrue(blocked)
        } finally {
            harness.close()
        }
    }
}
