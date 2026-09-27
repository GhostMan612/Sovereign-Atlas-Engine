// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import com.sovereignatlas.atlas.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

final class ChatXmlTest {
    private val fix = GeoPoint(44.9, -93.1, 250.0, null, null, 0L)

    @Test
    fun uidCompositionAndTypes() {
        val xml = CotGenerator.generateChatXml(
            localUid = "unit-1",
            callsign = "ALPHA",
            geoPoint = fix,
            text = "hello",
            messageId = "m-1",
        )
        assertTrue(xml.contains("uid=\"GeoChat.unit-1.All_Chat_Rooms.m-1\""))
        assertTrue(xml.contains("type=\"b-t-f\""))
        assertTrue(xml.contains("to=\"All Chat Rooms\""))
        assertTrue(xml.contains("senderCallsign=\"ALPHA\""))
        assertTrue(xml.contains(">hello</remarks>"))
    }

    @Test
    fun coordinatesAvoidScientificNotation() {
        val xml = CotGenerator.generateChatXml(
            localUid = "unit-1",
            callsign = "ALPHA",
            geoPoint = GeoPoint(0.0000001, -0.0000002, 0.0005, null, null, 0L),
            text = "ping",
            messageId = "m-2",
        )
        assertTrue(xml.contains("lat=\"0.0000001\""))
        assertTrue(xml.contains("lon=\"-0.0000002\""))
        assertTrue(xml.contains("hae=\"0.001\"") || xml.contains("hae=\"0.000\""))
        assertTrue(!xml.contains("e-") && !xml.contains("e+"))
    }

    @Test
    fun escapingAndDmTarget() {
        val xml = CotGenerator.generateChatXml(
            localUid = "unit-1",
            callsign = "A&B",
            geoPoint = fix,
            text = "a<b",
            messageId = "m-3",
            targetUid = "unit-9",
        )
        assertTrue(xml.contains("to=\"unit-9\""))
        assertTrue(xml.contains("senderCallsign=\"A&amp;B\""))
        assertTrue(xml.contains(">a&lt;b</remarks>"))
    }

    @Test
    fun missingAltitudeRendersSentinel() {
        val xml = CotGenerator.generateChatXml(
            localUid = "unit-1",
            callsign = "ALPHA",
            geoPoint = fix.copy(altitude = null),
            text = "ping",
            messageId = "m-4",
        )
        assertTrue(xml.contains("hae=\"9999999\""))
    }

    @Test
    fun nullFixThrows() {
        assertThrows(IllegalStateException::class.java) {
            CotGenerator.generateChatXml(
                localUid = "unit-1",
                callsign = "ALPHA",
                geoPoint = null,
                text = "ping",
                messageId = "m-5",
            )
        }
    }
}
