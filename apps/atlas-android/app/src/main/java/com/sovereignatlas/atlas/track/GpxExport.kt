// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.track

import com.sovereignatlas.atlas.db.Track
import com.sovereignatlas.atlas.db.Waypoint
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun escapeXml(raw: String): String {
    val out = StringBuilder(raw.length)
    for (char in raw) {
        when (char) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&apos;")
            else -> out.append(char)
        }
    }
    return out.toString()
}

fun formatGpxTime(atMs: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(atMs))
}

fun waypointsToGpx(records: List<Waypoint>): String {
    val out = StringBuilder()
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    out.append("<gpx version=\"1.1\" creator=\"SovereignAtlas\">\n")
    for (record in records) {
        out.append(
            "  <wpt lat=\"${record.latitude}\" lon=\"${record.longitude}\">\n",
        )
        out.append("    <name>${escapeXml(record.name.ifEmpty { record.id })}</name>\n")
        record.notes?.takeIf { it.isNotEmpty() }?.let { note ->
            out.append("    <desc>${escapeXml(note)}</desc>\n")
        }
        out.append("    <time>${formatGpxTime(record.timestamp)}</time>\n")
        out.append("  </wpt>\n")
    }
    out.append("</gpx>\n")
    return out.toString()
}

fun exportAllGpx(waypoints: List<Waypoint>, tracks: List<Track>): String {
    val out = StringBuilder()
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    out.append("<gpx version=\"1.1\" creator=\"SovereignAtlas\">\n")
    for (record in waypoints) {
        out.append(
            "  <wpt lat=\"${record.latitude}\" lon=\"${record.longitude}\">\n",
        )
        out.append("    <name>${escapeXml(record.name.ifEmpty { record.id })}</name>\n")
        record.notes?.takeIf { it.isNotEmpty() }?.let { note ->
            out.append("    <desc>${escapeXml(note)}</desc>\n")
        }
        out.append("    <time>${formatGpxTime(record.timestamp)}</time>\n")
        out.append("  </wpt>\n")
    }
    for (record in tracks) {
        out.append("  <trk>\n")
        out.append("    <name>${escapeXml(record.name.ifEmpty { record.id })}</name>\n")
        out.append("    <trkseg>\n")
        val points = parseTrackGeometry(record.geometry) ?: emptyList()
        for (point in points) {
            out.append(
                "      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n",
            )
            out.append("        <time>${formatGpxTime(record.timestamp)}</time>\n")
            out.append("      </trkpt>\n")
        }
        out.append("    </trkseg>\n")
        out.append("  </trk>\n")
    }
    out.append("</gpx>\n")
    return out.toString()
}

fun trackToGpx(record: Track): String {
    val out = StringBuilder()
    out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    out.append("<gpx version=\"1.1\" creator=\"SovereignAtlas\">\n")
    out.append("  <trk>\n")
    out.append("    <name>${escapeXml(record.name.ifEmpty { record.id })}</name>\n")
    out.append("    <trkseg>\n")
    val points = parseTrackGeometry(record.geometry) ?: emptyList()
    for (point in points) {
        out.append(
            "      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n",
        )
        out.append("        <time>${formatGpxTime(record.timestamp)}</time>\n")
        out.append("      </trkpt>\n")
    }
    out.append("    </trkseg>\n")
    out.append("  </trk>\n")
    out.append("</gpx>\n")
    return out.toString()
}
