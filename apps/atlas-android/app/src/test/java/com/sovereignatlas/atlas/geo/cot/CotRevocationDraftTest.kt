// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import com.sovereignatlas.atlas.field.WaypointSharingPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DRAFT tombstone, tested as a draft.
 *
 * What these tests actually establish: the event is well-formed, carries no
 * coordinates, and round-trips through this package's own parser. What they do NOT
 * establish, and cannot without a real ATAK peer on a real mesh, is that a receiving
 * node honours it. [CotRevocationDraft] says so in its documentation; these tests
 * are not allowed to imply otherwise by omission.
 */
final class CotRevocationDraftTest {

    @Test
    fun theDeletionEventNamesTheSharedUid() {
        val xml = CotRevocationDraft.buildDeletion("wp-42", atMillis = 1_700_000_000_000L)

        assertNotNull(xml)
        assertTrue(xml!!.contains("uid=\"wp-42\""))
        assertTrue(xml.contains("type=\"${CotRevocationDraft.DELETE_TYPE}\""))
    }

    @Test
    fun theDeletionEventCarriesNoPointElement() {
        // The test that matters most here. A tombstone that included the position so
        // a peer could tidy its store would transmit the exact coordinates the
        // withdrawal exists to stop publishing — turning the safest action in the
        // feature into the last one that leaks.
        val xml = CotRevocationDraft.buildDeletion("wp-42", atMillis = 0L)!!

        assertFalse("a withdrawal must not carry coordinates", xml.contains("<point"))
        assertFalse(xml.contains("lat="))
        assertFalse(xml.contains("lon="))
    }

    @Test
    fun theDeletionEventLinksToTheWaypointItWithdraws() {
        val xml = CotRevocationDraft.buildDeletion("wp-42", atMillis = 0L)!!

        assertTrue(xml.contains("<link uid=\"wp-42\""))
        assertTrue(xml.contains("type=\"b-m-p-w\""))
    }

    @Test
    fun aBlankIdYieldsNoEvent() {
        assertNull(CotRevocationDraft.buildDeletion("", atMillis = 0L))
        assertNull(CotRevocationDraft.buildDeletion("   ", atMillis = 0L))
    }

    @Test
    fun theUidIsXmlEscaped() {
        // A uid is operator- and peer-supplied. Unescaped, a crafted uid could close
        // the attribute and inject detail elements into a withdrawal that every
        // peer parses.
        val xml = CotRevocationDraft.buildDeletion("wp\"><injected a=\"b", atMillis = 0L)!!

        assertFalse("the crafted uid must not break out of the attribute", xml.contains("\"><injected"))
        assertTrue(xml.contains("&quot;"))
        assertTrue(xml.contains("&lt;"))
    }

    @Test
    fun theWireFormIsAnEmptyArrayForABlankId() {
        val payload = CotRevocationDraft.asSerializer().serializeRevocation("  ", atMillis = 0L)

        assertEquals(0, payload.size)
    }

    @Test
    fun theWireFormIsUtf8Xml() {
        val payload = CotRevocationDraft.asSerializer().serializeRevocation("wp-42", atMillis = 0L)

        assertTrue(payload.toString(Charsets.UTF_8).startsWith("<event"))
    }

    // ------------------------------------------------------------------
    // Timestamp formatting, cross-checked against this package's own parser
    // ------------------------------------------------------------------

    @Test
    fun formattingRoundTripsThroughIso8601() {
        // The formatter is the inverse of Iso8601's parser. Asserting a round trip
        // proves they agree without either test restating the other's constants.
        for (instant in listOf(0L, 1_700_000_000_000L, 1_000_000_000_123L)) {
            val text = CotRevocationDraft.formatIso8601Utc(instant)
            assertEquals(
                "round trip failed for $instant",
                instant,
                Iso8601.parseToEpochMillis(text),
            )
        }
    }

