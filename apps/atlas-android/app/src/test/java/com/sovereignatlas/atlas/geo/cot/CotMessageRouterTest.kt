// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routing rules, proven on the JVM.
 *
 * Before this router existed these rules lived in `AtakMulticastListener`, whose
 * constructor needs `WifiManager` and `PowerManager`. That meant the single fact a
 * tactical map most needs to be right - a hostile contact must never be filed as a
 * friendly - could not be asserted without a device. Every case below used to be
 * untestable.
 */
final class CotMessageRouterTest {

    /** Records what the router delegates, so the escape hatch can be asserted. */
    private class RecordingLegacyReader(
        private val result: (ByteArray) -> ParsedCot? = { null },
    ) : LegacyCotReader {
        val seen = mutableListOf<ByteArray>()
        override fun parseUnreadable(packetData: ByteArray): ParsedCot? {
            seen += packetData
            return result(packetData)
        }
    }

    private class Fixture(
        legacy: LegacyCotReader = RecordingLegacyReader(),
        localDeviceUid: String = "local-node",
    ) {
        val pli = PliStore(localDeviceUid = localDeviceUid)
        val markers = MarkerStore(TestScope())
        val messages = MessageStore(localDeviceUid = localDeviceUid)
        val legacy = legacy
        val router = CotMessageRouter(
            pliStore = pli,
            markerStore = markers,
            messageStore = messages,
            legacyReader = legacy,
            clock = { OBSERVED },
        )
    }

    private fun xml(type: String, uid: String, callsign: String = "CONTACT-1"): ByteArray =
        ("<event version=\"2.0\" uid=\"$uid\" type=\"$type\" how=\"m-g\" " +
            "time=\"2026-10-01T00:00:00Z\" start=\"2026-10-01T00:00:00Z\" " +
            "stale=\"2026-10-01T00:05:00Z\">" +
            "<point lat=\"44.9\" lon=\"-93.1\" hae=\"100.0\" ce=\"5.0\" le=\"5.0\"/>" +
            "<detail><contact callsign=\"$callsign\"/></detail></event>").toByteArray()

    @Test
    fun aFriendlyGroundContactReachesThePliStore() {
        val f = Fixture()
        f.router.route(xml("a-f-G-U-C", "friendly-1"))

        assertEquals(1, f.pli.observe().value.size)
        assertEquals("friendly-1", f.pli.observe().value["friendly-1"]?.uid)
        assertTrue("a friendly must not become a marker", f.markers.observe().value.isEmpty())
    }

    @Test
    fun aHostileGroundContactReachesTheMarkerStoreAndNeverThePliStore() {
        // THE case. a-h-G is hostile. If this ever lands in the PLI store, an
        // operator sees an enemy drawn as a friend.
        val f = Fixture()
        f.router.route(xml("a-h-G", "hostile-1"))

        assertEquals(1, f.markers.observe().value.size)
        assertEquals("hostile-1", f.markers.observe().value["hostile-1"]?.uid)
        assertTrue("a hostile must NEVER reach the friendly PLI store", f.pli.observe().value.isEmpty())
    }

    @Test
    fun neutralAndUnknownAffiliationsReachTheMarkerStore() {
        val f = Fixture()
        f.router.route(xml("a-n-G", "neutral-1"))
        f.router.route(xml("a-u-Z", "unknown-1"))

        assertEquals(2, f.markers.observe().value.size)
        assertTrue(f.pli.observe().value.isEmpty())
    }

    @Test
    fun pirateContactsReachTheMarkerStore() {
        val f = Fixture()
        f.router.route(xml("b-m-p-i", "pirate-1"))

        assertEquals(1, f.markers.observe().value.size)
        assertTrue(f.pli.observe().value.isEmpty())
    }

    @Test
    fun geochatIsDelegatedAndNeverBecomesAMapSymbol() {
        val legacy = RecordingLegacyReader()
        val f = Fixture(legacy)
        f.router.route(xml("b-t-f", "chat-1"))

        assertEquals("chat must be delegated", 1, legacy.seen.size)
        assertTrue("chat must not become a track", f.pli.observe().value.isEmpty())
        assertTrue(f.markers.observe().value.isEmpty())
    }

    @Test
    fun anUnknownTypeIsDroppedWithoutConsultingTheLegacyReader() {
        val legacy = RecordingLegacyReader()
        val f = Fixture(legacy)
        f.router.route(xml("z-nonsense", "junk-1"))

        assertTrue(legacy.seen.isEmpty())
        assertTrue(f.pli.observe().value.isEmpty())
        assertTrue(f.markers.observe().value.isEmpty())
    }

