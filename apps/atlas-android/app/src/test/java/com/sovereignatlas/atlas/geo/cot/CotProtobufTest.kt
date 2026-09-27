// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import atakmap.commoncommo.v1.TakMessage
import com.sovereignatlas.atlas.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun stripHeader(payload: ByteArray): ByteArray {
    assertEquals(0xBF.toByte(), payload[0])
    assertEquals(0x01.toByte(), payload[1])
    assertEquals(0xBF.toByte(), payload[2])
    return payload.copyOfRange(3, payload.size)
}

private fun fix(altitude: Double? = 250.0): GeoPoint {
    return GeoPoint(44.9, -93.1, altitude, null, null, 0L)
}

final class CotProtobufTest {
    @Test
    fun pliEnvelopeRoundTrips() {
        val payload = CotProtobufGenerator.generatePliProto(
            localUid = "unit-1",
            callsign = "ALPHA",
            geoPoint = fix(),
            ceFallback = 5.0,
        )
        val event = TakMessage.ADAPTER.decode(stripHeader(payload)).cotEvent!!
        assertEquals("a-f-G-U-C", event.type)
        assertEquals("unit-1", event.uid)
        assertEquals(44.9, event.lat, 0.0)
        assertEquals(-93.1, event.lon, 0.0)
        assertEquals(250.0, event.hae, 0.0)
        assertEquals(5.0, event.ce, 0.0)
        assertEquals("ALPHA", event.detail?.contact?.callsign)
        assertEquals("Cyan", event.detail?.group?.name)
    }

    @Test
    fun chatEnvelopeRoundTrips() {
        val payload = CotProtobufGenerator.generateChatProto(
            localUid = "unit-1",
            callsign = "A&B",
            geoPoint = fix(),
            text = "a<b",
            messageId = "m-1",
        )
        val event = TakMessage.ADAPTER.decode(stripHeader(payload)).cotEvent!!
        assertEquals("b-t-f", event.type)
        assertEquals("GeoChat.unit-1.All_Chat_Rooms.m-1", event.uid)
        val detail = event.detail?.xmlDetail ?: ""
        assertTrue(detail.contains("senderCallsign=\"A&amp;B\""))
        assertTrue(detail.contains(">a&lt;b</remarks>"))
        assertTrue(detail.contains("to=\"All Chat Rooms\""))
    }

    @Test
    fun garbageProtoDecodesNull() {
        val decoded = runCatching {
            TakMessage.ADAPTER.decode(byteArrayOf(9, 9, 9, 9, 9))
        }.getOrNull()
        assertNull(decoded?.cotEvent)
    }
}
