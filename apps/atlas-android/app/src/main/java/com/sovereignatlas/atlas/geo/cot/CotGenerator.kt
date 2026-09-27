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

    private fun escapeXml(input: String): String = buildString {
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

    fun generatePliXml(
        uid: String,
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
}
