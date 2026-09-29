// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import atakmap.commoncommo.v1.Contact
import atakmap.commoncommo.v1.CotEvent
import atakmap.commoncommo.v1.Detail
import atakmap.commoncommo.v1.Group
import atakmap.commoncommo.v1.TakMessage

object CotProtobufGenerator {

    private val isoFormatter = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(java.time.ZoneOffset.UTC)

    private fun formatIso8601(epochMillis: Long): String =
        isoFormatter.format(java.time.Instant.ofEpochMilli(epochMillis))

    fun generatePliProto(
        localUid: String,
        callsign: String,
        teamColor: String,
        geoPoint: com.sovereignatlas.atlas.geo.GeoPoint,
        ceFallback: Double?,
    ): ByteArray {
        val now = System.currentTimeMillis()
        val event = CotEvent(
            type = "a-f-G-U-C",
            uid = localUid,
            sendTime = now,
            startTime = now,
            staleTime = now + 15 * 60 * 1000L,
            how = "h-g-i-g-o",
            lat = geoPoint.latitude,
            lon = geoPoint.longitude,
            hae = geoPoint.altitude ?: 9999999.0,
            ce = ceFallback ?: 9999999.0,
            le = 9999999.0,
            detail = Detail(
                contact = Contact(callsign = callsign),
                group = Group(name = teamColor, role = "Team Member"),
            ),
        )
        val msg = TakMessage(cotEvent = event)
        return byteArrayOf(0xBF.toByte(), 0x01.toByte(), 0xBF.toByte()) + TakMessage.ADAPTER.encode(msg)
    }

    fun buildMarkerUid(localUid: String): String =
        "${localUid.replace(" ", "_")}-marker-${java.util.UUID.randomUUID()}"

    fun generateMarkerProto(
        localUid: String,
        type: String,
        callsign: String,
        geoPoint: com.sovereignatlas.atlas.geo.GeoPoint,
        markerUid: String = buildMarkerUid(localUid)
    ): ByteArray {
        val now = System.currentTimeMillis()
        val event = CotEvent(
            type = type,
            uid = markerUid,
            sendTime = now,
            startTime = now,
            staleTime = now + 24 * 60 * 60 * 1000L,
            how = "h-g-i-g-o",
            lat = geoPoint.latitude,
            lon = geoPoint.longitude,
            hae = geoPoint.altitude ?: 9999999.0,
            ce = 9999999.0,
            le = 9999999.0,
            detail = Detail(
                contact = Contact(callsign = callsign)
            )
        )
        val msg = TakMessage(cotEvent = event)
        return byteArrayOf(0xBF.toByte(), 0x01.toByte(), 0xBF.toByte()) + TakMessage.ADAPTER.encode(msg)
    }

    fun generateChatProto(
        localUid: String,
        callsign: String,
        geoPoint: com.sovereignatlas.atlas.geo.GeoPoint,
        text: String,
        messageId: String,
        chatroom: String = "All Chat Rooms",
        targetUid: String? = null,
    ): ByteArray {
        val now = System.currentTimeMillis()
        val chatroomToken = chatroom.replace(" ", "_")
        val uid = "GeoChat.$localUid.$chatroomToken.$messageId"

        val safeText = CotGenerator.escapeXml(text)
        val safeCallsign = CotGenerator.escapeXml(callsign)
        val safeChatroom = CotGenerator.escapeXml(chatroom)
        val remarksTo = targetUid ?: chatroom

        val timeStr = formatIso8601(now)

        val xmlDetail = buildString {
            append("<__chat parent=\"RootContactGroup\" groupOwner=\"false\" messageId=\"").append(messageId)
            append("\" chatroom=\"").append(safeChatroom).append("\" id=\"").append(safeChatroom)
            append("\" senderCallsign=\"").append(safeCallsign).append("\">")
            append("<chatgrp uid0=\"").append(localUid).append("\" uid1=\"").append(safeChatroom).append("\" id=\"").append(safeChatroom).append("\"/>")
            append("</__chat>")
            append("<link uid=\"").append(localUid).append("\" type=\"a-f-G-U-C\" relation=\"p-p\"/>")
            append("<remarks source=\"BAO.F.ATAK.").append(localUid).append("\" to=\"").append(remarksTo).append("\" time=\"").append(timeStr).append("\">")
            append(safeText).append("</remarks>")
        }

        val event = CotEvent(
            type = "b-t-f",
            uid = uid,
            sendTime = now,
            startTime = now,
            staleTime = now + 30 * 60 * 1000L,
            how = "h-g-i-g-o",
            lat = geoPoint.latitude,
            lon = geoPoint.longitude,
            hae = geoPoint.altitude ?: 9999999.0,
            ce = 9999999.0,
            le = 9999999.0,
            detail = Detail(
                xmlDetail = xmlDetail,
            ),
        )
        val msg = TakMessage(cotEvent = event)
        return byteArrayOf(0xBF.toByte(), 0x01.toByte(), 0xBF.toByte()) + TakMessage.ADAPTER.encode(msg)
    }
}