    @Test
    fun malformedBytesAreDroppedWithoutThrowing() {
        val legacy = RecordingLegacyReader()
        val f = Fixture(legacy)

        f.router.route("not xml".toByteArray())
        f.router.route(ByteArray(0))
        f.router.route(byteArrayOf(0xFF.toByte(), 0x00, 0x12, 0x3B))

        assertTrue(f.pli.observe().value.isEmpty())
        assertTrue(f.markers.observe().value.isEmpty())
    }

    @Test
    fun aProtobufFramedPayloadIsDelegatedWholesale() {
        val legacy = RecordingLegacyReader()
        val f = Fixture(legacy)
        val framed = byteArrayOf(0xBF.toByte(), 0x01, 0xBF.toByte(), 0x08, 0x0A)

        f.router.route(framed)

        assertEquals(1, legacy.seen.size)
        assertTrue(
            "the raw framed bytes must be handed over intact",
            legacy.seen[0].contentEquals(framed),
        )
        // A protobuf payload is not XML, so the pure reader must not have seen it.
        assertTrue(f.markers.observe().value.isEmpty())
    }

    @Test
    fun aDelegatedResultIsStillRoutedByTheseSameRules() {
        // Protobuf comes back through this class, so a protobuf hostile obeys the
        // marker rule too rather than drifting to a second implementation.
        val legacy = RecordingLegacyReader {
            ParsedCot.Marker(
                CotMarker(
                    uid = "pb-hostile",
                    type = "a-h-G",
                    callsign = "VIA-PROTOBUF",
                    latitude = 44.9,
                    longitude = -93.1,
                    altitude = null,
                    timestampMillis = OBSERVED,
                ),
            )
        }
        val f = Fixture(legacy)
        f.router.route(byteArrayOf(0xBF.toByte(), 0x01, 0xBF.toByte(), 0x08))

        assertEquals(1, f.markers.observe().value.size)
        assertEquals("VIA-PROTOBUF", f.markers.observe().value["pb-hostile"]?.callsign)
        assertTrue(f.pli.observe().value.isEmpty())
    }

    @Test
    fun aDelegatedChatResultReachesTheMessageStore() {
        val legacy = RecordingLegacyReader {
            ParsedCot.Chat(
                ChatMessage(
                    messageId = "m-1",
                    senderUid = "someone-else",
                    senderCallsign = "SENDER",
                    chatroom = "All Chat Rooms",
                    remarksTo = "All Chat Rooms",
                    text = "contact north",
                    timestampMillis = OBSERVED,
                    isSelf = false,
                ),
            )
        }
        val f = Fixture(legacy)
        f.router.route(xml("b-t-f", "chat-1"))

        assertEquals(1, f.messages.messages.value.size)
        assertEquals("contact north", f.messages.messages.value.first().text)
    }

    @Test
    fun xmlFramedPayloadsStillReachThePureReader() {
        // The 0xBF 0x00 0xBF frame precedes an XML payload. The pure reader
        // locates <event and ignores the header, so a framed PLI still parses.
        val f = Fixture()
        val framed = byteArrayOf(0xBF.toByte(), 0x00, 0xBF.toByte()) + xml("a-f-G", "framed-1")

        f.router.route(framed)

        assertEquals(1, f.pli.observe().value.size)
    }

    @Test
    fun pruneDropsExpiredTracksFromBothStores() {
        val f = Fixture()
        f.router.route(xml("a-f-G", "friendly-1"))
        f.router.route(xml("a-h-G", "hostile-1"))
        assertEquals(1, f.pli.observe().value.size)
        assertEquals(1, f.markers.observe().value.size)

        // Both fixtures carry stale=00:05:00Z, so past that instant both go.
        f.router.prune(Iso8601.parseToEpochMillis("2026-10-01T00:10:00Z")!!)

        assertTrue(f.pli.observe().value.isEmpty())
        assertTrue(f.markers.observe().value.isEmpty())
    }

    @Test
    fun selfEchoIsStillFilteredByThePliStore() {
        val f = Fixture(localDeviceUid = "friendly-1")
        f.router.route(xml("a-f-G", "friendly-1"))

        assertTrue("a node must not track itself as a friendly", f.pli.observe().value.isEmpty())
    }

    private companion object {
        /** 2026-10-01T00:00:00Z, as an opaque observation instant. */
        const val OBSERVED = 1_790_812_800_000L
    }
}