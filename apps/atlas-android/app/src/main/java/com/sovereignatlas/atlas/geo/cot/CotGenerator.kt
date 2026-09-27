// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.geo.cot

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object CotGenerator {
    private val isoFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    internal fun escapeXml(input: String): String = buildString {
        for (char in input) {
            when {
                char.code < 0x20 && char !in "\t\n\r" -> append("?")
                char == '&' -> append("&amp;")
                char == '<' -> append("&lt;")
                char == '>' -> append("&gt;")
                char == '"' -> append("&quot;")
                char == '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    fun generatePliXml(        uid: String,
        callsign: String,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        accuracyMeters: Double,
        teamColor: String = "Cyan",
        teamRole: String = "Team Member",
        observedAt: Instant = Instant.now(),
    ): String {
        val timeStr = isoFormatter.format(observedAt)
        val staleStr = isoFormatter.format(observedAt.plusSeconds(15 * 60))

        val safeUid = escapeXml(uid)
        val safeCallsign = escapeXml(callsign)
        val safeColor = escapeXml(teamColor)
        val safeRole = escapeXml(teamRole)

        return buildString {
            append("<event version=\"2.0\" uid=\"$safeUid\" type=\"a-f-G-U-C\" time=\"$timeStr\" start=\"$timeStr\" stale=\"$staleStr\" how=\"m-g\">")
            append("<point lat=\"$latitude\" lon=\"$longitude\" hae=\"$altitude\" ce=\"$accuracyMeters\" le=\"9999999.0\"/>")
            append("<detail>")
            append("<contact callsign=\"$safeCallsign\"/>")
            append("<__group name=\"$safeColor\" role=\"$safeRole\"/>")
            append("</detail>")
            append("</event>")
        }
    }

    fun generateChatXml(
        localUid: String,
        callsign: String,
        geoPoint: com.sovereignatlas.atlas.geo.GeoPoint?,
        text: String,
        messageId: String,
        chatroom: String = "All Chat Rooms",
        targetUid: String? = null,
    ): String {
        if (geoPoint == null) {
            throw IllegalStateException("No GPS fix - cannot send message")
        }

        val now = System.currentTimeMillis()
        val timeStr = formatIso8601(now)
        val staleStr = formatIso8601(now + 30 * 60 * 1000L)

        val chatroomToken = chatroom.replace(" ", "_")
        val uid = "GeoChat.$localUid.$chatroomToken.$messageId"

        val latStr = String.format(java.util.Locale.US, "%.7f", geoPoint.latitude)
        val lonStr = String.format(java.util.Locale.US, "%.7f", geoPoint.longitude)
        val haeStr = geoPoint.altitude?.let {
            String.format(java.util.Locale.US, "%.3f", it)
        } ?: "9999999"

        val remarksTo = targetUid ?: chatroom

        val safeText = escapeXml(text)
        val safeCallsign = escapeXml(callsign)
        val safeChatroom = escapeXml(chatroom)

        return buildString {
            append("<event version=\"2.0\" uid=\"").append(uid)
                .append("\" type=\"b-t-f\" time=\"").append(timeStr)
            append("\" start=\"").append(timeStr)
                .append("\" stale=\"").append(staleStr)
                .append("\" how=\"h-g-i-g-o\">")
            append("<point lat=\"").append(latStr)
                .append("\" lon=\"").append(lonStr)
                .append("\" hae=\"").append(haeStr)
                .append("\" ce=\"9999999\" le=\"9999999\"/>")
            append("<detail>")
            append("<__chat parent=\"RootContactGroup\" groupOwner=\"false\" messageId=\"")
                .append(messageId)
                .append("\" chatroom=\"").append(safeChatroom)
                .append("\" id=\"").append(safeChatroom)
                .append("\" senderCallsign=\"").append(safeCallsign).append("\">")
            append("<chatgrp uid0=\"").append(localUid)
                .append("\" uid1=\"").append(safeChatroom)
                .append("\" id=\"").append(safeChatroom).append("\"/>")
            append("</__chat>")
            append("<link uid=\"").append(localUid)
                .append("\" type=\"a-f-G-U-C\" relation=\"p-p\"/>")
            append("<remarks source=\"BAO.F.ATAK.").append(localUid)
                .append("\" to=\"").append(remarksTo)
                .append("\" time=\"").append(timeStr).append("\">")
            append(safeText).append("</remarks>")
            append("</detail>")
            append("</event>")
        }
    }

    private fun formatIso8601(epochMillis: Long): String =
        isoFormatter.format(java.time.Instant.ofEpochMilli(epochMillis))
}