    @Test
    fun formattingIsUtcRegardlessOfTheWallClock() {
        // The property that a local-time formatter would break, and the reason the
        // test asserts the literal rather than comparing against a clock.
        assertEquals("1970-01-01T00:00:00.000Z", CotRevocationDraft.formatIso8601Utc(0L))
        assertEquals("2023-11-14T22:13:20.000Z", CotRevocationDraft.formatIso8601Utc(1_700_000_000_000L))
    }

    @Test
    fun theStaleTimeIsFifteenMinutesAfterSend() {
        val at = 1_700_000_000_000L
        val xml = CotRevocationDraft.buildDeletion("wp-42", atMillis = at)!!

        val time = Regex("time=\"([^\"]+)\"").find(xml)!!.groupValues[1]
        val stale = Regex("stale=\"([^\"]+)\"").find(xml)!!.groupValues[1]

        assertEquals(at, Iso8601.parseToEpochMillis(time))
        assertEquals(at + 15 * 60 * 1000L, Iso8601.parseToEpochMillis(stale))
    }

    @Test
    fun theWithdrawalIsWellFormedEnoughForThisPackageToRead() {
        // Not a full XML validation. It asserts the shape the pure parser relies on,
        // so a malformed tombstone fails here rather than silently on the mesh.
        val xml = CotRevocationDraft.buildDeletion("wp-42", atMillis = 1_700_000_000_000L)!!

        assertTrue(xml.startsWith("<event "))
        assertTrue(xml.endsWith("</event>"))
        assertEquals(1, Regex("<event ").findAll(xml).count())
        assertEquals(1, Regex("</event>").findAll(xml).count())
        assertEquals(1, Regex("<detail>").findAll(xml).count())
        assertEquals(1, Regex("</detail>").findAll(xml).count())
    }

    // ------------------------------------------------------------------
    // The tombstone through the router
    // ------------------------------------------------------------------

    @Test
    fun stoppingASharedWaypointEmitsTheDraftTombstone() {
        val router = WaypointSharingRouter(
            serializer = { _, _ -> error("a PRIVATE waypoint must never be serialized") },
            revocationSerializer = CotRevocationDraft.asSerializer(),
        )
        val shared = ShareableWaypoint(
            id = "wp-draft",
            name = "Cache",
            latitude = 44.9,
            longitude = -93.1,
            timestampMillis = 1L,
            notes = null,
            policy = WaypointSharingPolicy.Team,
        )

        val outcome = router.stopSharing(shared, atMillis = 1_700_000_000_000L, wasShared = true)
        val payload = (outcome.decision as OutboundWaypointDecision.Revoke).payload
            .toString(Charsets.UTF_8)

        assertTrue(payload.contains("t-x-d-d"))
        assertTrue(payload.contains("wp-draft"))
        assertFalse(payload.contains("44.9"))
        assertEquals(WaypointSharingPolicy.Private, outcome.waypoint.policy)
    }

    @Test
    fun theRoundTripWaypointIsNotSerializableAfterReverting() {
        // The end-to-end guarantee: the waypoint that came back from stop-sharing
        // would be refused by the outbound router, so a stale reference to the
        // pre-revert object is the only thing that could still publish it.
        val router = WaypointSharingRouter(
            serializer = { waypoint, scope ->
                "payload:${waypoint.id}:$scope".toByteArray()
            },
            revocationSerializer = CotRevocationDraft.asSerializer(),
        )
        val shared = ShareableWaypoint(
            id = "wp-final",
            name = "Cache",
            latitude = 44.9,
            longitude = -93.1,
            timestampMillis = 1L,
            notes = null,
            policy = WaypointSharingPolicy.Team,
        )

        val reverted = router.stopSharing(shared, atMillis = 1L, wasShared = true).waypoint

        assertTrue(router.route(reverted) is OutboundWaypointDecision.Withhold)
        assertTrue(
            "the original shared object is deliberately still Team — the caller must " +
                "persist the reverted one",
            shared.policy == WaypointSharingPolicy.Team,
        )
    }
}
