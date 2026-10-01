// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The seam that keeps the `AtlasMap` rendering path working while the C2 layer
 * moves to the wider [CotEvent].
 *
 * These assertions exist because the conversion is the one place where a
 * producer-declared fact could quietly turn into a local guess.
 */
final class CotEventMapperTest {

    // 2026-10-01T00:00:00Z, used only as an opaque observation instant.
    private val observed = 1_790_812_800_000L

    private fun event(
        stale: String = "2026-10-01T00:05:00Z",
        callsign: String? = "GHOST-1",
        uid: String = "test-uid",
    ) = CotEvent(
        version = "2.0",
        uid = uid,
        type = "a-f-G-U-C",
        how = "m-g",
        time = "2026-10-01T00:00:00Z",
        start = "2026-10-01T00:00:00Z",
        stale = stale,
        lat = 34.1,
        lon = -118.2,
        hae = 10.0,
        ce = 2.0,
        le = 2.0,
        callsign = callsign,
    )

    @Test
    fun carriesTheGeometricAndIdentityFields() {
        val pli = event().toCotPli(observed)

        assertEquals("test-uid", pli.uid)
        assertEquals("a-f-G-U-C", pli.type)
        assertEquals("GHOST-1", pli.callsign)
        assertEquals(34.1, pli.latitude, 1e-9)
        assertEquals(-118.2, pli.longitude, 1e-9)
        assertEquals(10.0, pli.altitude!!, 1e-9)
    }

    @Test
    fun convertsProducerStaleIntoAbsoluteExpiry() {
        val pli = event().toCotPli(observed)

        assertEquals(Iso8601.parseToEpochMillis("2026-10-01T00:05:00Z"), pli.expiresAtMillis)
        assertNotNull(pli.expiresAtMillis)
    }

    @Test
    fun anUnreadableStaleYieldsNullExpirySoTheTtlTakesOver() {
        // Null, not a fallback timestamp. Fabricating an expiry from an unreadable
        // stale would be inventing a fact (RULES 2.3).
        val pli = event(stale = "IP").toCotPli(observed)

        assertNull(pli.expiresAtMillis)
    }

    @Test
    fun nullIslandSurvivesTheMapping() {
        val pli = event().copy(lat = 0.0, lon = 0.0).toCotPli(observed)

        assertEquals(0.0, pli.latitude, 1e-9)
        assertEquals(0.0, pli.longitude, 1e-9)
    }

    @Test
    fun anAbsentCallsignFallsBackToTheUidSuffix() {
        // Matches what AtakPayloadParser already does, so a marker with no contact
        // block renders with the same label it does today rather than blanking.
        val pli = event(callsign = null, uid = "node-9f3c").toCotPli(observed)

        assertEquals("9f3c", pli.callsign)
    }

    @Test
    fun observationInstantIsTheCallersNotTheEvents() {
        val pli = event().toCotPli(observed)

        assertEquals(observed, pli.timestamp)
    }

    @Test
    fun toCotMarkerCarriesIdentityGeometryAndProducerStale() {
        val marker = event().toCotMarker(observed)

        assertEquals("test-uid", marker.uid)
        assertEquals("a-f-G-U-C", marker.type)
        assertEquals("GHOST-1", marker.callsign)
        assertEquals(34.1, marker.latitude, 1e-9)
        assertEquals(-118.2, marker.longitude, 1e-9)
        assertEquals(10.0, marker.altitude!!, 1e-9)
        assertEquals(observed, marker.timestampMillis)
        assertEquals(Iso8601.parseToEpochMillis("2026-10-01T00:05:00Z"), marker.expiresAtMillis)
    }

    @Test
    fun toCotMarkerFallsBackToTheUidSuffixWhenCallsignIsAbsent() {
        val marker = event(callsign = null, uid = "node-9f3c").toCotMarker(observed)

        assertEquals("9f3c", marker.callsign)
    }

    @Test
    fun toCotMarkerLeavesExpiryNullForAnUnreadableStale() {
        assertNull(event(stale = "IP").toCotMarker(observed).expiresAtMillis)
    }
}