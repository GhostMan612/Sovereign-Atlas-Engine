// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The live UDP ingest path routes on these rules, so they are pinned here rather
 * than left to a device.
 *
 * The test that matters most is [hostileGroundContactsAreMarkersNotPlis]. CoT's
 * type prefix is not "friendly vs everything else": the second letter is a
 * dimension and the third is the affiliation. A rule of "a- means friendly" files
 * every hostile contact as a friendly PLI on the operator's map.
 */
final class CotIngestTest {

    private val observed = 1_790_812_800_000L

    private fun event(type: String, uid: String = "u-1", stale: String = "2026-10-01T00:05:00Z") =
        CotEvent(
            version = "2.0",
            uid = uid,
            type = type,
            how = "m-g",
            time = "2026-10-01T00:00:00Z",
            start = "2026-10-01T00:00:00Z",
            stale = stale,
            lat = 44.9,
            lon = -93.1,
            hae = 100.0,
            ce = 5.0,
            le = 5.0,
            callsign = "CONTACT-1",
        )

    @Test
    fun friendlyGroundTypesArePlis() {
        assertEquals(CotTrackKind.PLI, event("a-f-G-U-C").trackKind())
        assertEquals(CotTrackKind.PLI, event("a-f-G-I").trackKind())
        assertEquals(CotTrackKind.PLI, event("a-f-G-U-C-I").trackKind())
    }

    @Test
    fun hostileGroundContactsAreMarkersNotPlis() {
        // THE regression guard. a-h-G is a HOSTILE ground contact. Filing it in
        // the friendly PLI store would show an enemy as a friend.
        assertEquals(CotTrackKind.MARKER, event("a-h-G").trackKind())
        assertEquals(CotTrackKind.MARKER, event("a-h-A-F").trackKind())
    }

    @Test
    fun neutralAndUnknownAffiliationsAreMarkers() {
        assertEquals(CotTrackKind.MARKER, event("a-n-G").trackKind())
        assertEquals(CotTrackKind.MARKER, event("a-u-Z").trackKind())
    }

    @Test
    fun pirateAndSarTypesAreMarkers() {
        assertEquals(CotTrackKind.MARKER, event("b-m-p-i").trackKind())
        assertEquals(CotTrackKind.MARKER, event("b-m-p-s").trackKind())
    }

    @Test
    fun geochatIsNotATrack() {
        // b- is not automatically a marker. GeoChat rides the same wire format and
        // must not become a map symbol.
        assertEquals(CotTrackKind.CHAT, event("b-t-f").trackKind())
    }

    @Test
    fun unrecognisedTypesAreIgnoredRatherThanGuessed() {
        assertEquals(CotTrackKind.UNKNOWN, event("z-unknown-thing").trackKind())
        assertEquals(CotTrackKind.UNKNOWN, event("").trackKind())
    }

    @Test
    fun ingestDecisionForAPliCarriesTheMappedTrack() {
        val decision = decideIngest(event("a-f-G-U-C"), observed)

        assertTrue(decision is CotIngestDecision.UpsertPli)
        val pli = (decision as CotIngestDecision.UpsertPli).pli
        assertEquals("u-1", pli.uid)
        assertEquals("CONTACT-1", pli.callsign)
        assertEquals(44.9, pli.latitude, 1e-9)
        assertEquals(observed, pli.timestamp)
        assertEquals(Iso8601.parseToEpochMillis("2026-10-01T00:05:00Z"), pli.expiresAtMillis)
    }

    @Test
    fun ingestDecisionForAMarkerCarriesTheMappedTrack() {
        val decision = decideIngest(event("a-h-G"), observed)

        assertTrue(decision is CotIngestDecision.UpsertMarker)
        val marker = (decision as CotIngestDecision.UpsertMarker).marker
        assertEquals("u-1", marker.uid)
        assertEquals("a-h-G", marker.type)
        assertEquals(100.0, marker.altitude!!, 1e-9)
        assertEquals(observed, marker.timestampMillis)
        assertEquals(Iso8601.parseToEpochMillis("2026-10-01T00:05:00Z"), marker.expiresAtMillis)
    }

    @Test
    fun chatIsDelegatedAndUnknownIsIgnored() {
        assertTrue(decideIngest(event("b-t-f"), observed) is CotIngestDecision.DelegateToLegacyChat)
        assertTrue(decideIngest(event("nonsense"), observed) is CotIngestDecision.Ignore)
    }

    @Test
    fun aHostileContactNeverReachesTheFriendlyStore() {
        // End-to-end through the decision, which is what the listener calls.
        val friendly = decideIngest(event("a-f-G", uid = "friendly-1"), observed)
        val hostile = decideIngest(event("a-h-G", uid = "hostile-1"), observed)

        assertTrue(friendly is CotIngestDecision.UpsertPli)
        assertTrue(hostile is CotIngestDecision.UpsertMarker)
    }
}