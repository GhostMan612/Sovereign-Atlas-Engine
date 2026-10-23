// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.android.mesh

import com.sovereignatlas.atlas.android.settings.NetworkProfile
import com.sovereignatlas.atlas.geo.cot.CotMessageRouter
import com.sovereignatlas.atlas.geo.cot.MarkerStore
import com.sovereignatlas.atlas.geo.cot.MessageStore
import com.sovereignatlas.atlas.geo.cot.PliStore
import com.sovereignatlas.atlas.tactical.dtn.FragmentStore
import com.sovereignatlas.atlas.tactical.mesh.MockMeshRadioClient
import com.sovereignatlas.atlas.tactical.mesh.PortNum
import com.sovereignatlas.atlas.tactical.mesh.RadioConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeshTransportSupervisorTest {

    private class TestHarness(
        scope: kotlinx.coroutines.CoroutineScope,
        var activeProfile: NetworkProfile = NetworkProfile.MESH_ONLY,
    ) : java.io.Closeable {
        val mockRadio = MockMeshRadioClient(initialState = RadioConnectionState.CONNECTED)
        val pliStore = PliStore(localDeviceUid = "test-node")
        val markerStore = MarkerStore(scope)
        val messageStore = MessageStore(localDeviceUid = "test-node")
        val fragmentStore = FragmentStore()

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
        )

        override fun close() {
            supervisor.stopSupervisor()
            markerStore.shutdown()
        }
    }

    @Test
    fun strictEmconBlocksTransmissions() = runTest {
        val harness = TestHarness(scope = backgroundScope, activeProfile = NetworkProfile.RADIO_SILENCE)
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
        val harness = TestHarness(scope = backgroundScope, activeProfile = NetworkProfile.CLOUD_ONLY)
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
        val harness = TestHarness(scope = backgroundScope, activeProfile = NetworkProfile.MESH_ONLY)
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
}
